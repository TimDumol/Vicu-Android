package com.rendyhd.vicu.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers view models use for work that must stay off the main thread (building a
 * screen's state out of several flows). Injected so a test can run it in the test's own steps.
 * [io] is for blocking file and database work; common code cannot name `Dispatchers.IO`.
 */
class AppDispatchers(
    val default: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = ioDispatcher,
)

/** `Dispatchers.IO` of the platform: it exists on the JVM and on Native, but not in common code. */
expect val ioDispatcher: CoroutineDispatcher
