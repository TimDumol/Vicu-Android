package com.rendyhd.vicu.data.sync

import com.rendyhd.vicu.auth.CurrentUser
import com.rendyhd.vicu.data.remote.api.CreateTaskDto
import com.rendyhd.vicu.data.remote.api.TaskDto
import com.rendyhd.vicu.data.remote.api.VikunjaApiService
import com.rendyhd.vicu.util.DateUtils
import com.rendyhd.vicu.util.HiddenTasks
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.util.SystemTimeSource
import com.rendyhd.vicu.util.TimeSource
import com.rendyhd.vicu.util.isRetriableNetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One kind of synced document kept in a hidden carrier task: the custom lists, the preferences.
 * The document is made of records stamped with revisions, so two copies always merge
 * (docs/cross-app-semantics-v1.md, section 10).
 */
interface SyncedDocumentCodec<Doc> {
    val spec: CarrierSpec
    val carrierTitle: String
    val messages: SyncedDocumentMessages

    /** Reads a carrier description. */
    fun parse(description: String?): CarrierRead<Doc>

    /** The carrier description holding [document]. */
    fun encode(document: Doc): String

    fun normalize(document: Doc): Doc

    /** Both copies in one; [server] is the carrier's, which wins where the records cannot decide. */
    fun merge(local: Doc, server: Doc): Doc

    /** [document] without the tombstones older than 90 days at [nowMs] (section 10.6). */
    fun pruneTombstones(document: Doc, nowMs: Long): Doc

    /** True when the document holds a record: only then is a carrier worth creating. */
    fun hasContent(document: Doc): Boolean

    fun same(left: Doc, right: Doc): Boolean
}

/** What a sync says about one kind, in the words the user sees. */
data class SyncedDocumentMessages(
    val updateRequired: String,
    val malformed: String,
    val noInbox: String,
    val offline: String,
    val failed: String,
)

/** A carrier description read: the document, or why there is none. */
data class CarrierRead<out Doc>(
    val document: Doc? = null,
    val error: String? = null,
    /** Written by a newer version: never written or deleted here (section 10.7). */
    val newerVersion: Boolean = false,
)

/** The device's copy of a synced document and whether the carrier has caught up with it. */
data class SyncedDocumentState<Doc>(
    val deviceId: String,
    val document: Doc,
    /** True while the copy may hold something the carrier does not. */
    val dirty: Boolean = false,
    val carrierTaskId: Long? = null,
    val lastSyncedAt: String? = null,
)

/** Where the device keeps its copy. [load] makes one when there is none yet. */
interface SyncedDocumentLocal<Doc> {
    suspend fun load(): SyncedDocumentState<Doc>
    suspend fun save(state: SyncedDocumentState<Doc>)
}

sealed interface SyncedDocumentStatus {
    data object Idle : SyncedDocumentStatus
    data object Syncing : SyncedDocumentStatus

    /** The device holds changes the carrier does not have yet; a later sync sends them. */
    data object Pending : SyncedDocumentStatus
    data class Offline(val message: String) : SyncedDocumentStatus
    data class Error(val message: String) : SyncedDocumentStatus
    data class UpdateRequired(val message: String) : SyncedDocumentStatus
}

/**
 * Syncs one document kind with its carrier, the same way the desktop does
 * (`custom-list-service.ts`):
 *
 * 1. find the user's own carriers of the kind ([CarrierFinder]) and read them; a newer version's
 *    stops the sync with "update required", a malformed one is ignored and reported;
 * 2. merge them into the device's copy and drop old tombstones;
 * 3. write the result to the carrier with the lowest id when it differs (creating the carrier in
 *    the Inbox when there is none and the document holds something);
 * 4. read the carrier back and merge once more, so a write that raced another device ends as
 *    [SyncedDocumentStatus.Pending], not lost;
 * 5. delete every other own carrier whose content the kept one now holds (section 10.5).
 *
 * Edits to the device's copy go through [mutate], which shares a lock with the merge steps but
 * never with the network, so an offline edit never waits for a sync.
 */
