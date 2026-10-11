package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.repository.PlatformRepositoryHooks
import com.rendyhd.vicu.util.IosCompletionSoundPlayer
import com.rendyhd.vicu.worker.SyncRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The iOS side of the repositories: sync requests go to [SyncRequester], the completion sound to
 * [IosCompletionSoundPlayer]. Reminders are not scheduled yet: they come with the notification
 * budget, since iOS keeps at most 64 pending local notifications.
 */
class IosRepositoryHooks(
    private val syncRequester: () -> SyncRequester,
    private val soundPlayer: IosCompletionSoundPlayer,
    private val appScope: CoroutineScope,
) : PlatformRepositoryHooks {

    override fun triggerSync() {
        syncRequester().request()
    }

    override fun updateWidgets() {}

    override fun playCompletionSound() {
        appScope.launch { soundPlayer.play() }
    }

    override suspend fun scheduleAlarm(task: Task) {}

    override suspend fun cancelAlarm(taskId: Long) {}

    override suspend fun rescheduleAlarms() {}
}
