package org.wip.plugintoolkit.api.processor.generators

import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestGeneratorEscapingTest {
    @Test
    fun stringListUsesKotlinStringEscaping() {
        val generated = stringListCodeBlock(listOf("quote\"", "slash\\", "dollar\$value")).toString()

        assertEquals("listOf(\"quote\\\"\", \"slash\\\\\", \"dollar\${'\$'}value\")", generated)
    }
}
