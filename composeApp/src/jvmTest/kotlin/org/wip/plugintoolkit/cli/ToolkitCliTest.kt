package org.wip.plugintoolkit.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlin.test.assertIs

class ToolkitCliTest {
    @Test
    fun `known commands are parsed without starting the desktop UI`() {
        assertEquals(ToolkitCliCommand.Help, parseToolkitCliCommand(arrayOf("--help")))
        assertEquals(ToolkitCliCommand.Plugins, parseToolkitCliCommand(arrayOf("plugins", "list")))
        assertEquals(ToolkitCliCommand.Flows, parseToolkitCliCommand(arrayOf("flows")))
    }

    @Test
    fun `desktop flags and unknown commands are distinguished`() {
        assertEquals(ToolkitCliInvocation.Desktop, parseToolkitCliInvocation(arrayOf("--background")))
        assertIs<ToolkitCliInvocation.Invalid>(parseToolkitCliInvocation(arrayOf("unknown")))
    }

    @Test
    fun `run cli prints decoded flow names from its data source`() = runTest {
        val output = mutableListOf<String>()

        val code = runToolkitCli(
            ToolkitCliCommand.Flows,
            output = output::add,
            dataLoader = { ToolkitCliData(flowNames = listOf("A/B")) }
        )

        assertEquals(0, code)
        assertEquals(listOf("A/B"), output)
    }

    @Test
    fun `run cli reports startup failure instead of waiting forever`() = runTest {
        val errors = mutableListOf<String>()

        val code = runToolkitCli(
            ToolkitCliCommand.Status,
            error = errors::add,
            dataLoader = { error("registry failed") }
        )

        assertEquals(1, code)
        kotlin.test.assertTrue(errors.single().contains("registry failed"))
    }
}
