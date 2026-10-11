package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.rendyhd.vicu.auth.OidcHandler
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLRequest
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.UIKit.navigationItem
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * Signs in with the identity provider in a web view, like the Android `OidcLoginActivity`. The
 * redirect URI is Vikunja's own frontend callback (already registered at the provider), so the
 * redirect is caught here before it loads; ASWebAuthenticationSession cannot catch an https
 * redirect to a server the app does not own.
 */
@Composable
actual fun rememberOidcLauncher(
    onResult: (code: String?, state: String?, error: String?) -> Unit
): (OidcHandler.AuthParams) -> Unit {
    val onResultState = rememberUpdatedState(onResult)
    return remember {
        { params ->
            val presenter = topViewController()
            if (presenter == null) {
                onResultState.value(null, null, "Could not show the sign-in page")
            } else {
                val signIn = OidcWebViewController(params) { code, state, error ->
                    onResultState.value(code, state, error)
                }
                val navigation = UINavigationController(rootViewController = signIn)
                // Only Cancel closes the sheet, so a swipe cannot leave the result unanswered.
                navigation.modalInPresentation = true
                presenter.presentViewController(navigation, animated = true, completion = null)
            }
        }
    }
}

/**
 * The sign-in page. It is its own navigation delegate: WKWebView holds the delegate weakly, and
 * the navigation controller holds this controller for as long as the sheet is up.
 */
private class OidcWebViewController(
    private val params: OidcHandler.AuthParams,
    private val onFinish: (code: String?, state: String?, error: String?) -> Unit,
) : UIViewController(nibName = null, bundle = null), WKNavigationDelegateProtocol {

    private var finished = false
    private var webView: WKWebView? = null

    override fun loadView() {
        val view = WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration())
        view.navigationDelegate = this
        webView = view
        this.view = view
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        title = "Sign in"
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
            target = this,
            action = NSSelectorFromString("cancel"),
        )
        val url = NSURL.URLWithString(params.authUrl)
        if (url == null) {
            finish(null, null, "Invalid sign-in address")
        } else {
            webView?.loadRequest(NSURLRequest.requestWithURL(url))
        }
    }

    @ObjCAction
    fun cancel() {
        finish(null, null, "Cancelled")
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        if (!url.startsWith(params.redirectUri)) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        val query = NSURLComponents.componentsWithString(url)?.queryItems
            ?.filterIsInstance<NSURLQueryItem>()
            ?.associate { it.name to it.value }
            .orEmpty()
        val error = query["error"]
        when {
            error != null -> finish(null, null, query["error_description"] ?: error)
            query["code"] != null -> finish(query["code"], query["state"], null)
            else -> finish(null, null, null)
        }
    }

    private fun finish(code: String?, state: String?, error: String?) {
        if (finished) return
        finished = true
        webView?.stopLoading()
        dismissViewControllerAnimated(true) { onFinish(code, state, error) }
    }
}
