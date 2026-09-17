package org.wip.plugintoolkit.features.flows.ui

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.logic.FlowTypeInference
import org.wip.plugintoolkit.features.flows.logic.SystemNodesRegistry
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.model.SubflowPortMapping

/**
 * Creates a concrete [Node] instance from a [PaletteNode] prototype.
 */
fun PaletteNode.toNode(
    id: Long,
    position: ModelOffset,
    availableFlows: List<Flow> = emptyList()
): Node {
    return when (this) {
        is PaletteNode.Capability -> {
            val capInputs = capability.parameters?.map { (key, meta) ->
                InputPort(
                    id = key,
                    name = key,
                    description = meta.description,
                    dataType = meta.type,
                    semanticTypes = meta.semanticTypes,
                    defaultValue = meta.defaultValue,
                    constraints = meta.constraints?.let {
                        PortConstraints(regex = it.regex)
                    },
                    isAdvanced = meta.isAdvanced,
                    condition = meta.condition
                )
            } ?: emptyList()

            val capOutputs = (capability.outputs?.map { out ->
                OutputPort(
                    id = out.name,
                    name = out.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    description = out.description,
                    dataType = out.type,
                    semanticTypes = out.semanticTypes,
                    isAdvanced = out.isAdvanced,
                    condition = out.condition
                )
            } ?: listOf(
                OutputPort(
                    id = "result",
                    name = "Result",
                    description = "Capability Result",
                    dataType = capability.returnType,
                    semanticTypes = capability.semanticTypes,
                    isAdvanced = false,
                    condition = null
                )
            )) + (capability.parameters?.filter { it.value.role == org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION }
                ?.map { (key, meta) ->
                    OutputPort(
                        id = key,
                        name = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        description = meta.description,
                        dataType = meta.type,
                        semanticTypes = meta.semanticTypes,
                        isAdvanced = meta.isAdvanced,
                        condition = meta.condition
                    )
                } ?: emptyList())

            Node.CapabilityNode(
                id = id,
                position = position,
                pluginInfo = pluginInfo,
                capability = capability,
                inputs = capInputs,
                outputs = capOutputs
            )
        }

        is PaletteNode.System -> {
            val inputs = SystemNodesRegistry.getInputs(action)
            val outputs = SystemNodesRegistry.getOutputs(action)
            Node.SystemNode(
                id = id,
                position = position,
                title = action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                systemAction = action,
                inputs = inputs,
                outputs = outputs
            )
        }

        is PaletteNode.FlowInput -> {
            Node.FlowInputNode(
                id = id,
                position = position,
                outputs = listOf(OutputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY)))
            )
        }

        is PaletteNode.FlowOutput -> {
            Node.FlowOutputNode(
                id = id,
                position = position,
                inputs = listOf(InputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY)))
            )
        }

        is PaletteNode.SubFlow -> {
            val targetFlow = availableFlows.find { it.name == name }
            val (inputs, outputs) = if (targetFlow != null) {
                FlowTypeInference.getSubflowPorts(targetFlow)
            } else {
                Pair(emptyList(), emptyList())
            }

            val inputMappings = targetFlow?.nodes?.filterIsInstance<Node.FlowInputNode>()?.map { inputNode ->
                SubflowPortMapping(
                    portId = "input_${inputNode.id}",
                    boundaryNodeId = inputNode.id
                )
            } ?: emptyList()

            val outputMappings = targetFlow?.nodes?.filterIsInstance<Node.FlowOutputNode>()?.map { outputNode ->
                SubflowPortMapping(
                    portId = "output_${outputNode.id}",
                    boundaryNodeId = outputNode.id
                )
            } ?: emptyList()

            Node.SubFlowNode(
                id = id,
                position = position,
                flowName = name,
                inputs = inputs,
                outputs = outputs,
                inputMappings = inputMappings,
                outputMappings = outputMappings
            )
        }
    }
}
