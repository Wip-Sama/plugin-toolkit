package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import org.wip.plugintoolkit.api.ConditionGroup
import org.wip.plugintoolkit.api.ConditionOperator
import org.wip.plugintoolkit.api.ConditionSource
import org.wip.plugintoolkit.api.ParameterCondition
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapabilityValidationTest {

    private class FakeKspLogger : KSPLogger {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        override fun error(message: String, symbol: KSNode?) { errors.add(message) }
        override fun warn(message: String, symbol: KSNode?) { warnings.add(message) }
        override fun info(message: String, symbol: KSNode?) {}
        override fun logging(message: String, symbol: KSNode?) {}
        override fun exception(e: Throwable) { errors.add(e.message ?: "Exception") }
    }

    private val dummyNode = Proxy.newProxyInstance(
        KSNode::class.java.classLoader,
        arrayOf(KSNode::class.java)
    ) { _, _, _ -> null } as KSNode

    @Test
    fun testCycleDetection() {
        val logger = FakeKspLogger()
        val parameters = mapOf(
            "paramA" to ConditionGroup.of(
                ParameterCondition(source = ConditionSource.PARAMETER, target = "paramB", operator = ConditionOperator.EQUALS, value = "true")
            ),
            "paramB" to ConditionGroup.of(
                ParameterCondition(source = ConditionSource.PARAMETER, target = "paramA", operator = ConditionOperator.EQUALS, value = "true")
            )
        )

        GeneratorUtils.validateCapabilityConditions(
            capabilityName = "testCap",
            parameters = parameters,
            availableSettings = emptySet(),
            logger = logger,
            originNode = dummyNode
        )

        assertTrue(logger.errors.any { it.contains("Circular parameter dependency detected") })
    }

    @Test
    fun testSelfDependency() {
        val logger = FakeKspLogger()
        val parameters = mapOf(
            "paramA" to ConditionGroup.of(
                ParameterCondition(source = ConditionSource.PARAMETER, target = "paramA", operator = ConditionOperator.EQUALS, value = "true")
            )
        )

        GeneratorUtils.validateCapabilityConditions(
            capabilityName = "testCap",
            parameters = parameters,
            availableSettings = emptySet(),
            logger = logger,
            originNode = dummyNode
        )

        assertTrue(logger.errors.any { it.contains("cannot depend on itself") })
    }

    @Test
    fun testUnknownParameterDependency() {
        val logger = FakeKspLogger()
        val parameters = mapOf(
            "paramA" to ConditionGroup.of(
                ParameterCondition(source = ConditionSource.PARAMETER, target = "nonExistent", operator = ConditionOperator.EQUALS, value = "true")
            )
        )

        GeneratorUtils.validateCapabilityConditions(
            capabilityName = "testCap",
            parameters = parameters,
            availableSettings = emptySet(),
            logger = logger,
            originNode = dummyNode
        )

        assertTrue(logger.errors.any { it.contains("depends on unknown parameter 'nonExistent'") })
    }

    @Test
    fun testValidDependenciesAndDotNotation() {
        val logger = FakeKspLogger()
        val parameters = mapOf(
            "config" to null,
            "apiKey" to ConditionGroup.of(
                ParameterCondition(source = ConditionSource.PARAMETER, target = "config.provider", operator = ConditionOperator.EQUALS, value = "cloud")
            ),
            "endpoint" to ConditionGroup.of(
                ParameterCondition(source = ConditionSource.PARAMETER, target = "apiKey", operator = ConditionOperator.IS_NOT_BLANK)
            )
        )

        GeneratorUtils.validateCapabilityConditions(
            capabilityName = "testCap",
            parameters = parameters,
            availableSettings = setOf("gpuEnabled"),
            logger = logger,
            originNode = dummyNode
        )

        assertEquals(0, logger.errors.size, "Expected no errors for valid dependency chain including dot notation root target: ${logger.errors}")
    }
}