class SyncedDocumentSync<Doc : Any>(
    private val codec: SyncedDocumentCodec<Doc>,
    private val local: SyncedDocumentLocal<Doc>,
    private val api: VikunjaApiService,
    private val carrierFinder: CarrierFinder,
    private val currentUser: CurrentUser,
    private val serverKey: suspend () -> String,
    private val inboxProjectId: suspend () -> Long?,
    private val time: TimeSource = SystemTimeSource,
    private val onStatus: (SyncedDocumentStatus) -> Unit = {},
    /** After a sync that may have changed the device's copy (widgets read some documents). */
    private val onChanged: suspend () -> Unit = {},
    /**
     * Runs once the carriers were read and merged, before anything is written, on the merged copy;
     * what it returns is written (the one-time move of settings kept on the device, section 11.5).
     */
    private val afterMerge: suspend (SyncedDocumentState<Doc>) -> Doc = { it.document },
) {
    private companion object {
        const val TAG = "SyncedDocumentSync"
    }

    private val mutationMutex = Mutex()
    private val syncMutex = Mutex()
    private var clearGeneration = 0L

    /** Runs a change to the device's copy, never at the same time as a sync's merge. */
    suspend fun <T> mutate(block: suspend () -> T): T = mutationMutex.withLock { block() }

    /** Forgets the device's copy ([block] clears it); a sync running meanwhile writes nothing back. */
    suspend fun clear(block: suspend () -> Unit) = mutationMutex.withLock {
        clearGeneration++
        block()
        onStatus(SyncedDocumentStatus.Idle)
    }

    suspend fun sync(): SyncedDocumentStatus = syncMutex.withLock {
        onStatus(SyncedDocumentStatus.Syncing)
        val status = try {
            syncOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "${codec.spec.kind} sync failed: ${e.message}")
            try {
                mutationMutex.withLock { local.save(local.load().copy(dirty = true)) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                // The failure above is the one worth reporting.
            }
            if (isRetriableNetworkError(e)) {
                SyncedDocumentStatus.Offline(e.message ?: codec.messages.offline)
            } else {
                SyncedDocumentStatus.Error(e.message ?: codec.messages.failed)
            }
        }
        onStatus(status)
        status
    }

    private suspend fun syncOnce(): SyncedDocumentStatus {
        val generation = clearGeneration
        // Whose carriers are whose is not known yet (no `GET user` answer): try again once it is.
        val owner = currentUser.id() ?: return SyncedDocumentStatus.Pending
        val server = serverKey()

        val carriers = mutableListOf<Pair<TaskDto, Doc>>()
        var malformed = 0
        var newer = false
        for (task in carrierFinder.find(codec.spec, server, owner)) {
            val read = codec.parse(task.description)
            when {
                read.document != null -> carriers += task to read.document
                read.newerVersion -> newer = true
                else -> malformed++
            }
        }
        if (generation != clearGeneration) return SyncedDocumentStatus.Idle
        if (newer) return SyncedDocumentStatus.UpdateRequired(codec.messages.updateRequired)

        val now = time.now().toEpochMilliseconds()
        lateinit var merged: Doc
        mutationMutex.withLock {
            val state = local.load()
            var document = codec.normalize(state.document)
            carriers.forEach { (_, remote) -> document = codec.merge(document, remote) }
            document = codec.pruneTombstones(document, now)
            merged = afterMerge(state.copy(document = document))
            local.save(state.copy(document = merged, dirty = state.dirty || !codec.same(merged, document)))
        }

        val canonical = carriers.minByOrNull { it.first.id }
        var carrierId = canonical?.first?.id
        val needsWrite = if (canonical == null) codec.hasContent(merged) else !codec.same(canonical.second, merged)
        if (needsWrite) {
            if (carrierId != null) {
                write(carrierId, merged)
            } else {
                val inbox = inboxProjectId()?.takeIf { it > 0L } ?: error(codec.messages.noInbox)
                carrierId = create(inbox, merged, server)
            }
        }

        var pending = false
        var kept: Doc? = null
        if (carrierId != null) {
            val verified = codec.parse(api.getTask(carrierId).description).document
                ?: error("Cannot read the ${codec.spec.kind} carrier back")
            mutationMutex.withLock {
                if (generation != clearGeneration) return SyncedDocumentStatus.Idle
                val latest = local.load()
                // Edits made here while the request was out stay; the server's copy wins the rest.
                val converged = codec.pruneTombstones(codec.merge(latest.document, codec.merge(merged, verified)), now)
                pending = !codec.same(converged, verified)
                local.save(
                    latest.copy(
                        document = converged,
                        dirty = pending,
                        carrierTaskId = carrierId,
                        lastSyncedAt = if (pending) latest.lastSyncedAt else DateUtils.nowIso(),
                    ),
                )
            }
            kept = verified
        } else {
            mutationMutex.withLock {
                if (generation != clearGeneration) return SyncedDocumentStatus.Idle
                val latest = local.load()
                val converged = codec.merge(latest.document, merged)
                pending = !codec.same(converged, merged)
                local.save(
                    latest.copy(
                        document = converged,
                        dirty = pending,
                        carrierTaskId = null,
                        lastSyncedAt = if (pending) latest.lastSyncedAt else DateUtils.nowIso(),
                    ),
                )
            }
        }

        if (!pending && kept != null && carrierId != null) {
            deleteCovered(carriers.map { it.first.id } - carrierId, kept, owner, server, now)
        }
        onChanged()
        return when {
            pending -> SyncedDocumentStatus.Pending
            malformed > 0 -> SyncedDocumentStatus.Error(codec.messages.malformed)
            else -> SyncedDocumentStatus.Idle
        }
    }

    /**
     * Deletes each of [extraIds] whose content [kept] already holds: read again right before, so an
     * edit another device made to it since is never lost, and only when it is still the user's own
     * and readable (section 10.5). Anything going wrong leaves it for the next sync.
     */
    private suspend fun deleteCovered(extraIds: List<Long>, kept: Doc, owner: Long, server: String, now: Long) {
        val keptPruned = codec.pruneTombstones(kept, now)
        for (id in extraIds) {
            try {
                val fresh = api.getTask(id)
                if (!HiddenTasks.isOwn(fresh, owner)) continue
                val document = codec.parse(fresh.description).document ?: continue
                if (!codec.same(codec.pruneTombstones(codec.merge(keptPruned, document), now), keptPruned)) continue
                api.deleteTask(id)
                carrierFinder.forget(codec.spec, server, id)
                Logger.i(TAG, "Deleted ${codec.spec.kind} carrier $id: the kept carrier holds all of it")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Could not remove the extra ${codec.spec.kind} carrier $id: ${e.message}")
            }
        }
    }

    private suspend fun write(id: Long, document: Doc) {
        api.updateTask(id, buildJsonObject {
            put("title", codec.carrierTitle)
            put("description", codec.encode(document))
            HiddenTasks.putForcedFields(this)
        })
    }

    private suspend fun create(projectId: Long, document: Doc, server: String): Long {
        val task = api.createTask(projectId, CreateTaskDto(
            title = codec.carrierTitle,
            description = codec.encode(document),
            done = true,
            dueDate = HiddenTasks.NULL_DATE,
            reminders = emptyList(),
        ))
        write(task.id, document)
        carrierFinder.remember(codec.spec, server, task.id, projectId)
        return task.id
    }
}
