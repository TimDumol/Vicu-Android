package com.rendyhd.vicu.voice

import com.rendyhd.vicu.domain.model.Project
import com.rendyhd.vicu.ui.screens.taskentry.FieldSource
import com.rendyhd.vicu.ui.screens.taskentry.TaskEntryUiState
import com.rendyhd.vicu.ui.screens.taskentry.entryLabelsWords
import com.rendyhd.vicu.ui.screens.taskentry.entryPriorityName
import com.rendyhd.vicu.ui.screens.taskentry.resolveEntryFields
import com.rendyhd.vicu.util.ClockDay
import com.rendyhd.vicu.util.DateContext
import com.rendyhd.vicu.util.DateDisplay
import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.util.DateUtils
import com.rendyhd.vicu.util.parser.ParsedToken
import com.rendyhd.vicu.util.parser.ParserConfig
import com.rendyhd.vicu.util.parser.TaskParser
import com.rendyhd.vicu.util.parser.TokenType
import kotlinx.datetime.LocalDateTime

/** A field read from the words, as the voice card shows it under them ("Tomorrow 15:00", "Work"). */
data class VoicePill(val type: TokenType, val label: String)

/** The words heard so far, read like quick add: what to highlight, and the fields as pills. */
data class VoicePreview(val tokens: List<ParsedToken>, val pills: List<VoicePill>) {
    companion object {
        val EMPTY = VoicePreview(emptyList(), emptyList())
    }
}

/**
 * Reads [text] the way the task will be added (`TextTaskCreator`: the parser with the user's
 * settings, the quick add field rules, the Inbox unless a project is named), so the card shows
 * what letting go adds.
 */
fun voicePreview(
    text: String,
    config: ParserConfig,
    now: LocalDateTime,
    day: ClockDay,
    projects: List<Project>,
    inboxId: Long,
    format: DateDisplayFormat,
): VoicePreview {
    if (text.isBlank() || !config.enabled) return VoicePreview.EMPTY
    val parsed = TaskParser.parse(text, config, reference = now, zone = day.zone)
    val fields = resolveEntryFields(
        TaskEntryUiState(
            title = text,
            projectId = inboxId,
            allProjects = projects,
            parseResult = parsed,
            parserConfig = config,
        ),
        day.zone,
    )
    val pills = buildList {
        if (fields.dueSource == FieldSource.TEXT && fields.dueDate.isNotBlank()) {
            add(VoicePill(TokenType.DATE, DateDisplay.formatDue(DateContext.CHIP, fields.dueDate, day.date, day.zone, format)))
        }
        if (fields.parsedProjectName != null && !fields.projectNotFound) {
            projects.find { it.id == fields.projectId }?.let { add(VoicePill(TokenType.PROJECT, it.title)) }
        }
        entryLabelsWords(emptyList(), parsed.labels)?.let { add(VoicePill(TokenType.LABEL, it)) }
        if (fields.prioritySource == FieldSource.TEXT) {
            entryPriorityName(fields.priority)?.let { add(VoicePill(TokenType.PRIORITY, it)) }
        }
        if (fields.recurrenceSource == FieldSource.TEXT) {
            add(VoicePill(TokenType.RECURRENCE, DateUtils.formatRecurrence(fields.recurrence.repeatAfter, fields.recurrence.repeatMode)))
        }
    }
    // A project that does not exist is not highlighted: the task goes to the Inbox.
    val tokens = parsed.tokens.filter { it.type != TokenType.PROJECT || !fields.projectNotFound }
    return VoicePreview(tokens, pills)
}
