package org.wip.plugintoolkit.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.nio.file.Files

class ToolkitCliTest {
    @Test
    fun `known commands are parsed without starting the desktop UI`() {
        assertEquals(ToolkitCliCommand.Help, parseToolkitCliCommand(arrayOf("--help")))
        assertEquals(ToolkitCliCommand.Plugins, parseToolkitCliCommand(arrayOf("plugins", "list")))
        assertEquals(ToolkitCliCommand.Flows, parseToolkitCliCommand(arrayOf("flows")))
    }

    @Test
    fun `desktop flags and launcher arguments do not abort GUI startup`() {
        assertEquals(ToolkitCliInvocation.Desktop, parseToolkitCliInvocation(arrayOf("--background")))
        assertEquals(ToolkitCliInvocation.Desktop, parseToolkitCliInvocation(arrayOf("--launcher-token")))
        assertEquals(ToolkitCliInvocation.Desktop, parseToolkitCliInvocation(arrayOf("document.toolkit")))
        assertIs<ToolkitCliInvocation.Invalid>(parseToolkitCliInvocation(arrayOf("plugins", "unknown")))
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

    @Test
    fun `real flow loader reads current and legacy storage without Koin or migration`() = runTest {
        val root = Files.createTempDirectory("toolkit-cli-flows")
        val flowsDir = Files.createDirectories(root.resolve("flows"))
        Files.writeString(flowsDir.resolve("current.json"), """{"name":"Current","nodes":[],"connections":[]}""")
        val legacy = root.resolve("flows.json")
        Files.writeString(legacy, """[{"name":"Legacy","nodes":[],"connections":[]}]""")

        val data = loadToolkitCliData(ToolkitCliCommand.Flows, settingsDir = root.toString())

        assertEquals(setOf("Current", "Legacy"), data.flowNames.toSet())
        assertTrue(Files.exists(legacy), "CLI reads must not migrate or delete legacy data")
        assertEquals(1, Files.list(flowsDir).use { it.count() })
    }

    @Test
    fun `real plugin loader reads registry without Koin or rewriting it`() = runTest {
        val root = Files.createTempDirectory("toolkit-cli-plugins")
        val pluginsDir = Files.createDirectories(root.resolve("plugins"))
        val registry = pluginsDir.resolve("installed_plugins.json")
        val original = """[{"pkg":"example.plugin","name":"Example","version":"1.0","installPath":"/plugins/example"}]"""
        Files.writeString(registry, original)

        val data = loadToolkitCliData(ToolkitCliCommand.Plugins, settingsDir = root.toString())

        assertEquals(listOf("example.plugin"), data.plugins.map { it.pkg })
        assertEquals(original, Files.readString(registry), "CLI reads must not rewrite registry state")
    }
}
