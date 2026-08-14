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
}
