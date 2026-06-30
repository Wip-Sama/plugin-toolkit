package org.wip.plugintoolkit.features.flows.logic

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.api.isSemanticTypeCompatible
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError

data class TypeInferenceResult(
    val inferredTypes: Map<Pair<Long, String>, DataType>,
    val inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>>,
    val validationErrors: List<ValidationError>
)

object FlowTypeInference {

    fun runTypeInference(flow: Flow): TypeInferenceResult {
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        val inferredSemantic = mutableMapOf<Pair<Long, String>, List<SemanticType>>()

        // Initialize with declared types and semantic types
        flow.nodes.forEach { node ->
            node.inputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
            node.outputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
        }

        // Fixed-point iteration to propagate types
        var changed = true
        var iteration = 0
        val maxIterations = 10

        while (changed && iteration < maxIterations) {
            changed = false
            iteration++

            flow.connections.forEach { conn ->
                val srcKey = Pair(conn.sourceNodeId, conn.sourcePortId)
                val tgtKey = Pair(conn.targetNodeId, conn.targetPortId)

                val srcType = inferred[srcKey]
                val tgtType = inferred[tgtKey]

                if (srcType != null && tgtType != null) {
                    // Propagate specific types backwards to wildcard ANY sources
                    if (srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY &&
                        !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[srcKey] = tgtType
                        changed = true
                    }
                    // Propagate specific types forwards to wildcard ANY targets
                    if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                        !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[tgtKey] = srcType
                        changed = true
                    }
                }

                // Propagate semantic types
                val srcSemantic = inferredSemantic[srcKey].orEmpty()
                val tgtSemantic = inferredSemantic[tgtKey].orEmpty()
                if (srcSemantic.isNotEmpty() && tgtSemantic.isEmpty()) {
                    inferredSemantic[tgtKey] = srcSemantic
                    changed = true
                } else if (srcSemantic.isEmpty() && tgtSemantic.isNotEmpty()) {
                    inferredSemantic[srcKey] = tgtSemantic
                    changed = true
                }
            }

            // Custom propagation for special system nodes
            flow.nodes.forEach { node ->
                if (node is Node.SystemNode) {
                    if (SystemNodesRegistry.propagateTypes(node, inferred)) {
                        changed = true
                    }
                    if (SystemNodesRegistry.propagateSemanticTypes(node, inferredSemantic)) {
                        changed = true
                    }
                }
            }
        }

        // Now compute validation errors using inferred types and semantic types!
        val errors = mutableListOf<ValidationError>()
        flow.connections.forEach { conn ->
            val srcType = inferred[Pair(conn.sourceNodeId, conn.sourcePortId)]
            val tgtType = inferred[Pair(conn.targetNodeId, conn.targetPortId)]
            val srcSemantic = inferredSemantic[Pair(conn.sourceNodeId, conn.sourcePortId)].orEmpty()
            val tgtSemantic = inferredSemantic[Pair(conn.targetNodeId, conn.targetPortId)].orEmpty()

            if (srcType != null && tgtType != null) {
                // If they are not compatible, generate an error!
                if (!(srcType.isCompatibleWith(tgtType) || srcType.canConvert(tgtType))) {
                    errors.add(
                        ValidationError(
                            sourceNodeId = conn.sourceNodeId,
                            sourcePortId = conn.sourcePortId,
                            targetNodeId = conn.targetNodeId,
                            targetPortId = conn.targetPortId,
                            message = "Type mismatch: ${srcType.format()} is not compatible with ${tgtType.format()}"
                        )
                    )
                } else if (org.wip.plugintoolkit.api.checkSemanticCompatibility(srcSemantic, tgtSemantic) is org.wip.plugintoolkit.api.CompatibilityResult.Incompatible) {
                    errors.add(
                        ValidationError(
                            sourceNodeId = conn.sourceNodeId,
                            sourcePortId = conn.sourcePortId,
                            targetNodeId = conn.targetNodeId,
                            targetPortId = conn.targetPortId,
                            message = "Semantic type mismatch: '${srcSemantic.joinToString { it.canonicalId }}' is not compatible with '${tgtSemantic.joinToString { it.canonicalId }}'"
                        )
                    )
                }
            }
        }

