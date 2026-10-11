package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.repository.PlatformRepositoryHooks

/**
 * The iOS side of the repositories. Reminders are not scheduled yet: they come with the
 * notification budget, since iOS keeps at most 64 pending local notifications.
 */
class IosRepositoryHooks : PlatformRepositoryHooks {

    override fun triggerSync() {}

    override fun updateWidgets() {}

    override fun playCompletionSound() {}

    override suspend fun scheduleAlarm(task: Task) {}

    override suspend fun cancelAlarm(taskId: Long) {}

    override suspend fun rescheduleAlarms() {}
}
