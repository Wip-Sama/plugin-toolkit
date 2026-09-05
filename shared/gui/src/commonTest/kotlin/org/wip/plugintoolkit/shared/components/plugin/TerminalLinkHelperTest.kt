package org.wip.plugintoolkit.shared.components.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalLinkHelperTest {

    @Test
    fun testUrlDetection() {
        val line = "Check release at https://github.com/wip-sama/toolkit/releases/v1.0 for details."
        val spans = TerminalLinkHelper.findLinkSpans(line) { false }

        assertEquals(1, spans.size)
        val span = spans[0]
        assertTrue(span.isUrl)
        assertEquals("https://github.com/wip-sama/toolkit/releases/v1.0", span.target)
    }

    @Test
    fun testNonExistentPathIsNotDetected() {
        val line = "Executing: C:\\nonexistent\\fake_dir\\tool.exe --input test.txt"
        val spans = TerminalLinkHelper.findLinkSpans(line) { false }

        // Path does not exist, so no link span should be created
        assertTrue(spans.isEmpty(), "Non-existent path should not be detected as a link")
    }

    @Test
    fun testExistingPathWithoutSpaces() {
        val existingPaths = setOf("C:\\tools\\python.exe")
        val line = "Executing: C:\\tools\\python.exe --version"
        val spans = TerminalLinkHelper.findLinkSpans(line) { it in existingPaths }

        assertEquals(1, spans.size)
        val span = spans[0]
        assertEquals("C:\\tools\\python.exe", span.target)
        assertEquals(false, span.isUrl)
    }

    @Test
    fun testExistingPathWithSpacesAndArguments() {
        val existingPaths = setOf(
            "C:\\Users\\test\\python.exe",
            "E:\\Manga\\Fukushuu o Koinegau Saikyou Yuusha wa\\tt",
            "E:\\Manga\\Fukushuu o Koinegau Saikyou Yuusha wa\\tt2"
        )
        val line = "Executing: C:\\Users\\test\\python.exe E:\\Manga\\Fukushuu o Koinegau Saikyou Yuusha wa\\tt --output E:\\Manga\\Fukushuu o Koinegau Saikyou Yuusha wa\\tt2 --width x2"
        val spans = TerminalLinkHelper.findLinkSpans(line) { it in existingPaths }

        assertEquals(3, spans.size)
        assertEquals("C:\\Users\\test\\python.exe", spans[0].target)
        assertEquals("E:\\Manga\\Fukushuu o Koinegau Saikyou Yuusha wa\\tt", spans[1].target)
        assertEquals("E:\\Manga\\Fukushuu o Koinegau Saikyou Yuusha wa\\tt2", spans[2].target)
    }

    @Test
    fun testQuotedPathWithSpaces() {
        val existingPaths = setOf("C:\\Program Files\\App\\tool.exe")
        val line = "Running \"C:\\Program Files\\App\\tool.exe\" now"
        val spans = TerminalLinkHelper.findLinkSpans(line) { it in existingPaths }

        assertEquals(1, spans.size)
        assertEquals("C:\\Program Files\\App\\tool.exe", spans[0].target)
    }
}
