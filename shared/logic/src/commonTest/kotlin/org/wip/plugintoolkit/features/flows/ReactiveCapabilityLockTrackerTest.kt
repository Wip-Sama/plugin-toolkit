package org.wip.plugintoolkit.features.flows

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.logic.NodeReadinessState
import org.wip.plugintoolkit.features.flows.logic.ReactiveCapabilityLockTracker
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReactiveCapabilityLockTrackerTest {

    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val tracker = ReactiveCapabilityLockTracker(pluginManager)

    private val testPluginInfo = PluginInfo(
        id = "test.plugin",
        name = "Test Plugin",
        version = "1.0.0",
        description = "Testing tracker"
    )

    private val lockedCapability = Capability(
        name = "OCR",
        description = "Optical Character Recognition",
        requiredLocks = listOf("unlimited_ocr"),
        returnType = DataType.Primitive(PrimitiveType.STRING)
    )

    private val dummyManifest = PluginManifest(
        manifestVersion = "1.0",
        plugin = testPluginInfo,
        requirements = org.wip.plugintoolkit.api.Requirements(minMemoryMb = 256, minExecutionTimeMs = 1000),
        capabilities = listOf(lockedCapability)
    )

    @Test
    fun testObserveCapabilityStatusLockedWhenLockMissing() = runTest {
        val locksFlow = MutableStateFlow<Map<String, Map<String, Boolean>>>(
            mapOf("test.plugin" to mapOf("unlimited_ocr" to false))
        )
        every { pluginManager.pluginLocksState } returns locksFlow
        every { pluginManager.installedPlugins } returns MutableStateFlow(
            listOf(InstalledPlugin("test.plugin", "Test Plugin", "1.0.0", "/path"))
        )
        every { pluginManager.getManifest("test.plugin") } returns dummyManifest
        every { pluginManager.loadPluginSettings("test.plugin") } returns PluginSettingsStore()

        val status = tracker.observeCapabilityStatus("test.plugin", lockedCapability).first()

        assertIs<NodeReadinessState.Locked>(status)
        assertEquals(listOf("unlimited_ocr"), status.missingLocks)
    }

    @Test
    fun testObserveCapabilityStatusUnlockedWhenLockSatisfied() = runTest {
        val locksFlow = MutableStateFlow<Map<String, Map<String, Boolean>>>(
            mapOf("test.plugin" to mapOf("unlimited_ocr" to true))
        )
        every { pluginManager.pluginLocksState } returns locksFlow
        every { pluginManager.installedPlugins } returns MutableStateFlow(
            listOf(InstalledPlugin("test.plugin", "Test Plugin", "1.0.0", "/path"))
        )
        every { pluginManager.getManifest("test.plugin") } returns dummyManifest
        every { pluginManager.loadPluginSettings("test.plugin") } returns PluginSettingsStore()

        val status = tracker.observeCapabilityStatus("test.plugin", lockedCapability).first()

        assertIs<NodeReadinessState.Ready>(status)
    }

    @Test
    fun testValidateFlowForExecutionFailsOnLockedCapability() {
        val locksFlow = MutableStateFlow<Map<String, Map<String, Boolean>>>(
            mapOf("test.plugin" to mapOf("unlimited_ocr" to false))
        )
        every { pluginManager.pluginLocksState } returns locksFlow
        every { pluginManager.loadPluginSettings("test.plugin") } returns PluginSettingsStore()

        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = testPluginInfo,
            capability = lockedCapability,
            inputs = emptyList(),
            outputs = emptyList()
        )
        val flow = Flow("TestFlow", nodes = listOf(node))

        val result = tracker.validateFlowForExecution(flow)
        assertTrue(result.isFailure, "Flow should fail validation when capability lock is unsatisfied")
    }

    @Test
    fun testValidateFlowForExecutionSucceedsWhenAllLocksSatisfied() {
        val locksFlow = MutableStateFlow<Map<String, Map<String, Boolean>>>(
            mapOf("test.plugin" to mapOf("unlimited_ocr" to true))
        )
        every { pluginManager.pluginLocksState } returns locksFlow
        every { pluginManager.loadPluginSettings("test.plugin") } returns PluginSettingsStore()

        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = testPluginInfo,
            capability = lockedCapability,
            inputs = emptyList(),
            outputs = emptyList()
        )
        val flow = Flow("TestFlow", nodes = listOf(node))

        val result = tracker.validateFlowForExecution(flow)
        assertTrue(result.isSuccess, "Flow should pass validation when capability lock is satisfied")
    }
}
