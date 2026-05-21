package org.wip.plugintoolkit.features.flows.viewmodel

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort

object SystemNodesRegistry {
    fun getInputs(action: String): List<InputPort> {
        val actionLower = action.lowercase()
        return when (actionLower) {
            "save" -> listOf(
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("file_path", "File Path", DataType.Primitive(PrimitiveType.STRING), defaultValue = "output.txt")
            )
            "load" -> listOf(
                InputPort("file_path", "File Path", DataType.Primitive(PrimitiveType.STRING), semanticType = "file", defaultValue = "output.txt")
            )
            "log" -> listOf(
                InputPort("level", "Log Level", DataType.Enum("LogLevel", listOf("INFO", "DEBUG", "WARN", "ERROR")), defaultValue = "INFO"),
                InputPort("message", "Message", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY))
            )
            "delay" -> listOf(
                InputPort("duration", "Duration (ms)", DataType.Primitive(PrimitiveType.INT), defaultValue = 1000),
                InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY))
            )
            "convert" -> listOf(
                InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("ignore_semantic_type", "Ignore Semantic Type", DataType.Primitive(PrimitiveType.BOOLEAN), defaultValue = false)
            )
            "merger" -> listOf(
                InputPort("list1", "List 1", DataType.Array(DataType.Primitive(PrimitiveType.ANY))),
                InputPort("list2", "List 2", DataType.Array(DataType.Primitive(PrimitiveType.ANY)))
            )
            "conditional" -> listOf(
                InputPort("condition", "Condition", DataType.Primitive(PrimitiveType.BOOLEAN), defaultValue = false),
                InputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY))
            )
            "error" -> listOf(
                InputPort("message", "Error Message", DataType.Primitive(PrimitiveType.STRING), defaultValue = "An error occurred during flow execution"),
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY))
            )
            "comparator" -> listOf(
                InputPort("a", "Value A", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("b", "Value B", DataType.Primitive(PrimitiveType.ANY))
            )
            "for" -> listOf(
                InputPort("subflow_name", "Subflow Name", DataType.Primitive(PrimitiveType.STRING), semanticType = "flow"),
                InputPort("start", "Start", DataType.Primitive(PrimitiveType.INT), defaultValue = 0),
                InputPort("end", "End", DataType.Primitive(PrimitiveType.INT), defaultValue = 10),
                InputPort("step", "Step", DataType.Primitive(PrimitiveType.INT), defaultValue = 1),
                InputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY))
            )
            "while" -> listOf(
                InputPort("subflow_name", "Subflow Name", DataType.Primitive(PrimitiveType.STRING), semanticType = "flow"),
                InputPort("condition", "Initial Condition", DataType.Primitive(PrimitiveType.BOOLEAN), defaultValue = true),
                InputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY))
            )
            else -> emptyList()
        }
    }

    fun getOutputs(action: String): List<OutputPort> {
        val actionLower = action.lowercase()
        return when (actionLower) {
            "save" -> listOf(
                OutputPort("success", "Success", DataType.Primitive(PrimitiveType.BOOLEAN))
            )
            "load" -> listOf(
                OutputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY))
            )
            "log" -> listOf(
                OutputPort("output", "Output", DataType.Primitive(PrimitiveType.ANY))
            )
            "delay" -> listOf(
                OutputPort("output_data", "Output", DataType.Primitive(PrimitiveType.ANY))
            )
            "convert" -> listOf(
                OutputPort("output_data", "Output", DataType.Primitive(PrimitiveType.ANY)),
                OutputPort("success", "Success", DataType.Primitive(PrimitiveType.BOOLEAN))
            )
            "merger" -> listOf(
                OutputPort("output", "Output", DataType.Array(DataType.Primitive(PrimitiveType.ANY)))
            )
            "conditional" -> listOf(
                OutputPort("if_true", "If True", DataType.Primitive(PrimitiveType.ANY)),
                OutputPort("if_false", "If False", DataType.Primitive(PrimitiveType.ANY))
            )
            "error" -> emptyList()
            "comparator" -> listOf(
                OutputPort("minor", "Minor (A < B)", DataType.Primitive(PrimitiveType.BOOLEAN)),
                OutputPort("major", "Major (A > B)", DataType.Primitive(PrimitiveType.BOOLEAN)),
                OutputPort("equal", "Equal (A == B)", DataType.Primitive(PrimitiveType.BOOLEAN)),
                OutputPort("not_equal", "Not Equal (A != B)", DataType.Primitive(PrimitiveType.BOOLEAN))
            )
            "for" -> listOf(
                OutputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY))
            )
            "while" -> listOf(
                OutputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY))
            )
            else -> emptyList()
        }
    }

    fun propagateTypes(
        node: Node.SystemNode,
        inferred: MutableMap<Pair<Long, String>, DataType>
    ): Boolean {
        var changed = false
        val action = node.systemAction.lowercase()
        when (action) {
            "delay" -> {
                val inputType = inferred[Pair(node.id, "input_data")]
                if (inputType != null) {
                    val currentOutputType = inferred[Pair(node.id, "output_data")]
                    if (inputType != currentOutputType) {
                        inferred[Pair(node.id, "output_data")] = inputType
                        changed = true
                    }
                }
            }
            "merger" -> {
                val list1Type = inferred[Pair(node.id, "list1")]
                val list2Type = inferred[Pair(node.id, "list2")]
                
                val list1Items = (list1Type as? DataType.Array)?.items
                val list2Items = (list2Type as? DataType.Array)?.items

                val specificArrayType = when {
                    list1Type is DataType.Array && list1Items != null && !(list1Items is DataType.Primitive && list1Items.primitiveType == PrimitiveType.ANY) -> list1Type
                    list2Type is DataType.Array && list2Items != null && !(list2Items is DataType.Primitive && list2Items.primitiveType == PrimitiveType.ANY) -> list2Type
                    list1Type is DataType.Array -> list1Type
                    list2Type is DataType.Array -> list2Type
                    else -> null
                }
                
                if (specificArrayType != null) {
                    val currentOutputType = inferred[Pair(node.id, "output")]
                    if (specificArrayType != currentOutputType) {
                        inferred[Pair(node.id, "output")] = specificArrayType
                        changed = true
                    }
                    if (list1Type is DataType.Array && list1Items is DataType.Primitive && list1Items.primitiveType == PrimitiveType.ANY) {
                        inferred[Pair(node.id, "list1")] = specificArrayType
                        changed = true
                    }
                    if (list2Type is DataType.Array && list2Items is DataType.Primitive && list2Items.primitiveType == PrimitiveType.ANY) {
                        inferred[Pair(node.id, "list2")] = specificArrayType
                        changed = true
                    }
                }
            }
            "conditional" -> {
                val inputType = inferred[Pair(node.id, "input_data")]
                val trueType = inferred[Pair(node.id, "if_true")]
                val falseType = inferred[Pair(node.id, "if_false")]

                val specificType = when {
                    inputType != null && !(inputType is DataType.Primitive && inputType.primitiveType == PrimitiveType.ANY) -> inputType
                    trueType != null && !(trueType is DataType.Primitive && trueType.primitiveType == PrimitiveType.ANY) -> trueType
                    falseType != null && !(falseType is DataType.Primitive && falseType.primitiveType == PrimitiveType.ANY) -> falseType
                    else -> null
                }

                if (specificType != null) {
                    if (inferred[Pair(node.id, "input_data")] != specificType) {
                        inferred[Pair(node.id, "input_data")] = specificType
                        changed = true
                    }
                    if (inferred[Pair(node.id, "if_true")] != specificType) {
                        inferred[Pair(node.id, "if_true")] = specificType
                        changed = true
                    }
                    if (inferred[Pair(node.id, "if_false")] != specificType) {
                        inferred[Pair(node.id, "if_false")] = specificType
                        changed = true
                    }
                }
            }
            "for" -> {
                val inputType = inferred[Pair(node.id, "input_data")]
                val outputType = inferred[Pair(node.id, "output_data")]
                if (inputType != null && inputType != outputType) {
                    inferred[Pair(node.id, "output_data")] = inputType
                    changed = true
                } else if (outputType != null && outputType != inputType) {
                    inferred[Pair(node.id, "input_data")] = outputType
                    changed = true
                }
            }
            "while" -> {
                val inputType = inferred[Pair(node.id, "input_data")]
                val outputType = inferred[Pair(node.id, "output_data")]
                if (inputType != null && inputType != outputType) {
                    inferred[Pair(node.id, "output_data")] = inputType
                    changed = true
                } else if (outputType != null && outputType != inputType) {
                    inferred[Pair(node.id, "input_data")] = outputType
                    changed = true
                }
            }
        }
        return changed
    }

    fun propagateSemanticTypes(
        node: Node.SystemNode,
        inferredSemantic: MutableMap<Pair<Long, String>, String?>
    ): Boolean {
        var changed = false
        val action = node.systemAction.lowercase()
        when (action) {
            "delay" -> {
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")]
                val outputSemantic = inferredSemantic[Pair(node.id, "output_data")]
                if (!inputSemantic.isNullOrBlank() && outputSemantic.isNullOrBlank()) {
                    inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                    changed = true
                } else if (inputSemantic.isNullOrBlank() && !outputSemantic.isNullOrBlank()) {
                    inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                    changed = true
                }
            }
            "convert" -> {
                val ignoreSemanticPort = node.inputs.find { it.id == "ignore_semantic_type" }
                val ignoreSemantic = ignoreSemanticPort?.value as? Boolean ?: false
                if (!ignoreSemantic) {
                    val inputSemantic = inferredSemantic[Pair(node.id, "input_data")]
                    val outputSemantic = inferredSemantic[Pair(node.id, "output_data")]
                    if (!inputSemantic.isNullOrBlank() && outputSemantic.isNullOrBlank()) {
                        inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                        changed = true
                    } else if (inputSemantic.isNullOrBlank() && !outputSemantic.isNullOrBlank()) {
                        inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                        changed = true
                    }
                }
            }
            "merger" -> {
                val list1Semantic = inferredSemantic[Pair(node.id, "list1")]
                val list2Semantic = inferredSemantic[Pair(node.id, "list2")]
                val outputSemantic = inferredSemantic[Pair(node.id, "output")]
                
                val specificSemantic = when {
                    !list1Semantic.isNullOrBlank() -> list1Semantic
                    !list2Semantic.isNullOrBlank() -> list2Semantic
                    !outputSemantic.isNullOrBlank() -> outputSemantic
                    else -> null
                }
                
                if (specificSemantic != null) {
                    if (inferredSemantic[Pair(node.id, "list1")] != specificSemantic) {
                        inferredSemantic[Pair(node.id, "list1")] = specificSemantic
                        changed = true
                    }
                    if (inferredSemantic[Pair(node.id, "list2")] != specificSemantic) {
                        inferredSemantic[Pair(node.id, "list2")] = specificSemantic
                        changed = true
                    }
                    if (inferredSemantic[Pair(node.id, "output")] != specificSemantic) {
                        inferredSemantic[Pair(node.id, "output")] = specificSemantic
                        changed = true
                    }
                }
            }
            "conditional" -> {
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")]
                val trueSemantic = inferredSemantic[Pair(node.id, "if_true")]
                val falseSemantic = inferredSemantic[Pair(node.id, "if_false")]

                val specificSemantic = when {
                    !inputSemantic.isNullOrBlank() -> inputSemantic
                    !trueSemantic.isNullOrBlank() -> trueSemantic
                    !falseSemantic.isNullOrBlank() -> falseSemantic
                    else -> null
                }

                if (specificSemantic != null) {
                    if (inferredSemantic[Pair(node.id, "input_data")] != specificSemantic) {
                        inferredSemantic[Pair(node.id, "input_data")] = specificSemantic
                        changed = true
                    }
                    if (inferredSemantic[Pair(node.id, "if_true")] != specificSemantic) {
                        inferredSemantic[Pair(node.id, "if_true")] = specificSemantic
                        changed = true
                    }
                    if (inferredSemantic[Pair(node.id, "if_false")] != specificSemantic) {
                        inferredSemantic[Pair(node.id, "if_false")] = specificSemantic
                        changed = true
                    }
                }
            }
            "for" -> {
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")]
                val outputSemantic = inferredSemantic[Pair(node.id, "output_data")]
                if (!inputSemantic.isNullOrBlank() && outputSemantic != inputSemantic) {
                    inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                    changed = true
                } else if (!outputSemantic.isNullOrBlank() && inputSemantic != outputSemantic) {
                    inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                    changed = true
                }
            }
            "while" -> {
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")]
                val outputSemantic = inferredSemantic[Pair(node.id, "output_data")]
                if (!inputSemantic.isNullOrBlank() && outputSemantic != inputSemantic) {
                    inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                    changed = true
                } else if (!outputSemantic.isNullOrBlank() && inputSemantic != outputSemantic) {
                    inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                    changed = true
                }
            }
        }
        return changed
    }
}
