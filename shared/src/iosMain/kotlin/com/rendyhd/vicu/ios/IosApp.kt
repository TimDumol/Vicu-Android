package com.rendyhd.vicu.ios

import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.rendyhd.vicu.di.iosModule
import com.rendyhd.vicu.di.sharedModules
import com.rendyhd.vicu.ui.AppInputs
import com.rendyhd.vicu.ui.navigation.AppLink
import com.rendyhd.vicu.util.BuildInfo
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import kotlin.experimental.ExperimentalNativeApi

/**
 * The entry points the Swift app calls (`IosApp.shared` in Swift): [start] from the app delegate
 * before anything else, [onActive] and [onBackground] from the scene phase, [openUrl] for links.
 */
object IosApp {
    private var started = false

    @OptIn(ExperimentalNativeApi::class)
    fun start() {
        if (started) return
        started = true
        // Before anything logs or builds the HTTP client: release builds log no requests.
        BuildInfo.configure(isDebug = Platform.isDebugBinary)
        startKoin { modules(sharedModules + iosModule) }
        SingletonImageLoader.setSafe { KoinPlatform.getKoin().get<ImageLoader>() }
        lifecycle().start()
    }

    fun onActive() = lifecycle().onActive()

    fun onBackground() = lifecycle().onBackground()

    /** Opens a `vicu://` link; false when it names nothing the app can open. */
    fun openUrl(url: String): Boolean {
        val link = AppLink.parse(url) ?: return false
        KoinPlatform.getKoin().get<AppInputs>().open(link)
        return true
    }

    private fun lifecycle(): IosAppLifecycle = KoinPlatform.getKoin().get()
}
