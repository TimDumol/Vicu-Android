package com.rendyhd.vicu.util

import com.rendyhd.vicu.auth.AuthManager
import com.rendyhd.vicu.auth.AuthState
import com.rendyhd.vicu.data.local.NlpPrefsStore
import com.rendyhd.vicu.data.local.NotificationPrefsStore
import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.repository.LabelRepository
import com.rendyhd.vicu.domain.repository.ProjectRepository
import com.rendyhd.vicu.domain.repository.TaskRepository
import com.rendyhd.vicu.location.LocationAccess
import com.rendyhd.vicu.location.LocationPermissionPlatform
import com.rendyhd.vicu.location.LocationReminder
import com.rendyhd.vicu.location.LocationReminderStore
import com.rendyhd.vicu.location.LocationTrigger
import com.rendyhd.vicu.location.PlacePhrase
import com.rendyhd.vicu.location.PlacePhrases
import com.rendyhd.vicu.location.SavedPlacesStore
import com.rendyhd.vicu.ui.screens.taskdetail.withoutLineBreaks
import com.rendyhd.vicu.ui.screens.taskentry.TaskEntryUiState
import com.rendyhd.vicu.ui.screens.taskentry.resolveEntryFields
import com.rendyhd.vicu.ui.screens.taskentry.resolveParsedLabels
import com.rendyhd.vicu.util.parser.TaskParser
import com.rendyhd.vicu.util.parser.TokenType
import com.rendyhd.vicu.util.parser.extractBangToday
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.toLocalDateTime

/**
 * Makes a task from one sentence, the way quick add reads it ("Call mom tomorrow 3pm #family"),
 * without a screen: for a voice command (Siri, the Android voice add). The task goes to the
 * project the text names, otherwise the Inbox, and is stored on the device first and queued, so
 * it is never lost and the caller gets an answer at once; the sync sends it.
 *
 * "When I get home" or "when I leave work" for a saved place also sets the task's location
 * reminder ([PlacePhrases]).
 */
