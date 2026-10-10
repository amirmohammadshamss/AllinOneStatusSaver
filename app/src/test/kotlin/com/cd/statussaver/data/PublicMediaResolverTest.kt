package com.cd.statussaver.data

import org.junit.Assert.*
import org.junit.Test

class PublicMediaResolverTest {
    @Test fun acceptsLinkSharedWithCaption() {
        assertEquals("https://example.com/post?id=12", PublicMediaResolver.normalizeUrl("Watch this https://example.com/post?id=12"))
    }

    @Test fun rejectsInsecureAndCredentialLinks() {
        for (url in listOf("http://example.com/video.mp4", "file:///tmp/video.mp4", "https://user:password@example.com/video.mp4", "https://")) {
            assertTrue(url, runCatching { PublicMediaResolver.normalizeUrl(url) }.isFailure)
        }
    }

    @Test fun resolvesRelativeVideoAndHtmlEntities() {
        val html = """<meta property="og:video" content="/clip.mp4?x=1&amp;y=2">"""
        assertEquals("https://example.com/clip.mp4?x=1&y=2", PublicMediaResolver.extractMedia(html, "https://example.com/post"))
    }

    @Test fun recognizesVideoSourceWithSignedQuery() {
        val html = """<video><source src="https://cdn.example.com/video.webm?token=abc"></video>"""
        assertEquals("https://cdn.example.com/video.webm?token=abc", PublicMediaResolver.extractMedia(html, "https://example.com"))
    }

    @Test fun ignoresPageCoversAndInsecureMedia() {
        assertNull(PublicMediaResolver.extractMedia("""<meta property="og:image" content="https://example.com/logo.jpg">""", "https://example.com"))
        assertNull(PublicMediaResolver.extractMedia("""<video src="http://example.com/video.mp4"></video>""", "https://example.com"))
    }

    @Test fun doesNotDownloadAnEmbeddedPlayerPageAsVideo() {
        assertNull(PublicMediaResolver.extractMedia("""<meta property="og:video" content="https://example.com/player">""", "https://example.com"))
    }

    @Test fun checksMimeInsteadOfTrustingAnHtmlResponse() {
        assertNull(MediaTypes.extension("text/html"))
        assertEquals("mp4", MediaTypes.extension("video/mp4; charset=UTF-8"))
        assertEquals("image/jpeg", MediaTypes.fromName("STATUS.JPEG"))
    }

    @Test fun acceptsQuotedSharedLinks() {
        assertEquals("https://example.com/clip.mp4", PublicMediaResolver.normalizeUrl("Watch \"https://example.com/clip.mp4\""))
    }

    @Test fun recognizesExtensionlessSourceWithVideoMime() {
        val html = """<video><source type="video/mp4" src="https://cdn.example.com/media?id=1"></video>"""
        assertEquals("https://cdn.example.com/media?id=1", PublicMediaResolver.extractMedia(html, "https://example.com"))
    }

    @Test fun recognizesExtensionlessOpenGraphVideoWithMime() {
        val html = """<meta property="og:video:type" content="video/mp4"><meta property="og:video" content="https://cdn.example.com/stream?id=1">"""
        assertEquals("https://cdn.example.com/stream?id=1", PublicMediaResolver.extractMedia(html, "https://example.com"))
    }

    @Test fun rejectsExtensionlessSourceWithoutVideoMime() {
        assertNull(PublicMediaResolver.extractMedia("""<video><source type="text/html" src="https://example.com/player"></video>""", "https://example.com"))
    }
}
