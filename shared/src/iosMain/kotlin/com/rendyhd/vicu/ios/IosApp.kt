package com.rendyhd.vicu.ios

import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.rendyhd.vicu.auth.AuthDebugLog
import com.rendyhd.vicu.di.iosModule
import com.rendyhd.vicu.di.sharedModules
import com.rendyhd.vicu.ui.AppInputs
import com.rendyhd.vicu.notification.IosNotificationDelegate
import com.rendyhd.vicu.ui.UiStateStore
import com.rendyhd.vicu.ui.navigation.AppLink
import com.rendyhd.vicu.util.BuildInfo
import com.rendyhd.vicu.widget.AppleWidgetBridge
import com.rendyhd.vicu.widget.IosWidgetSnapshots
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import kotlin.experimental.ExperimentalNativeApi

/**
 * The entry points the Swift app calls (`IosApp.shared` in Swift): [start] from the app delegate
 * before anything else, [onActive] and [onBackground] from the scene phase, [openUrl] for links.
 */
object IosApp {
    private var started = false

    // The notification center holds its delegate weakly. Built here, not by Koin: Koin cannot
    // declare a subclass of an Objective-C class.
    private var notificationDelegate: IosNotificationDelegate? = null

    /** [widgetBridge] reloads the widgets through WidgetKit, which only Swift can call. */
    @OptIn(ExperimentalNativeApi::class)
    fun start(widgetBridge: AppleWidgetBridge?) {
        if (started) return
        started = true
        // Before anything logs or builds the HTTP client: release builds log no requests.
        BuildInfo.configure(isDebug = Platform.isDebugBinary)
        AuthDebugLog.init(null)
        startKoin { modules(sharedModules + iosModule) }
        SingletonImageLoader.setSafe { KoinPlatform.getKoin().get<ImageLoader>() }
        // Both before the app finishes launching: iOS hands a notification action or a background
        // task to the app right after, and needs the delegate and the handlers in place.
        val koin = KoinPlatform.getKoin()
        notificationDelegate = IosNotificationDelegate(
            actions = koin.get(),
            inputs = koin.get(),
            syncEngine = koin.get(),
            appScope = koin.get(),
        ).also { it.install() }
        koin.get<IosBackgroundTasks>().register()
        koin.get<IosWidgetSnapshots>().bridge = widgetBridge
        lifecycle().start()
    }

    fun onActive() = lifecycle().onActive()

    fun onBackground() = lifecycle().onBackground()

    /** The UI's state for the scene to keep while the app is away (scene storage in Swift). */
    fun saveUiState(): String = UiStateStore.save()

    /** Opens a `vicu://` link; false when it names nothing the app can open. */
    fun openUrl(url: String): Boolean {
        val link = AppLink.parse(url) ?: return false
        KoinPlatform.getKoin().get<AppInputs>().open(link)
        return true
    }

    /** Opens the task entry (the Control Center control, the Add task shortcut, the quick action). */
    fun openQuickAdd() {
        KoinPlatform.getKoin().get<AppInputs>().open(AppLink.AddTask())
    }

    private fun lifecycle(): IosAppLifecycle = KoinPlatform.getKoin().get()
}
