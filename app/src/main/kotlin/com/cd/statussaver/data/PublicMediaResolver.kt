package com.cd.statussaver.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.ByteArrayOutputStream
import org.jsoup.Jsoup

/** Public pages and direct media only. Private platform APIs need a maintained integration. */
class PublicMediaResolver {
    fun open(url: String): HttpURLConnection {
        var next = normalizeUrl(url)
        repeat(6) {
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) StatusSaver/2.0")
            connection.setRequestProperty("Accept-Encoding", "identity")
            try {
                if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("The page returned an invalid redirect.")
                    next = normalizeUrl(URI(next).resolve(location).toString())
                    connection.disconnect()
                } else {
                    check(connection.responseCode in 200..299) {
                        "This link is unavailable (HTTP ${connection.responseCode}). Try sharing the media file instead."
                    }
                    return connection
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
        error("This link redirects too many times.")
    }

    fun resolve(url: String): String {
        val connection = open(url)
        try {
            val mime = connection.contentType.orEmpty().substringBefore(';').lowercase()
            if (MediaTypes.extension(mime) != null) return connection.url.toString()
            check(mime == "text/html" || mime == "application/xhtml+xml") { "The link does not contain supported media." }
            val html = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= MAX_PAGE_BYTES) { "The page is too large to read." }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            return extractMedia(html, connection.url.toString())
                ?: error("This page does not expose downloadable public media. Share an image/video file or use a direct media link. Sign-in pages and private stories are not supported.")
        } finally { connection.disconnect() }
    }

    companion object {
        private const val MAX_PAGE_BYTES = 2 * 1024 * 1024

        fun normalizeUrl(text: String): String {
            val value = Regex("https://[^\\s<>\"']+", RegexOption.IGNORE_CASE).find(text.trim())?.value
                ?: error("Enter an HTTPS link.")
            val uri = URI(value)
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                "Enter a valid HTTPS link."
            }
            return uri.toASCIIString()
        }

        fun extractMedia(html: String, pageUrl: String): String? {
            val document = Jsoup.parse(html, pageUrl)
            // Do not mistake a social page's Open Graph cover/logo for a post download.
            val selectors = listOf(
                "meta[property=og:video:secure_url]" to "content",
                "meta[property=og:video:url]" to "content",
                "meta[property=og:video]" to "content",
                "video[src]" to "src",
                "video source[src]" to "src",
                "meta[name=twitter:player:stream]" to "content"
            )
            for ((selector, attribute) in selectors) {
                for (element in document.select(selector)) {
                    val absolute = element.absUrl(attribute)
                    val normalized = runCatching { normalizeUrl(absolute) }.getOrNull() ?: continue
                    val pathMime = MediaTypes.fromName(URI(normalized).path.orEmpty())
                    val declaredMime = if (element.tagName() == "meta")
                        document.selectFirst("meta[property=og:video:type]")?.attr("content")
                    else element.attr("type")
                    // CDNs often use extensionless signed URLs. Require explicit video
                    // metadata and validate the response MIME again before saving.
                    if (pathMime?.startsWith("video/") == true ||
                        (declaredMime?.startsWith("video/") == true && MediaTypes.extension(declaredMime) != null)) return normalized
                }
            }
            return null
        }
    }
}
