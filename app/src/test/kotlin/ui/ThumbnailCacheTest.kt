package org.catosaur.app.ui

import kotlin.test.Test
import kotlin.test.assertNotNull

class ThumbnailCacheTest {
    @Test
    fun downloadsAYouTubeThumbnailOverTheNetwork() {
        System.setProperty("java.net.preferIPv4Stack", "true")
        val image = downloadThumbnail("https://i.ytimg.com/vi/Y_ZpX5Rzm-E/mqdefault.jpg")
        assertNotNull(image, "thumbnail should decode to an image")
    }
}