        // Check input ports regex validation
        flow.nodes.forEach { node ->
            node.inputs.forEach { input ->
                val regexStr = input.constraints?.regex
                if (!regexStr.isNullOrEmpty()) {
                    val rawValue = input.value ?: input.defaultValue
                    val strValue = when (rawValue) {
                        is kotlinx.serialization.json.JsonPrimitive -> {
                            if (rawValue.isString) rawValue.content else rawValue.toString()
                        }

                        null -> null
                        else -> rawValue.toString()
                    }
                    if (!strValue.isNullOrEmpty()) {
                        try {
                            val regex = Regex(regexStr)
                            val isArray = input.dataType is DataType.Array
                            val items = if (isArray) {
                                strValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            } else {
                                listOf(strValue)
                            }
                            for (item in items) {
                                if (!regex.matches(item)) {
                                    errors.add(
                                        ValidationError(
                                            sourceNodeId = node.id,
                                            sourcePortId = input.id,
                                            targetNodeId = node.id,
                                            targetPortId = input.id,
                                            message = if (isArray) "Item '$item' does not match pattern '$regexStr'" else "Value does not match pattern '$regexStr'"
                                        )
                                    )
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            errors.add(
                                ValidationError(
                                    sourceNodeId = node.id,
                                    sourcePortId = input.id,
                                    targetNodeId = node.id,
                                    targetPortId = input.id,
                                    message = "Invalid regex pattern: ${e.message}"
                                )
                            )
                        }
                    }
                }
            }
        }

        return TypeInferenceResult(inferred, inferredSemantic, errors)
    }

    fun getSubflowPorts(targetFlow: Flow): Pair<List<InputPort>, List<OutputPort>> {
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        val inferredSemantic = mutableMapOf<Pair<Long, String>, List<SemanticType>>()
        targetFlow.nodes.forEach { node ->
            node.inputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
            node.outputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
        }
        var changed = true
        var iteration = 0
        while (changed && iteration < 10) {
            changed = false
            iteration++
            targetFlow.connections.forEach { conn ->
                val srcKey = Pair(conn.sourceNodeId, conn.sourcePortId)
                val tgtKey = Pair(conn.targetNodeId, conn.targetPortId)
                val srcType = inferred[srcKey]
                val tgtType = inferred[tgtKey]
                if (srcType != null && tgtType != null) {
                    if (srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY &&
                        !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[srcKey] = tgtType
                        changed = true
                    }
                    if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                        !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[tgtKey] = srcType
                        changed = true
                    }
                }

                val srcSemantic = inferredSemantic[srcKey].orEmpty()
                val tgtSemantic = inferredSemantic[tgtKey].orEmpty()
                if (srcSemantic.isNotEmpty() && tgtSemantic.isEmpty()) {
                    inferredSemantic[tgtKey] = srcSemantic
                    changed = true
                } else if (srcSemantic.isEmpty() && tgtSemantic.isNotEmpty()) {
                    inferredSemantic[srcKey] = tgtSemantic
                    changed = true
                }
            }

            targetFlow.nodes.forEach { node ->
                if (node is Node.SystemNode) {
                    if (SystemNodesRegistry.propagateTypes(node, inferred)) {
                        changed = true
                    }
                    if (SystemNodesRegistry.propagateSemanticTypes(node, inferredSemantic)) {
                        changed = true
                    }
                }
            }
        }

        val inputs = targetFlow.nodes.filterIsInstance<Node.FlowInputNode>().map { inputNode ->
            val port = inputNode.outputs.firstOrNull()
            val inferredType =
                inferred[Pair(inputNode.id, port?.id ?: "")] ?: port?.dataType ?: DataType.Primitive(PrimitiveType.ANY)
            val inferredSem = inferredSemantic[Pair(inputNode.id, port?.id ?: "")] ?: port?.semanticTypes ?: emptyList()
            InputPort(
                id = "input_${inputNode.id}",
                name = port?.name ?: "Input Data",
                dataType = inferredType,
                semanticTypes = inferredSem
            )
        }

        val outputs = targetFlow.nodes.filterIsInstance<Node.FlowOutputNode>().map { outputNode ->
            val port = outputNode.inputs.firstOrNull()
            val inferredType =
                inferred[Pair(outputNode.id, port?.id ?: "")] ?: port?.dataType ?: DataType.Primitive(PrimitiveType.ANY)
            val inferredSem =
                inferredSemantic[Pair(outputNode.id, port?.id ?: "")] ?: port?.semanticTypes ?: emptyList()
            OutputPort(
                id = "output_${outputNode.id}",
                name = port?.name ?: "Output Data",
                dataType = inferredType,
                semanticTypes = inferredSem
            )
        }

        return Pair(inputs, outputs)
    }


}

fun DataType.format(): String {
    return when (this) {
        is DataType.Primitive -> this.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
        is DataType.Array -> "List<${this.items.format()}>"
        is DataType.MapType -> "Map<String, ${this.valueType.format()}>"
        is DataType.Enum -> this.className.substringAfterLast('.')
        is DataType.Object -> this.className.substringAfterLast('.')
    }
}
