package com.rendyhd.vicu.data.remote

import coil3.intercept.Interceptor
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageResult

/**
 * Images of the Vikunja server (attachments, avatars) are requested as `http://localhost/<path>`,
 * a placeholder the image loader rewrites to the configured server. Only the placeholder host is
 * rewritten and authenticated, so credentials never go to an unrelated host.
 */
object VikunjaImages {
    const val PLACEHOLDER_HOST = "localhost"

    /** [url] on the server at [fullBaseUrl], or null when [url] is not a placeholder URL. */
    fun rewrite(url: String, fullBaseUrl: String): String? {
        val placeholder = Regex("""^https?://$PLACEHOLDER_HOST(?::\d+)?(/[^?#]*)?(\?[^#]*)?(#.*)?$""")
            .matchEntire(url) ?: return null
        val base = Regex("""^(https?://[^/?#]+)(/[^?#]*)?""").find(fullBaseUrl) ?: return null
        val origin = base.groupValues[1]
        val basePath = base.groupValues[2].trimEnd('/')
        val requestPath = placeholder.groupValues[1].trimStart('/')
        val path = when {
            basePath.isEmpty() && requestPath.isEmpty() -> "/"
            basePath.isEmpty() -> "/$requestPath"
            requestPath.isEmpty() -> basePath
            else -> "$basePath/$requestPath"
        }
        return origin + path + placeholder.groupValues[2]
    }
}

/**
 * The Coil interceptor for [VikunjaImages] on platforms whose image loader does not go through
 * OkHttp (iOS; Android does the same in its OkHttp interceptor).
 */
class VikunjaImageInterceptor(
    private val fullBaseUrl: suspend () -> String,
    private val token: suspend () -> String?,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: request.data.toString()
        val rewritten = VikunjaImages.rewrite(url, fullBaseUrl()) ?: return chain.proceed()
        val bearer = token()?.takeIf { it.isNotBlank() }
        val headers = request.httpHeaders.newBuilder().apply {
            if (bearer != null) set("Authorization", "Bearer $bearer")
        }.build()
        return chain.withRequest(
            request.newBuilder().data(rewritten).httpHeaders(headers).build(),
        ).proceed()
    }
}
