package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeColorParsingTest {
    @Test
    fun `parses the ARGB order emitted by the color formatter`() {
        assertEquals(Color(0x80336699.toInt()).toArgb(), parseColorString("#80336699").toArgb())
        assertEquals(Color(0x80336699.toInt()).toArgb(), parseColorString("80336699").toArgb())
    }

    @Test
    fun `parses functional colors before treating commas as array separators`() {
        assertEquals(Color.Red.toArgb(), parseColorString("rgb(255, 0, 0)").toArgb())
        assertEquals(Color(0x80FF0000.toInt()).toArgb(), parseColorString("rgba(255, 0, 0, 0.5)").toArgb())
    }

    @Test
    fun `parses short RGBA consistently with alpha detection`() {
        assertEquals(Color(0xAAFF0000.toInt()).toArgb(), parseColorString("#F00A").toArgb())
    }
}
