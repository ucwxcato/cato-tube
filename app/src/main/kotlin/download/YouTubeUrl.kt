package org.catosaur.app.download

import java.net.URI
import java.net.URISyntaxException

/** Accepts single-video HTTP(S) links on youtube.com, m.youtube.com, and youtu.be. */
internal fun isYouTubeUrl(value: String): Boolean = try {
    val uri = URI(value)
    val host = uri.host?.lowercase()?.removePrefix("www.")
    uri.scheme in setOf("http", "https") && host in setOf("youtube.com", "m.youtube.com", "youtu.be")
} catch (_: URISyntaxException) {
    false
}
