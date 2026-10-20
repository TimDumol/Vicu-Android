package com.rendyhd.vicu.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.rendyhd.vicu.MainActivity
import com.rendyhd.vicu.R
import com.rendyhd.vicu.voice.VoiceAddActivity

/**
 * Two buttons on the home screen: the mic adds a task by voice without opening the app
 * ([VoiceAddActivity]); + opens quick add.
 */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = loadWidgetColors(context)
        val openQuickAdd = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHOW_TASK_ENTRY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        provideContent {
            GlanceTheme(colors = colors) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoundButton(
                        icon = R.drawable.ic_widget_mic,
                        label = "Add a task by voice",
                        action = actionStartActivity<VoiceAddActivity>(),
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    RoundButton(
                        icon = R.drawable.ic_widget_add,
                        label = "Add a task",
                        action = actionStartActivity(openQuickAdd),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, action: Action) {
    Box(
        modifier = GlanceModifier
            .size(48.dp)
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.primary)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = label,
            modifier = GlanceModifier.size(36.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        )
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
