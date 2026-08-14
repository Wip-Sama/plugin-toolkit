package org.wip.plugintoolkit.features.colorpicker.utils

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorExtTest {
    @Test
    fun `hex parser accepts rgb and argb values`() {
        assertEquals(Color(0xFF336699.toInt()), parseHexColor("#336699"))
        assertEquals(Color(0x80336699.toInt()), parseHexColor("80336699"))
    }

    @Test
    fun `hex parser rejects malformed values`() {
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GG3366"))
    }
}
