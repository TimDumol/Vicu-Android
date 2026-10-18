package com.rendyhd.vicu.auth

import com.rendyhd.vicu.util.IosPaths
import com.rendyhd.vicu.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults

/**
 * The tokens and the install id live in the Keychain, readable after the first unlock so
 * background refresh can use them, and never moved to another device. The rest of the session
 * (server URL, auth method, Inbox id, expiries, the backup token's id) is in `session.json` in a
 * folder of Application Support that is kept out of backups. So a device restored from a backup,
 * or a second device set up from one, starts without a session and makes its own install id.
 *
 * Keychain items outlive an uninstall while the session file does not, so the first start of an
 * install deletes any Keychain items left behind. Older versions kept the session in the user
 * defaults, which iOS backs up; the first start of this version moves it ([SessionFirstStart]).
 *
 * Nothing is read until the session is first used, so Koin can build this without touching the
 * Keychain or the file system (CLAUDE.md, iOS gotcha 19).
 */
class IosKeychainTokenStorage internal constructor(
    private val keychain: KeychainItems,
    /** The session file; its folder is created and kept out of backups by the default. */
    private val sessionFile: () -> String,
    /** Where older versions kept the session. */
    private val legacyDefaults: NSUserDefaults,
) : TokenStorage {

    constructor() : this(
        keychain = SystemKeychainItems(SERVICE_NAME),
        sessionFile = { IosPaths.excludedSubdirectory(IosPaths.applicationSupport, "session") + "/session.json" },
        legacyDefaults = NSUserDefaults.standardUserDefaults,
    )

    /** The values in memory and the flows that follow them. Changed only under [lock]. */
    private class Session(
        var values: SessionValues,
        /** The file to write changes to; null when this start keeps them in memory only. */
        val file: String?,
    ) {
        var installId: String? = null
        val authMethod = MutableStateFlow(values.authMethod)
        val vikunjaUrl = MutableStateFlow(values.vikunjaUrl)
        val inboxProjectId = MutableStateFlow(values.inboxProjectId)
    }

    private val lock = NSLock()
    private val json = Json { ignoreUnknownKeys = true }
    private val session: Session by lazy { open() }
    private val authMethodState: StateFlow<String?> by lazy { session.authMethod.asStateFlow() }
    private val vikunjaUrlState: StateFlow<String?> by lazy { session.vikunjaUrl.asStateFlow() }
    private val inboxProjectIdState: StateFlow<Long?> by lazy { session.inboxProjectId.asStateFlow() }

    override val authMethodFlow: Flow<String?> get() = authMethodState
    override val vikunjaUrlFlow: Flow<String?> get() = vikunjaUrlState
    override val inboxProjectIdFlow: Flow<Long?> get() = inboxProjectIdState

    companion object {
        private const val TAG = "IosKeychainTokenStorage"
        private const val SERVICE_NAME = "com.rendyhd.vicu"
        private const val ACCOUNT_JWT = "jwt"
        private const val ACCOUNT_REFRESH_TOKEN = "refresh_token"
        private const val ACCOUNT_API_TOKEN = "api_token"
        private const val ACCOUNT_INSTALL_ID = "install_id"
        private val TOKEN_ACCOUNTS = listOf(ACCOUNT_JWT, ACCOUNT_REFRESH_TOKEN, ACCOUNT_API_TOKEN)
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    private fun open(): Session {
        val file = sessionFile()
        if (SystemFileSystem.exists(Path(file))) {
            val text = try {
                SystemFileSystem.source(Path(file)).buffered().use { it.readString() }
            } catch (e: Exception) {
                // Not readable yet (data protection before the first unlock): written over, it
                // would lose the session, so this start keeps changes in memory.
                Logger.w(TAG, "Session file not readable: ${e.message}")
                return Session(SessionValues(), file = null)
            }
            val values = try {
                json.decodeFromString(SessionValues.serializer(), text)
            } catch (e: Exception) {
                Logger.w(TAG, "Session file not understood, starting without a session: ${e.message}")
                SessionValues()
            }
            // What a move cut short before it got to them.
            LegacySessionDefaults.remove(legacyDefaults)
            return Session(values, file)
        }

        val tokens = TOKEN_ACCOUNTS.map(keychain::read)
        return when (SessionFirstStart.decide(LegacySessionDefaults.installedBefore(legacyDefaults), tokens)) {
            SessionFirstStart.FRESH_INSTALL -> {
                (TOKEN_ACCOUNTS + ACCOUNT_INSTALL_ID).forEach(keychain::delete)
                begin(file, SessionValues())
            }
            SessionFirstStart.MOVE -> {
                LegacySessionDefaults.installId(legacyDefaults)?.let { keychain.write(ACCOUNT_INSTALL_ID, it) }
                begin(file, LegacySessionDefaults.read(legacyDefaults))
            }
            SessionFirstStart.RESTORED_COPY -> {
                Logger.i(TAG, "Session restored from another device's backup: starting without it")
                begin(file, SessionValues())
            }
            SessionFirstStart.UNDECIDED -> {
                Logger.w(TAG, "Keychain not available: the session stays where it is until a later start")
                Session(LegacySessionDefaults.read(legacyDefaults), file = null)
            }
        }
    }

    /** Writes the first session file; the old values leave the user defaults once it is there. */
    private fun begin(file: String, values: SessionValues): Session {
        if (write(file, values)) LegacySessionDefaults.remove(legacyDefaults)
        return Session(values, file)
    }

    /** Written whole, then moved into place, so a start never reads half a file. */
    private fun write(file: String, values: SessionValues): Boolean = try {
        val partial = Path("$file.part")
        SystemFileSystem.sink(partial).buffered().use { it.writeString(json.encodeToString(SessionValues.serializer(), values)) }
        SystemFileSystem.atomicMove(partial, Path(file))
        true
    } catch (e: Exception) {
        Logger.w(TAG, "Session file not written: ${e.message}")
        false
    }

    private fun values(): SessionValues {
        val session = session
        return locked { session.values }
    }

    private fun update(change: (SessionValues) -> SessionValues) {
        val session = session
        locked {
            val next = change(session.values)
            if (next == session.values) return@locked
            session.values = next
            session.file?.let { write(it, next) }
            session.authMethod.value = next.authMethod
            session.vikunjaUrl.value = next.vikunjaUrl
            session.inboxProjectId.value = next.inboxProjectId
        }
    }

    // The token items are touched only once open() has decided what they are: a new install
    // deletes what an uninstalled copy left, which must not be read before, or written and then
    // deleted.
    private fun readToken(account: String): String? = session.let { (keychain.read(account) as? KeychainValue.Found)?.value }

    private fun writeToken(account: String, value: String) {
        session.let { keychain.write(account, value) }
    }

    override suspend fun storeJwt(jwt: String, expiry: Long) {
        writeToken(ACCOUNT_JWT, jwt)
        update { it.copy(jwtExpiry = expiry) }
    }

    override suspend fun getJwt(): String? = readToken(ACCOUNT_JWT)

    override suspend fun getJwtExpiry(): Long = values().jwtExpiry

    override suspend fun storeApiToken(token: String, expiry: Long) {
        writeToken(ACCOUNT_API_TOKEN, token)
        // A user-supplied token is not ours to revoke.
        update { it.copy(apiTokenExpiry = expiry, backupTokenId = null) }
    }

    override suspend fun storeBackupApiToken(token: String, expiry: Long, tokenId: Long) {
        writeToken(ACCOUNT_API_TOKEN, token)
        update { it.copy(apiTokenExpiry = expiry, backupTokenId = tokenId) }
    }

    override suspend fun getBackupApiTokenId(): Long? = values().backupTokenId?.takeIf { it != 0L }

    override suspend fun getApiToken(): String? = readToken(ACCOUNT_API_TOKEN)

    override suspend fun getApiTokenExpiry(): Long = values().apiTokenExpiry

    override suspend fun hasApiToken(): Boolean = getApiToken() != null

    override suspend fun getInstallId(): String {
        val session = session
        return locked {
            session.installId ?: run {
                when (val stored = keychain.read(ACCOUNT_INSTALL_ID)) {
                    is KeychainValue.Found -> stored.value.takeIf(ApiTokenTitle::isValidInstallId)
                    // Not written over one the Keychain could not read; asked again next time.
                    KeychainValue.Unavailable -> return@locked ApiTokenTitle.newInstallId()
                    KeychainValue.Missing -> null
                } ?: ApiTokenTitle.newInstallId().also { created ->
                    if (!keychain.write(ACCOUNT_INSTALL_ID, created)) Logger.w(TAG, "Install id not stored in the Keychain")
                }
            }.also { session.installId = it }
        }
    }

    override suspend fun storeRefreshToken(token: String) {
        writeToken(ACCOUNT_REFRESH_TOKEN, token)
    }

    override suspend fun getRefreshToken(): String? = readToken(ACCOUNT_REFRESH_TOKEN)

    override suspend fun storeServerIsV2(isV2: Boolean) = update { it.copy(serverIsV2 = isV2) }

    override suspend fun getServerIsV2(): Boolean = values().serverIsV2

    override suspend fun storeAuthMethod(method: String) = update { it.copy(authMethod = method) }

    override suspend fun getAuthMethod(): String? = values().authMethod

    override suspend fun storeProviderKey(key: String) = update { it.copy(providerKey = key) }

    override suspend fun getProviderKey(): String? = values().providerKey

    override suspend fun storeVikunjaUrl(url: String) = update { it.copy(vikunjaUrl = url) }

    override suspend fun getVikunjaUrl(): String? = values().vikunjaUrl

    override suspend fun storeInboxProjectId(id: Long) = update { it.copy(inboxProjectId = id) }

    override suspend fun getInboxProjectId(): Long? = values().inboxProjectId?.takeIf { it != 0L }

    override suspend fun clearInboxProjectId() = update { it.copy(inboxProjectId = null) }

    override suspend fun storeUserId(id: Long) = update { it.copy(userId = id) }

    override suspend fun getUserId(): Long? = values().userId?.takeIf { it != 0L }

    /** Everything but the install id. */
    override suspend fun clear() {
        session.let { TOKEN_ACCOUNTS.forEach(keychain::delete) }
        update { SessionValues() }
    }
}
