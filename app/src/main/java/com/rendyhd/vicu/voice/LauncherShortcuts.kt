package com.rendyhd.vicu.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rendyhd.vicu.MainActivity
import com.rendyhd.vicu.R

/**
 * The app icon's long-press shortcuts: "Add task" (quick add) and "Add by voice"
 * ([VoiceAddActivity]). Published from code, not `shortcuts.xml`, because the debug build has its
 * own package name and a static shortcut has to name it.
 */
object LauncherShortcuts {
    fun publish(context: Context) {
        val addTask = ShortcutInfoCompat.Builder(context, "add_task")
            .setShortLabel(context.getString(R.string.shortcut_add_task))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(MainActivity.EXTRA_SHOW_TASK_ENTRY, true),
            )
            .build()
        val addByVoice = ShortcutInfoCompat.Builder(context, "add_task_voice")
            .setShortLabel(context.getString(R.string.shortcut_add_by_voice))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_mic))
            .setIntent(Intent(context, VoiceAddActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(addTask, addByVoice))
    }
}
