package com.rendyhd.vicu.auth

import com.rendyhd.vicu.data.remote.BaseUrlHolder
import com.rendyhd.vicu.util.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * One background refresh of the session token, so the rotating refresh token stays alive while
 * the app is closed (Android's periodic worker, iOS's background refresh). It refreshes only when
 * there is a server, a refresh token and a signed-in session, and not while the session's
 * backoff window after a failed refresh is open.
 */
class TokenRefreshRun(
    private val authManager: AuthManager,
    private val tokenStorage: TokenStorage,
    private val baseUrlHolder: BaseUrlHolder,
) {
    enum class Outcome {
        /** Nothing to do (no server, no refresh token, not signed in, or backing off). */
        SKIPPED,
        REFRESHED,

        /** The session cannot be refreshed again (signed out on the server); retrying is useless. */
        FAILED_FINAL,

        /** A network or server problem; the platform may retry later. */
        FAILED_RETRY,
    }

    /** [source] names the caller in the auth debug log. */
    suspend fun run(source: String): Outcome {
        AuthDebugLog.log("WORKER_REFRESH", "$source started")

        // Cold start after the process was gone: the base URL is read from storage first.
        baseUrlHolder.ensureInitialized()
        if (baseUrlHolder.baseUrl.isEmpty()) {
            AuthDebugLog.log("WORKER_REFRESH", "skipped: no Vikunja URL")
            return Outcome.SKIPPED
        }
        if (tokenStorage.getRefreshToken() == null) {
            AuthDebugLog.log("WORKER_REFRESH", "skipped: no refresh token")
            return Outcome.SKIPPED
        }

        // Loads the tokens on a cold start.
        authManager.ensureInitializedAndGetToken()

        // A session that needs signing in again is not retried in the background.
        if (authManager.authState.value != AuthState.Authenticated) {
            AuthDebugLog.log("WORKER_REFRESH", "skipped: state=${authManager.authState.value}")
            return Outcome.SKIPPED
        }

        // Respects the session's backoff, so background runs do not pile on a server that is
        // rate-limiting or failing.
        if (!authManager.canAttemptRefreshNow()) {
            AuthDebugLog.log("WORKER_REFRESH", "skipped: backoff window active")
            return Outcome.SKIPPED
        }

        return try {
            AuthDebugLog.refreshAttempt(source)
            when (val result = authManager.withRefreshLock { authManager.performV2RefreshTyped() }) {
                is RefreshResult.Success -> {
                    AuthDebugLog.refreshResult(source, true)
                    Outcome.REFRESHED
                }
                is RefreshResult.Failure -> when (result.kind) {
                    RefreshFailure.Unauthorized, RefreshFailure.NoRefreshToken -> {
                        AuthDebugLog.refreshResult(source, false, "terminal=${result.kind::class.simpleName}")
                        Outcome.FAILED_FINAL
                    }
                    else -> {
                        AuthDebugLog.refreshResult(source, false, "transient=${result.kind::class.simpleName}, will retry")
                        Outcome.FAILED_RETRY
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "Background token refresh failed: ${e.message}")
            AuthDebugLog.logError("$source refresh", e)
            Outcome.FAILED_RETRY
        }
    }

    private companion object {
        const val TAG = "TokenRefreshRun"
    }
}
