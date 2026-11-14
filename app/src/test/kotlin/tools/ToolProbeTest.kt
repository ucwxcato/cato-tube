package org.catosaur.app.tools

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolProbeTest {
    private val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()

    @Test
    fun foundToolReportsTheFirstOutputLine() {
        val check = ToolProbe.run(Tool.YT_DLP, listOf(java, "-version"), 10)
        assertEquals(ToolStatus.Found, check.status)
        assertNotNull(check.version)
    }

    @Test
    fun missingCommandIsReportedAsMissing() {
        val check = ToolProbe.run(Tool.DENO, listOf("catotube-no-such-tool"), 10)
        assertEquals(ToolStatus.Missing, check.status)
    }

    @Test
    fun nonzeroExitIsReportedAsFailed() {
        val check = ToolProbe.run(Tool.FFMPEG, listOf(java, "--catotube-unknown-flag"), 10)
        assertEquals(ToolStatus.Failed, check.status)
        assertNotNull(check.detail)
    }

    @Test
    fun hangingCommandTimesOutInsteadOfBlocking() {
        val check = ToolProbe.run(
            Tool.DENO,
            listOf("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 30"),
            2,
        )
        assertEquals(ToolStatus.Failed, check.status)
        assertTrue(check.detail.orEmpty().contains("no response"))
    }
}
