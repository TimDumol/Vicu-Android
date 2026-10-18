package com.rendyhd.vicu.data.local

import com.rendyhd.vicu.data.local.dao.TaskDao
import com.rendyhd.vicu.util.ClockDay
import com.rendyhd.vicu.util.DailySummary

/** What a daily summary notification contains. */
data class DailySummaryContent(
    val counts: DailySummary.Counts,
    /** Up to a few titles of tasks due today (not overdue), earliest first. */
    val dueTodayTitles: List<String>,
) {
    /**
     * The lines of the expanded notification: today's titles, how many more are due today, then
     * the overdue and tomorrow counts.
     */
    fun lines(): List<String> = buildList {
        addAll(dueTodayTitles)
        val remaining = counts.dueToday - dueTodayTitles.size
        if (remaining > 0) add("+$remaining more today")
        if (counts.overdue > 0) add("${counts.overdue} overdue tasks need attention")
        if (counts.dueTomorrow > 0) add("${counts.dueTomorrow} due tomorrow")
    }
}

/** Reads the counts of the daily summary from the local database. */
class DailySummaryReader(private val taskDao: TaskDao) {

    suspend fun read(
        day: ClockDay,
        includeOverdue: Boolean,
        includeDueToday: Boolean,
        includeTomorrow: Boolean,
    ): DailySummaryContent {
        val b = DailySummary.boundaries(day.date, day.zone)
        val counts = DailySummary.Counts(
            overdue = if (includeOverdue) taskDao.countOverdue(b.startOfToday) else 0,
            dueToday = if (includeDueToday) taskDao.countDueToday(b.startOfToday, b.startOfTomorrow) else 0,
            dueTomorrow = if (includeTomorrow) taskDao.countDueTomorrow(b.startOfTomorrow, b.startOfDayAfterTomorrow) else 0,
        )
        val titles = if (counts.dueToday > 0) {
            taskDao.getDueTodaySync(b.startOfToday, b.startOfTomorrow, TITLE_LIMIT).map { it.title }
        } else {
            emptyList()
        }
        return DailySummaryContent(counts, titles)
    }

    companion object {
        const val TITLE_LIMIT = 3
    }
}
