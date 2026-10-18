package com.rendyhd.vicu.auth

/** Work the platform starts when the session changes; see [AuthTransitions]. */
enum class AuthTransitionAction {
    /** The first time the app is signed in after it starts: make sure the daily summaries are queued. */
    ENSURE_SUMMARIES,

    /** Signed in again after the session expired: the changes queued meanwhile can go now. */
    SYNC_NOW,

    /** Signed in after a sign-out: schedule the account's background work again. */
    RESCHEDULE_ACCOUNT_WORK,
}

/**
 * The rules the Android activity follows when the auth state changes, as a pure state machine so
 * every platform runs the same ones. Feed it each state in order.
 */
class AuthTransitions {
    private var ensuredAtStart = false
    private var neededReAuth = false
    private var signedOut = false

    fun on(state: AuthState): Set<AuthTransitionAction> {
        val actions = mutableSetOf<AuthTransitionAction>()
        if (state == AuthState.Authenticated) {
            if (!ensuredAtStart) {
                ensuredAtStart = true
                actions += AuthTransitionAction.ENSURE_SUMMARIES
            }
            if (neededReAuth) actions += AuthTransitionAction.SYNC_NOW
            if (signedOut) actions += AuthTransitionAction.RESCHEDULE_ACCOUNT_WORK
        }
        neededReAuth = state == AuthState.NeedsReAuth
        signedOut = state == AuthState.Unauthenticated
        return actions
    }
}