class TextTaskCreator(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val labelRepository: LabelRepository,
    private val authManager: AuthManager,
    private val nlpPrefsStore: NlpPrefsStore,
    private val notificationPrefsStore: NotificationPrefsStore,
    private val dayClock: DayClock,
    private val time: TimeSource = SystemTimeSource,
    private val savedPlaces: SavedPlacesStore? = null,
    private val locationReminders: LocationReminderStore? = null,
    private val locationPermission: LocationPermissionPlatform? = null,
) {
    sealed interface Result {
        /** [confirmation] says what was added, for the voice reply or a toast. */
        data class Created(val task: Task, val confirmation: String) : Result

        data class Failed(val message: String) : Result
    }

    suspend fun create(text: String, format: DateDisplayFormat): Result {
        val input = text.withoutLineBreaks().trim()
        if (input.isBlank()) return Result.Failed("Say what the task is.")

        // A cold start (a voice command can launch the app) restores the session first.
        val auth = withTimeoutOrNull(AUTH_WAIT_MS) {
            authManager.authState.first { it != AuthState.Loading }
        }
        if (auth == null || auth == AuthState.Unauthenticated) return Result.Failed("Sign in to Vicu first.")
        val inboxId = authManager.getInboxProjectId()?.takeIf { it > 0 }
            ?: return Result.Failed("Choose an Inbox in Vicu first.")
        val placePhrase = if (locationReminders != null) PlacePhrases.find(input, savedPlaces?.all().orEmpty()) else null
        val sentence = placePhrase?.title ?: input

        dayClock.refresh()
        val day = dayClock.day.value
        val config = nlpPrefsStore.config.first()
        val projects = projectRepository.getAll().first()
        val labels = labelRepository.getAll().first()
        val now = time.now().toLocalDateTime(day.zone)
        val parsed = if (config.enabled) TaskParser.parse(sentence, config, reference = now, zone = day.zone) else null

        // The same rules as the quick add sheet with nothing picked by hand (TaskEntryFields.kt).
        val fields = resolveEntryFields(
            TaskEntryUiState(
                title = sentence,
                projectId = inboxId,
                allProjects = projects,
                parseResult = parsed,
                parserConfig = config,
            ),
            day.zone,
        )
        var title = parsed?.title ?: sentence
        var dueDate = fields.dueDate
        if (config.bangToday && TokenType.DATE !in config.suppressTypes && fields.dueSource == null) {
            val bang = extractBangToday(title, now)
            if (bang.dueDate != null) {
                title = bang.title
                dueDate = DueDates.bang(day.date, day.zone).toString()
            }
        }
        title = title.trim()
        if (title.isBlank()) return Result.Failed("Say what the task is.")

        val labelIds = if (parsed != null) resolveParsedLabels(parsed.labels, labels, labelRepository) else emptySet()
        val prefs = notificationPrefsStore.getPrefs().first()
        val task = Task(
            id = 0,
            title = title,
            dueDate = dueDate,
            priority = fields.priority,
            projectId = fields.projectId,
            repeatAfter = fields.recurrence.repeatAfter,
            repeatMode = fields.recurrence.repeatMode,
            reminders = listOfNotNull(
                DefaultReminder.build(
                    dueDate = dueDate,
                    offsetSeconds = prefs.defaultReminderOffset,
                    relativeTo = prefs.defaultReminderRelativeTo,
                ),
            ),
        )
        return when (val result = taskRepository.createQueued(task)) {
            is NetworkResult.Success -> {
                val created = result.data
                for (labelId in labelIds) labelRepository.addToTask(created.id, labelId)
                placePhrase?.let { phrase ->
                    locationReminders?.put(
                        LocationReminder(
                            taskId = created.id,
                            placeId = phrase.place.id,
                            placeName = phrase.place.name,
                            latitude = phrase.place.latitude,
                            longitude = phrase.place.longitude,
                            trigger = phrase.trigger,
                        ),
                    )
                }
                val projectName = projects.find { it.id == created.projectId }
                    ?.takeIf { it.id != inboxId }?.title
                var reply = confirmation(created.title, dueDate, projectName, day, format, placePhrase)
                if (placePhrase != null && !hasAlwaysLocation()) reply += ". $NEEDS_ALWAYS"
                Result.Created(created, reply)
            }
            is NetworkResult.Error -> Result.Failed(result.message)
            NetworkResult.Loading -> Result.Failed("Could not add the task.")
        }
    }

    private fun hasAlwaysLocation(): Boolean {
        val permission = locationPermission ?: return true
        permission.refresh()
        return permission.access.value == LocationAccess.ALWAYS
    }

    companion object {
        private const val AUTH_WAIT_MS = 5_000L
        internal const val NEEDS_ALWAYS = "Set Vicu's location access to Always so it can remind you there"

        /** `Added "Call mom", tomorrow 15:00`, `Added "Report" to Work` or `Added "Call mom", when you arrive at Home`. */
        internal fun confirmation(
            title: String,
            dueDate: String,
            projectName: String?,
            day: ClockDay,
            format: DateDisplayFormat,
            place: PlacePhrase? = null,
        ): String = buildString {
            append("Added “").append(title).append("”")
            if (projectName != null) append(" to ").append(projectName)
            // The row's phrase reads well aloud ("tomorrow, 15:00", "Friday"), in sentence case.
            val due = if (dueDate.isBlank()) "" else DateDisplay.formatDue(DateContext.ROW, dueDate, day.date, day.zone, format)
            if (due.isNotBlank()) {
                append(", ")
                append(if (due.startsWith("Today") || due.startsWith("Tomorrow")) due.replaceFirstChar { it.lowercase() } else due)
            }
            if (place != null) {
                append(", when you ")
                append(if (place.trigger == LocationTrigger.ARRIVE) "arrive at " else "leave ")
                append(place.place.name)
            }
        }
    }
}
