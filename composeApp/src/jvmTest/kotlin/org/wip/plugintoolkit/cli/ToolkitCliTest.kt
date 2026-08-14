package org.wip.plugintoolkit.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolkitCliTest {
    @Test
    fun `known commands are parsed without starting the desktop UI`() {
        assertEquals(ToolkitCliCommand.Help, parseToolkitCliCommand(arrayOf("--help")))
        assertEquals(ToolkitCliCommand.Plugins, parseToolkitCliCommand(arrayOf("plugins", "list")))
        assertEquals(ToolkitCliCommand.Flows, parseToolkitCliCommand(arrayOf("flows")))
    }

    @Test
    fun `desktop flags and unknown commands remain desktop arguments`() {
        assertNull(parseToolkitCliCommand(arrayOf("--background")))
        assertNull(parseToolkitCliCommand(arrayOf("unknown")))
    }
}
