package com.rendyhd.vicu.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One synced setting (docs/cross-app-semantics-v1.md, section 11). [value] is any JSON; null means
 * "the default", so a reset reaches every device. The revision is the custom lists' one.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncedPrefEntry(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val value: JsonElement? = null,
    val revision: CustomListRevision,
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

/** The preferences carrier's document: one entry per setting a user changed. */
@Serializable
data class SyncedPrefsDocument(
    val version: Int = 1,
    val entries: Map<String, @Serializable(with = SyncedPrefEntrySerializer::class) SyncedPrefEntry> = emptyMap(),
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

internal object SyncedPrefEntrySerializer : UnknownFieldsSerializer<SyncedPrefEntry>(SyncedPrefEntry.serializer())
internal object SyncedPrefsDocumentSerializer : UnknownFieldsSerializer<SyncedPrefsDocument>(SyncedPrefsDocument.serializer())

/** The device's copy of the preferences document, like the custom lists' sync state. */
@Serializable
data class SyncedPrefsLocalState(
    @SerialName("device_id") val deviceId: String,
    val document: SyncedPrefsDocument = SyncedPrefsDocument(),
    val dirty: Boolean = false,
    @SerialName("carrier_task_id") val carrierTaskId: Long? = null,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
)

/** The keys of the registry (section 11.3). A key is added here in the release that writes it. */
object SyncedPrefKeys {
    const val INBOX_PROJECT_ID = "inbox_project_id"
    const val TASK_ORDER = "task_order"
    private const val TASK_ORDER_PROJECT_PREFIX = "task_order.project."

    fun taskOrderOf(projectId: Long): String = "$TASK_ORDER_PROJECT_PREFIX$projectId"

    /** The project of a `task_order.project.<id>` key, or null for any other key. */
    fun taskOrderProject(key: String): Long? =
        if (key.startsWith(TASK_ORDER_PROJECT_PREFIX)) key.removePrefix(TASK_ORDER_PROJECT_PREFIX).toLongOrNull() else null
}
