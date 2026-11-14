package org.catosaur.app.ui

import org.catosaur.app.download.isAllowedThumbnailUrl
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

private const val THUMBNAIL_CONNECT_TIMEOUT_MS = 5_000
private const val THUMBNAIL_READ_TIMEOUT_MS = 10_000

/**
 * In-memory thumbnail cache for search results. Only allowlisted HTTPS ytimg URLs are fetched,
 * redirects are not followed (they could leave the allowlist), and nothing is written to disk.
 * All methods run on the EDT; the network read happens on a background thread.
 */
internal object ThumbnailCache {
    private const val MAX_ENTRIES = 200

    private val images = object : LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>?): Boolean =
            size > MAX_ENTRIES
    }
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()

    /** The image if it is already loaded, otherwise null. */
    fun cached(url: String): BufferedImage? = images[url]

    /** Starts loading [url] once. A failed load is not retried. [onLoaded] runs on the EDT when the image arrives. */
    fun request(url: String, onLoaded: () -> Unit) {
        if (images.containsKey(url) || url in failed || !isAllowedThumbnailUrl(url) || !pending.add(url)) return
        Thread({
            val image = fetch(url)
            SwingUtilities.invokeLater {
                pending.remove(url)
                if (image != null) {
                    images[url] = image
                    onLoaded()
                } else {
                    failed += url
                }
            }
        }, "thumbnail-loader").apply { isDaemon = true; start() }
    }

    private fun fetch(url: String): BufferedImage? = try {
        downloadThumbnail(url)
    } catch (e: IOException) {
        null
    }
}

/** Downloads one thumbnail. Throws IOException on network or decode failure. Redirects are not followed. */
internal fun downloadThumbnail(url: String): BufferedImage? {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = THUMBNAIL_CONNECT_TIMEOUT_MS
        connection.readTimeout = THUMBNAIL_READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode}")
        }
        connection.inputStream.use { ImageIO.read(it) }
    } finally {
        connection.disconnect()
    }
}
