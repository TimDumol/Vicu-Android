package com.rendyhd.vicu.ios

import com.rendyhd.vicu.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.concurrent.AtomicInt
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Runs [block] while iOS lets the app finish it in the background (a UIKit background task, about
 * 30 seconds): after the app went to the background, or when iOS started it for a notification
 * action. The work is cancelled when the time runs out or after [timeout].
 */
internal fun CoroutineScope.launchInBackgroundTask(name: String, timeout: Duration, block: suspend () -> Unit): Job {
    val app = UIApplication.sharedApplication
    val ended = AtomicInt(0)
    var taskId: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    fun end() {
        if (ended.compareAndSet(0, 1) && taskId != UIBackgroundTaskInvalid) app.endBackgroundTask(taskId)
    }
    val job = launch(start = CoroutineStart.LAZY) {
        try {
            withTimeoutOrNull(timeout) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("BackgroundTask", "$name failed: ${e.message}")
        } finally {
            end()
        }
    }
    taskId = app.beginBackgroundTaskWithName(name) {
        job.cancel()
        end()
    }
    job.start()
    return job
}
