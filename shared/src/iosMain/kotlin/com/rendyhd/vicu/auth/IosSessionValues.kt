package com.rendyhd.vicu.auth

import kotlinx.serialization.Serializable
import platform.Foundation.NSUserDefaults

/** The session values other than the tokens and the install id, as `session.json` holds them. */
@Serializable
internal data class SessionValues(
    val vikunjaUrl: String? = null,
    val authMethod: String? = null,
    val providerKey: String? = null,
    val serverIsV2: Boolean = false,
    val userId: Long? = null,
    val inboxProjectId: Long? = null,
    val jwtExpiry: Long = 0,
    val apiTokenExpiry: Long = 0,
    val backupTokenId: Long? = null,
)

/**
 * What the first start of a version that keeps the session in `session.json` does with the
 * session older versions kept in the user defaults. iOS backs the user defaults up, so they can
 * come from another device; the Keychain items never do.
 */
internal enum class SessionFirstStart {
    /** No earlier install: Keychain items an uninstalled copy left behind are deleted. */
    FRESH_INSTALL,

    /** An older version ran on this device and its tokens are here: its values move over. */
    MOVE,

    /**
     * The user defaults say Vicu was installed but the Keychain holds none of its tokens: they
     * were restored from a backup of another device (or this install was signed out). Their
     * install id and token id belong to that other device, so this one starts with no session
     * and makes a new install id; reusing the old one would let this device delete the other's
     * backup API token as a leftover of its own.
     */
    RESTORED_COPY,

    /** The Keychain did not answer (locked since the restart): decided on a later start. */
    UNDECIDED,
    ;

    companion object {
        /**
         * [installedBefore]: the user defaults hold the mark older versions set on their first
         * start. [tokens]: what the Keychain answered for each token.
         */
        fun decide(installedBefore: Boolean, tokens: List<KeychainValue>): SessionFirstStart = when {
            tokens.any { it == KeychainValue.Unavailable } -> UNDECIDED
            !installedBefore -> FRESH_INSTALL
            tokens.any { it is KeychainValue.Found } -> MOVE
            else -> RESTORED_COPY
        }
    }
}

/** The keys older versions kept the session under in `NSUserDefaults.standardUserDefaults`. */
internal object LegacySessionDefaults {
    /** Set on the first start of an install, to tell a new install from Keychain items an uninstalled one left. */
    const val INSTALLED = "keychain_owned_by_install"
    const val INSTALL_ID = "install_id"
    const val AUTH_METHOD = "auth_method"
    const val PROVIDER_KEY = "provider_key"
    const val VIKUNJA_URL = "vikunja_url"
    const val INBOX_PROJECT_ID = "inbox_project_id"
    const val USER_ID = "user_id"
    const val SERVER_IS_V2 = "server_is_v2"
    const val JWT_EXPIRY = "jwt_expiry"
    const val API_TOKEN_EXPIRY = "api_token_expiry"
    const val BACKUP_TOKEN_ID = "backup_api_token_id"

    private val KEYS = listOf(
        INSTALLED, INSTALL_ID, AUTH_METHOD, PROVIDER_KEY, VIKUNJA_URL, INBOX_PROJECT_ID, USER_ID,
        SERVER_IS_V2, JWT_EXPIRY, API_TOKEN_EXPIRY, BACKUP_TOKEN_ID,
    )

    fun installedBefore(defaults: NSUserDefaults): Boolean = defaults.boolForKey(INSTALLED)

    fun installId(defaults: NSUserDefaults): String? =
        defaults.stringForKey(INSTALL_ID)?.takeIf(ApiTokenTitle::isValidInstallId)

    fun read(defaults: NSUserDefaults): SessionValues = SessionValues(
        vikunjaUrl = defaults.stringForKey(VIKUNJA_URL),
        authMethod = defaults.stringForKey(AUTH_METHOD),
        providerKey = defaults.stringForKey(PROVIDER_KEY),
        serverIsV2 = defaults.boolForKey(SERVER_IS_V2),
        userId = defaults.integerForKey(USER_ID).takeIf { it != 0L },
        inboxProjectId = defaults.integerForKey(INBOX_PROJECT_ID).takeIf { it != 0L },
        jwtExpiry = defaults.integerForKey(JWT_EXPIRY),
        apiTokenExpiry = defaults.integerForKey(API_TOKEN_EXPIRY),
        backupTokenId = defaults.integerForKey(BACKUP_TOKEN_ID).takeIf { it != 0L },
    )

    fun remove(defaults: NSUserDefaults) {
        KEYS.forEach(defaults::removeObjectForKey)
    }
}
