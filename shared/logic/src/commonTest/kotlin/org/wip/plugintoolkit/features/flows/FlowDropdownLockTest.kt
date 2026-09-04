package org.wip.plugintoolkit.features.flows

import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowDropdownLockTest {

    private val testPluginInfo = PluginInfo(
        id = "test.plugin",
        name = "Test Plugin",
        version = "1.0.0",
        description = "Test"
    )

    private val enumType = DataType.Enum(
        className = "TestMode",
        options = listOf("Free", "Pro", "Custom"),
        optionRequirements = mapOf("Custom" to listOf("custom_api_key")),
        optionLockRequirements = mapOf("Pro" to listOf("pro_feature_lock"))
    )

    private val testCapability = Capability(
        name = "TestCapability",
        description = "Testing enum locks",
        parameters = mapOf(
            "mode" to ParameterMetadata(
                description = "Mode selection",
                type = enumType,
                defaultValue = JsonPrimitive("Free"),
                required = true
            )
        ),
        returnType = DataType.Primitive(PrimitiveType.STRING)
    )

    @Test
    fun testCapabilityNodeNotReadyWhenOptionLockNotSatisfied() {
        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = testPluginInfo,
            capability = testCapability,
            inputs = listOf(
                InputPort(
                    id = "mode",
                    name = "Mode",
                    dataType = enumType,
                    value = JsonPrimitive("Pro"),
                    isRequired = true
                )
            ),
            outputs = emptyList()
        )

        // 1. Without lock: node must NOT be ready
        val notReadyWithoutLock = node.isReady(
            connections = emptyList(),
            settings = emptyMap(),
            locks = mapOf("pro_feature_lock" to false)
        )
        assertFalse(notReadyWithoutLock, "Node should not be ready when selected option's lock is unsatisfied")

        // 2. With lock satisfied: node must be ready
        val readyWithLock = node.isReady(
            connections = emptyList(),
            settings = emptyMap(),
            locks = mapOf("pro_feature_lock" to true)
        )
        assertTrue(readyWithLock, "Node should be ready when selected option's lock is satisfied")

        // 3. Free mode (no lock needed): node must be ready
        val freeNode = node.copyWithUpdatedInput("mode", JsonPrimitive("Free"))
        val freeReady = freeNode.isReady(
            connections = emptyList(),
            settings = emptyMap(),
            locks = emptyMap()
        )
        assertTrue(freeReady, "Node should be ready when free option is selected")
    }

    @Test
    fun testCapabilityNodeNotReadyWhenOptionSettingNotSatisfied() {
        val node = Node.CapabilityNode(
            id = 2L,
            position = Offset.Zero,
            pluginInfo = testPluginInfo,
            capability = testCapability,
            inputs = listOf(
                InputPort(
                    id = "mode",
                    name = "Mode",
                    dataType = enumType,
                    value = JsonPrimitive("Custom"),
                    isRequired = true
                )
            ),
            outputs = emptyList()
        )

        // 1. Without custom_api_key setting
        val notReadyWithoutSetting = node.isReady(
            connections = emptyList(),
            settings = emptyMap(),
            locks = emptyMap()
        )
        assertFalse(notReadyWithoutSetting, "Node should not be ready when required setting is missing")

        // 2. With blank custom_api_key setting
        val notReadyWithBlankSetting = node.isReady(
            connections = emptyList(),
            settings = mapOf("custom_api_key" to JsonPrimitive("")),
            locks = emptyMap()
        )
        assertFalse(notReadyWithBlankSetting, "Node should not be ready when required setting is blank")

        // 3. With valid custom_api_key setting
        val readyWithSetting = node.isReady(
            connections = emptyList(),
            settings = mapOf("custom_api_key" to JsonPrimitive("secret-123")),
            locks = emptyMap()
        )
        assertTrue(readyWithSetting, "Node should be ready when required setting is provided")
    }

    @Test
    fun testFlowIsBrokenWhenNodeOptionLockNotSatisfied() {
        val node = Node.CapabilityNode(
            id = 3L,
            position = Offset.Zero,
            pluginInfo = testPluginInfo,
            capability = testCapability,
            inputs = listOf(
                InputPort(
                    id = "mode",
                    name = "Mode",
                    dataType = enumType,
                    value = JsonPrimitive("Pro"),
                    isRequired = true
                )
            ),
            outputs = emptyList()
        )

        val flow = Flow(name = "TestFlow", nodes = listOf(node))
        val activeCaps = setOf("TestCapability")

        val settingsMap = mapOf(3L to emptyMap<String, kotlinx.serialization.json.JsonElement>())
        val unsatisfiedLocksMap = mapOf(3L to mapOf("pro_feature_lock" to false))
        val satisfiedLocksMap = mapOf(3L to mapOf("pro_feature_lock" to true))

        assertTrue(
            flow.isBroken(activeCaps, settingsMap, unsatisfiedLocksMap),
            "Flow should be broken when node's selected enum option lock is unsatisfied"
        )

        assertFalse(
            flow.isBroken(activeCaps, settingsMap, satisfiedLocksMap),
            "Flow should not be broken when node's selected enum option lock is satisfied"
        )
    }

    @Test
    fun testSettingsUtilsValidateCapabilityOptionLocksAndSettings() {
        // 1. Pro mode with unsatisfied lock
        val errorProUnsatisfied = SettingsUtils.validateCapabilityLocksAndSettings(
            capability = testCapability,
            providedLocks = mapOf("pro_feature_lock" to false),
            providedSettings = emptyMap(),
            parameterValues = mapOf("mode" to "Pro")
        )
        assertNotNull(errorProUnsatisfied)
        assertEquals("Option 'Pro' requires lock: pro_feature_lock", errorProUnsatisfied)

        // 2. Pro mode with satisfied lock
        val errorProSatisfied = SettingsUtils.validateCapabilityLocksAndSettings(
            capability = testCapability,
            providedLocks = mapOf("pro_feature_lock" to true),
            providedSettings = emptyMap(),
            parameterValues = mapOf("mode" to "Pro")
        )
        assertNull(errorProSatisfied)

        // 3. Custom mode with missing setting
        val errorCustomMissing = SettingsUtils.validateCapabilityLocksAndSettings(
            capability = testCapability,
            providedLocks = emptyMap(),
            providedSettings = emptyMap(),
            parameterValues = mapOf("mode" to "Custom")
        )
        assertNotNull(errorCustomMissing)
        assertEquals("Option 'Custom' requires setting: custom_api_key", errorCustomMissing)

        // 4. Custom mode with provided setting
        val errorCustomProvided = SettingsUtils.validateCapabilityLocksAndSettings(
            capability = testCapability,
            providedLocks = emptyMap(),
            providedSettings = mapOf("custom_api_key" to JsonPrimitive("key123")),
            parameterValues = mapOf("mode" to "Custom")
        )
        assertNull(errorCustomProvided)
    }

    @Test
    fun testSettingsUtilsGetCapabilityTargetSettingKey() {
        val targetKeyPro = SettingsUtils.getCapabilityTargetSettingKey(
            capability = testCapability,
            providedLocks = mapOf("pro_feature_lock" to false),
            providedSettings = emptyMap(),
            parameterValues = mapOf("mode" to "Pro")
        )
        assertEquals("pro_feature_lock", targetKeyPro)

        val targetKeyCustom = SettingsUtils.getCapabilityTargetSettingKey(
            capability = testCapability,
            providedLocks = emptyMap(),
            providedSettings = emptyMap(),
            parameterValues = mapOf("mode" to "Custom")
        )
        assertEquals("custom_api_key", targetKeyCustom)
    }
}
