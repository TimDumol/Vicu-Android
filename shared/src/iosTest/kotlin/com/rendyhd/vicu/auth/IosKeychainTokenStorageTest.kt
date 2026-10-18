package com.rendyhd.vicu.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosKeychainTokenStorageTest {
    private val folder = NSTemporaryDirectory() + "session-test-" + NSUUID().UUIDString
    private val file = "$folder/session.json"
    private val suite = "vicu-session-test-" + NSUUID().UUIDString
    private val defaults = NSUserDefaults(suiteName = suite)
    private val keychain = FakeKeychain()

    private class FakeKeychain : KeychainItems {
        val items = mutableMapOf<String, String>()
        var available = true

        override fun read(account: String): KeychainValue = when {
            !available -> KeychainValue.Unavailable
            else -> items[account]?.let { KeychainValue.Found(it) } ?: KeychainValue.Missing
        }

        override fun write(account: String, value: String): Boolean {
            if (!available) return false
            items[account] = value
            return true
        }

        override fun delete(account: String) {
            if (available) items.remove(account)
        }
    }

    private fun storage() = IosKeychainTokenStorage(
        keychain = keychain,
        sessionFile = {
            SystemFileSystem.createDirectories(Path(folder))
            file
        },
        legacyDefaults = defaults,
    )

    /** What an older version left in the user defaults after signing in with a password. */
    private fun olderVersionSignedIn() {
        defaults.setBool(true, LegacySessionDefaults.INSTALLED)
        defaults.setObject("a1b2c3", LegacySessionDefaults.INSTALL_ID)
        defaults.setObject("https://vikunja.test", LegacySessionDefaults.VIKUNJA_URL)
        defaults.setObject("password", LegacySessionDefaults.AUTH_METHOD)
        defaults.setBool(true, LegacySessionDefaults.SERVER_IS_V2)
        defaults.setInteger(5, LegacySessionDefaults.USER_ID)
        defaults.setInteger(12, LegacySessionDefaults.INBOX_PROJECT_ID)
        defaults.setInteger(1_900_000_000, LegacySessionDefaults.JWT_EXPIRY)
        defaults.setInteger(1_950_000_000, LegacySessionDefaults.API_TOKEN_EXPIRY)
        defaults.setInteger(77, LegacySessionDefaults.BACKUP_TOKEN_ID)
    }

    private fun exists(path: String) = SystemFileSystem.exists(Path(path))

    @AfterTest
    fun cleanUp() {
        defaults.removePersistentDomainForName(suite)
        SystemFileSystem.delete(Path(file), mustExist = false)
        SystemFileSystem.delete(Path(folder), mustExist = false)
    }

    @Test
    fun theFirstStartTellsAnUpdateFromARestoredCopy() {
        val found = KeychainValue.Found("token")
        val missing = KeychainValue.Missing
        val unavailable = KeychainValue.Unavailable

        assertEquals(SessionFirstStart.FRESH_INSTALL, SessionFirstStart.decide(installedBefore = false, listOf(missing, missing, missing)))
        // Left behind by an uninstalled copy: the Keychain outlives an uninstall.
        assertEquals(SessionFirstStart.FRESH_INSTALL, SessionFirstStart.decide(installedBefore = false, listOf(found, found, missing)))
        // An API-token sign-in has only the API token.
        assertEquals(SessionFirstStart.MOVE, SessionFirstStart.decide(installedBefore = true, listOf(missing, missing, found)))
        assertEquals(SessionFirstStart.MOVE, SessionFirstStart.decide(installedBefore = true, listOf(found, found, found)))
        // The user defaults came from a backup; this-device-only Keychain items never do.
        assertEquals(SessionFirstStart.RESTORED_COPY, SessionFirstStart.decide(installedBefore = true, listOf(missing, missing, missing)))
        // Locked since the restart: no conclusion, whatever else is known.
        assertEquals(SessionFirstStart.UNDECIDED, SessionFirstStart.decide(installedBefore = true, listOf(unavailable, unavailable, unavailable)))
        assertEquals(SessionFirstStart.UNDECIDED, SessionFirstStart.decide(installedBefore = false, listOf(unavailable, unavailable, unavailable)))
        assertEquals(SessionFirstStart.UNDECIDED, SessionFirstStart.decide(installedBefore = true, listOf(found, unavailable, missing)))
    }

    @Test
    fun buildingItTouchesNothing() {
        olderVersionSignedIn()
        keychain.items["jwt"] = "jwt-1"

        storage()

        assertFalse(exists(file))
        assertTrue(LegacySessionDefaults.installedBefore(defaults))
    }

    @Test
    fun anUpdatedDeviceMovesItsSessionOutOfTheUserDefaults() = runTest {
        olderVersionSignedIn()
        keychain.items["jwt"] = "jwt-1"
        keychain.items["refresh_token"] = "refresh-1"
        keychain.items["api_token"] = "tk_backup"

        val storage = storage()

        assertEquals("https://vikunja.test", storage.getVikunjaUrl())
        assertEquals("password", storage.getAuthMethod())
        assertTrue(storage.getServerIsV2())
        assertEquals(5L, storage.getUserId())
        assertEquals(12L, storage.getInboxProjectId())
        assertEquals(1_900_000_000L, storage.getJwtExpiry())
        assertEquals(1_950_000_000L, storage.getApiTokenExpiry())
        assertEquals(77L, storage.getBackupApiTokenId())
        assertEquals("jwt-1", storage.getJwt())
        assertEquals("a1b2c3", storage.getInstallId())
        assertEquals("a1b2c3", keychain.items["install_id"])
        assertEquals("https://vikunja.test", storage.vikunjaUrlFlow.first())
        assertEquals(12L, storage.inboxProjectIdFlow.first())

        // Nothing of the session is left where iOS backs it up.
        assertTrue(exists(file))
        assertFalse(LegacySessionDefaults.installedBefore(defaults))
        assertNull(defaults.objectForKey(LegacySessionDefaults.INSTALL_ID))
        assertNull(defaults.objectForKey(LegacySessionDefaults.VIKUNJA_URL))
        assertNull(defaults.objectForKey(LegacySessionDefaults.BACKUP_TOKEN_ID))

        // The next start reads the file.
        val again = storage()
        assertEquals("https://vikunja.test", again.getVikunjaUrl())
        assertEquals(77L, again.getBackupApiTokenId())
        assertEquals("a1b2c3", again.getInstallId())
    }

    @Test
    fun aRestoredCopyStartsWithoutTheSessionAndWithItsOwnInstallId() = runTest {
        olderVersionSignedIn()

        val storage = storage()

        val installId = storage.getInstallId()
        assertNotEquals("a1b2c3", installId)
        assertTrue(ApiTokenTitle.isValidInstallId(installId))
        assertEquals(installId, keychain.items["install_id"])
        // The other device's token id must never be revoked from here.
        assertNull(storage.getBackupApiTokenId())
        assertNull(storage.getVikunjaUrl())
        assertNull(storage.getInboxProjectId())
        assertNull(storage.getUserId())
        assertNull(defaults.objectForKey(LegacySessionDefaults.INSTALL_ID))
        assertFalse(LegacySessionDefaults.installedBefore(defaults))

        // Decided once: signing in later does not make it an update.
        keychain.items["api_token"] = "tk_new"
        assertEquals(installId, storage().getInstallId())
    }

    @Test
    fun aNewInstallDeletesWhatAnUninstalledCopyLeftInTheKeychain() = runTest {
        keychain.items["jwt"] = "old-jwt"
        keychain.items["api_token"] = "old-token"
        keychain.items["install_id"] = "ffffff"

        val storage = storage()

        assertNull(storage.getJwt())
        assertNull(storage.getApiToken())
        assertNotEquals("ffffff", storage.getInstallId())
        assertTrue(exists(file))
    }

    @Test
    fun aTokenStoredFirstOnANewInstallIsNotTakenForALeftover() = runTest {
        keychain.items["jwt"] = "old-jwt"
        val storage = storage()

        // The first thing this start does: the leftover is deleted before, not after.
        storage.storeJwt("new-jwt", 1_900_000_000)

        assertEquals("new-jwt", storage.getJwt())
        assertEquals(1_900_000_000L, storage.getJwtExpiry())
    }

    @Test
    fun aLockedKeychainLeavesTheMoveForALaterStart() = runTest {
        olderVersionSignedIn()
        keychain.items["jwt"] = "jwt-1"
        keychain.available = false

        val locked = storage()

        // Read from where it is, written nowhere.
        assertEquals("https://vikunja.test", locked.getVikunjaUrl())
        locked.storeInboxProjectId(13)
        assertEquals(13L, locked.getInboxProjectId())
        assertFalse(exists(file))
        assertTrue(LegacySessionDefaults.installedBefore(defaults))
        assertEquals(12L, defaults.integerForKey(LegacySessionDefaults.INBOX_PROJECT_ID))
        // A new id for now, which does not replace the one the Keychain could not read.
        assertNotEquals("a1b2c3", locked.getInstallId())

        keychain.available = true
        val unlocked = storage()
        assertEquals("https://vikunja.test", unlocked.getVikunjaUrl())
        assertEquals("a1b2c3", unlocked.getInstallId())
        assertTrue(exists(file))
    }

    @Test
    fun signingOutKeepsOnlyTheInstallId() = runTest {
        val storage = storage()
        val installId = storage.getInstallId()
        storage.storeVikunjaUrl("https://vikunja.test")
        storage.storeAuthMethod("oidc")
        storage.storeProviderKey("authentik")
        storage.storeJwt("jwt-1", 1_900_000_000)
        storage.storeRefreshToken("refresh-1")
        storage.storeBackupApiToken("tk_backup", 1_950_000_000, 77)
        storage.storeInboxProjectId(12)
        storage.storeUserId(5)

        storage.clear()

        assertNull(storage.getVikunjaUrl())
        assertNull(storage.getAuthMethod())
        assertNull(storage.getProviderKey())
        assertNull(storage.getJwt())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getApiToken())
        assertNull(storage.getBackupApiTokenId())
        assertNull(storage.getInboxProjectId())
        assertNull(storage.getUserId())
        assertEquals(0L, storage.getJwtExpiry())
        assertNull(storage.authMethodFlow.first())
        assertNull(storage.vikunjaUrlFlow.first())
        assertNull(storage.inboxProjectIdFlow.first())
        assertEquals(installId, storage.getInstallId())
        assertEquals(installId, storage().getInstallId())
        assertNull(storage().getVikunjaUrl())
    }

    @Test
    fun aUserTokenIsNotOursToRevoke() = runTest {
        val storage = storage()
        storage.storeBackupApiToken("tk_backup", 1_950_000_000, 77)
        assertEquals(77L, storage.getBackupApiTokenId())

        storage.storeApiToken("tk_user", 0)

        assertNull(storage.getBackupApiTokenId())
        assertNull(storage().getBackupApiTokenId())
        assertEquals("tk_user", storage.getApiToken())
    }
}
