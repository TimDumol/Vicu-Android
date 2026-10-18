package com.rendyhd.vicu.auth

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The tokens live in the Keychain, readable after the first unlock so background refresh can use
 * them, and never migrated to another device. The rest of the session (server URL, auth method,
 * Inbox id, expiries) is in this app's own user defaults. Keychain items outlive an uninstall
 * while user defaults do not, so the first start after an install deletes any tokens left behind.
 */
class IosKeychainTokenStorage : TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    init {
        if (!defaults.boolForKey(KEY_INSTALLED)) {
            ACCOUNTS.forEach(::deleteKeychainItem)
            defaults.setBool(true, KEY_INSTALLED)
        }
    }

    private val _authMethodFlow = MutableStateFlow<String?>(defaults.stringForKey(KEY_AUTH_METHOD))
    override val authMethodFlow: Flow<String?> = _authMethodFlow.asStateFlow()

    private val _vikunjaUrlFlow = MutableStateFlow<String?>(defaults.stringForKey(KEY_VIKUNJA_URL))
    private val _inboxProjectIdFlow = MutableStateFlow<Long?>(readInboxProjectId())
    override val inboxProjectIdFlow: Flow<Long?> = _inboxProjectIdFlow.asStateFlow()
    override val vikunjaUrlFlow: Flow<String?> = _vikunjaUrlFlow.asStateFlow()

    companion object {
        private const val KEY_AUTH_METHOD = "auth_method"
        private const val KEY_PROVIDER_KEY = "provider_key"
        private const val KEY_VIKUNJA_URL = "vikunja_url"
        private const val KEY_INBOX_PROJECT_ID = "inbox_project_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SERVER_IS_V2 = "server_is_v2"
        private const val KEY_JWT_EXPIRY = "jwt_expiry"
        private const val KEY_API_TOKEN_EXPIRY = "api_token_expiry"
        private const val KEY_BACKUP_TOKEN_ID = "backup_api_token_id"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_INSTALLED = "keychain_owned_by_install"

        private const val SERVICE_NAME = "com.rendyhd.vicu"
        private const val ACCOUNT_JWT = "jwt"
        private const val ACCOUNT_REFRESH_TOKEN = "refresh_token"
        private const val ACCOUNT_API_TOKEN = "api_token"
        private val ACCOUNTS = listOf(ACCOUNT_JWT, ACCOUNT_REFRESH_TOKEN, ACCOUNT_API_TOKEN)
    }

    /**
     * Runs [block] with a Keychain query for [account] plus [extra]. Values are CF objects
     * (constants such as kSecClassGenericPassword) or Objective-C objects (a String, NSData),
     * which are bridged for the call and released after it.
     */
    private inline fun <T> withQuery(
        account: String,
        vararg extra: Pair<CFStringRef?, Any?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val pairs = listOf<Pair<CFStringRef?, Any?>>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to account,
        ) + extra
        val dict = CFDictionaryCreateMutable(
            null, pairs.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        )
        val bridged = mutableListOf<CFTypeRef?>()
        for ((key, value) in pairs) {
            val cfValue: CFTypeRef? = when (value) {
                null -> null
                is CPointer<*> -> value.reinterpret()
                else -> CFBridgingRetain(value).also { bridged += it }
            }
            CFDictionaryAddValue(dict, key, cfValue)
        }
        try {
            return block(dict)
        } finally {
            bridged.forEach { CFRelease(it) }
            CFRelease(dict)
        }
    }

    private fun saveKeychainString(account: String, value: String): Boolean {
        deleteKeychainItem(account)
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        return withQuery(
            account,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ) { query -> SecItemAdd(query, null) == errSecSuccess }
    }

    private fun getKeychainString(account: String): String? =
        withQuery(account, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@memScoped null
                val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            }
        }

    private fun deleteKeychainItem(account: String) {
        withQuery(account) { query -> SecItemDelete(query) }
    }

    override suspend fun storeJwt(jwt: String, expiry: Long) {
        saveKeychainString(ACCOUNT_JWT, jwt)
        defaults.setInteger(expiry, KEY_JWT_EXPIRY)
    }

    override suspend fun getJwt(): String? = getKeychainString(ACCOUNT_JWT)

    override suspend fun getJwtExpiry(): Long = defaults.integerForKey(KEY_JWT_EXPIRY)

    override suspend fun storeApiToken(token: String, expiry: Long) {
        saveKeychainString(ACCOUNT_API_TOKEN, token)
        defaults.setInteger(expiry, KEY_API_TOKEN_EXPIRY)
        // A user-supplied token is not ours to revoke.
        defaults.removeObjectForKey(KEY_BACKUP_TOKEN_ID)
    }

    override suspend fun storeBackupApiToken(token: String, expiry: Long, tokenId: Long) {
        saveKeychainString(ACCOUNT_API_TOKEN, token)
        defaults.setInteger(expiry, KEY_API_TOKEN_EXPIRY)
        defaults.setInteger(tokenId, KEY_BACKUP_TOKEN_ID)
    }

    override suspend fun getBackupApiTokenId(): Long? {
        val id = defaults.integerForKey(KEY_BACKUP_TOKEN_ID)
        return if (id == 0L) null else id
    }

    override suspend fun getApiToken(): String? = getKeychainString(ACCOUNT_API_TOKEN)

    override suspend fun getApiTokenExpiry(): Long = defaults.integerForKey(KEY_API_TOKEN_EXPIRY)

    override suspend fun hasApiToken(): Boolean = getApiToken() != null

    override suspend fun getInstallId(): String {
        val existing = defaults.stringForKey(KEY_INSTALL_ID)
        if (existing != null && ApiTokenTitle.isValidInstallId(existing)) return existing
        val created = ApiTokenTitle.newInstallId()
        defaults.setObject(created, KEY_INSTALL_ID)
        return created
    }

    override suspend fun storeRefreshToken(token: String) {
        saveKeychainString(ACCOUNT_REFRESH_TOKEN, token)
    }

    override suspend fun getRefreshToken(): String? = getKeychainString(ACCOUNT_REFRESH_TOKEN)

    override suspend fun storeServerIsV2(isV2: Boolean) {
        defaults.setBool(isV2, KEY_SERVER_IS_V2)
    }

    override suspend fun getServerIsV2(): Boolean = defaults.boolForKey(KEY_SERVER_IS_V2)

    override suspend fun storeAuthMethod(method: String) {
        defaults.setObject(method, KEY_AUTH_METHOD)
        _authMethodFlow.value = method
    }

    override suspend fun getAuthMethod(): String? = defaults.stringForKey(KEY_AUTH_METHOD)

    override suspend fun storeProviderKey(key: String) {
        defaults.setObject(key, KEY_PROVIDER_KEY)
    }

    override suspend fun getProviderKey(): String? = defaults.stringForKey(KEY_PROVIDER_KEY)

    override suspend fun storeVikunjaUrl(url: String) {
        defaults.setObject(url, KEY_VIKUNJA_URL)
        _vikunjaUrlFlow.value = url
    }

    override suspend fun getVikunjaUrl(): String? = defaults.stringForKey(KEY_VIKUNJA_URL)

    override suspend fun storeInboxProjectId(id: Long) {
        defaults.setInteger(id, KEY_INBOX_PROJECT_ID)
        _inboxProjectIdFlow.value = id
    }

    override suspend fun getInboxProjectId(): Long? = readInboxProjectId()

    private fun readInboxProjectId(): Long? {
        val id = defaults.integerForKey(KEY_INBOX_PROJECT_ID)
        return if (id == 0L) null else id
    }

    override suspend fun clearInboxProjectId() {
        defaults.removeObjectForKey(KEY_INBOX_PROJECT_ID)
        _inboxProjectIdFlow.value = null
    }

    override suspend fun storeUserId(id: Long) {
        defaults.setInteger(id, KEY_USER_ID)
    }

    override suspend fun getUserId(): Long? {
        val id = defaults.integerForKey(KEY_USER_ID)
        return if (id == 0L) null else id
    }

    override suspend fun clear() {
        deleteKeychainItem(ACCOUNT_JWT)
        deleteKeychainItem(ACCOUNT_API_TOKEN)
        deleteKeychainItem(ACCOUNT_REFRESH_TOKEN)
        defaults.removeObjectForKey(KEY_JWT_EXPIRY)
        defaults.removeObjectForKey(KEY_API_TOKEN_EXPIRY)
        defaults.removeObjectForKey(KEY_BACKUP_TOKEN_ID)
        defaults.removeObjectForKey(KEY_AUTH_METHOD)
        defaults.removeObjectForKey(KEY_PROVIDER_KEY)
        defaults.removeObjectForKey(KEY_VIKUNJA_URL)
        defaults.removeObjectForKey(KEY_INBOX_PROJECT_ID)
        defaults.removeObjectForKey(KEY_USER_ID)
        defaults.removeObjectForKey(KEY_SERVER_IS_V2)
        _authMethodFlow.value = null
        _vikunjaUrlFlow.value = null
        _inboxProjectIdFlow.value = null
    }
}
