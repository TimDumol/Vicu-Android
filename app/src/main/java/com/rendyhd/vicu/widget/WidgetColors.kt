package com.rendyhd.vicu.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as glanceColorProviders
import com.rendyhd.vicu.data.local.ThemePrefsStore
import com.rendyhd.vicu.ui.theme.VicuDarkColorScheme
import com.rendyhd.vicu.ui.theme.VicuLightColorScheme
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext

/**
 * The widgets' colours: the Vicu scheme, or the wallpaper scheme when Settings has device colours on
 * (Android 12 and later), the same choice as `VicuTheme`.
 *
 * Both are resolved here, in the app, and handed to Glance as fixed day and night values. Glance's
 * default theme instead points at the system palette through resources that the launcher resolves
 * when it draws the widget, which left the widgets on the wallpaper colours after the app moved to
 * the Vicu scheme, and on some launchers produced secondary text, checkbox outlines and a + glyph
 * with no contrast against the widget background.
 */
internal fun widgetSchemes(context: Context, useDeviceColors: Boolean): WidgetSchemes =
    if (useDeviceColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WidgetSchemes(light = dynamicLightColorScheme(context), dark = dynamicDarkColorScheme(context))
    } else {
        WidgetSchemes(light = VicuLightColorScheme, dark = VicuDarkColorScheme)
    }

/** The light and dark schemes of [widgetSchemes]; a routine widget also works its routines' colours out from them. */
internal data class WidgetSchemes(val light: ColorScheme, val dark: ColorScheme) {
    fun toColorProviders(): ColorProviders = glanceColorProviders(light = light, dark = dark)
}

/** The Glance colours of [loadWidgetSchemes]. */
internal suspend fun loadWidgetColors(context: Context): ColorProviders = loadWidgetSchemes(context).toColorProviders()

/** [widgetSchemes] for the current device-colours setting; the Vicu scheme when the setting cannot be read. */
internal suspend fun loadWidgetSchemes(context: Context): WidgetSchemes {
    val useDeviceColors = runCatching {
        GlobalContext.get().get<ThemePrefsStore>().useDeviceColors.first()
    }.getOrDefault(false)
    return widgetSchemes(context, useDeviceColors)
}
