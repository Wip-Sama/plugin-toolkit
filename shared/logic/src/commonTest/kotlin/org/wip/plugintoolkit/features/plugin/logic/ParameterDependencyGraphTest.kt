package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ConditionGroup
import org.wip.plugintoolkit.api.ConditionOperator
import org.wip.plugintoolkit.api.ConditionSource
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterCondition
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PrimitiveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParameterDependencyGraphTest {

    @Test
    fun testTopologicalOrder() {
        val paramA = ParameterMetadata(
            description = "Model selector",
            type = DataType.Primitive(PrimitiveType.STRING)
        )
        val paramB = ParameterMetadata(
            description = "Refiner checkpoint",
            type = DataType.Primitive(PrimitiveType.STRING),
            condition = ConditionGroup.of(
                ParameterCondition(
                    source = ConditionSource.PARAMETER,
                    target = "paramA",
                    operator = ConditionOperator.EQUALS,
                    value = "SDXL"
                )
            )
        )
        val paramC = ParameterMetadata(
            description = "Refiner steps",
            type = DataType.Primitive(PrimitiveType.INT),
            condition = ConditionGroup.of(
                ParameterCondition(
                    source = ConditionSource.PARAMETER,
                    target = "paramB",
                    operator = ConditionOperator.IS_NOT_BLANK
                )
            )
        )

        val params = mapOf(
            "paramC" to paramC,
            "paramA" to paramA,
            "paramB" to paramB
        )

        val order = ParameterDependencyGraph.getTopologicalOrder(params)
        assertTrue(order.indexOf("paramA") < order.indexOf("paramB"))
        assertTrue(order.indexOf("paramB") < order.indexOf("paramC"))
    }

    @Test
    fun testComputeActiveParameters() {
        val paramModel = ParameterMetadata(
            description = "Model",
            type = DataType.Primitive(PrimitiveType.STRING)
        )
        val paramRefiner = ParameterMetadata(
            description = "Refiner",
            type = DataType.Primitive(PrimitiveType.STRING),
            condition = ConditionGroup.of(
                ParameterCondition(
                    source = ConditionSource.PARAMETER,
                    target = "model",
                    operator = ConditionOperator.EQUALS,
                    value = "SDXL"
                )
            )
        )
        val paramClipSkip = ParameterMetadata(
            description = "Clip Skip",
            type = DataType.Primitive(PrimitiveType.INT),
            condition = ConditionGroup.of(
                ParameterCondition(
                    source = ConditionSource.PARAMETER,
                    target = "model",
                    operator = ConditionOperator.EQUALS,
                    value = "SD15"
                )
            )
        )

        val params = mapOf(
            "model" to paramModel,
            "refiner" to paramRefiner,
            "clipSkip" to paramClipSkip
        )

        // When model is SDXL
        val activeSdxl = ParameterDependencyGraph.computeActiveParameters(
            parameters = params,
            values = mapOf("model" to JsonPrimitive("SDXL"))
        )
        assertTrue("model" in activeSdxl)
        assertTrue("refiner" in activeSdxl)
        assertFalse("clipSkip" in activeSdxl)

        // When model is SD15
        val activeSd15 = ParameterDependencyGraph.computeActiveParameters(
            parameters = params,
            values = mapOf("model" to JsonPrimitive("SD15"))
        )
        assertTrue("model" in activeSd15)
        assertFalse("refiner" in activeSd15)
        assertTrue("clipSkip" in activeSd15)
    }
}
