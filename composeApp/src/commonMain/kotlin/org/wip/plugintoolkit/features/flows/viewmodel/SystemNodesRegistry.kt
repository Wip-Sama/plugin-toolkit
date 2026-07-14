package org.wip.plugintoolkit.features.flows.viewmodel

import kotlinx.serialization.json.booleanOrNull
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticTypes
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort

object SystemNodesRegistry {
    fun getInputs(action: String): List<InputPort> {
        val actionLower = action.lowercase()
        return when (actionLower) {
            "save" -> listOf(
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY)),
                InputPort(
                    "file_path",
                    "File Path",
                    DataType.Primitive(PrimitiveType.STRING),
                    defaultValue = "output.txt"
                )
            )

            "save_file" -> listOf(
                InputPort(
                    "data",
                    "Data (File Path)",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("file")
                ),
                InputPort(
                    "destination_folder",
                    "Destination Folder",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("path")
                ),
                InputPort(
                    "file_name",
                    "File Name (Optional)",
                    DataType.Primitive(PrimitiveType.STRING),
                    defaultValue = "",
                    isRequired = false
                ),
                InputPort(
                    "is_destructive",
                    "Delete Source",
                    DataType.Primitive(PrimitiveType.BOOLEAN),
                    defaultValue = false
                )
            )

            "save_folder" -> listOf(
                InputPort(
                    "data",
                    "Data (Folder Path)",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("folder")
                ),
                InputPort(
                    "destination_folder",
                    "Destination Folder",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("path")
                ),
                InputPort(
                    "folder_name",
                    "Folder Name (Optional)",
                    DataType.Primitive(PrimitiveType.STRING),
                    defaultValue = "",
                    isRequired = false
                ),
                InputPort(
                    "is_destructive",
                    "Delete Source",
                    DataType.Primitive(PrimitiveType.BOOLEAN),
                    defaultValue = false
                )
            )

            "load" -> listOf(
                InputPort(
                    id = "file_path",
                    name = "File Path",
                    dataType = DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("file"),
                    defaultValue = "output.txt",
                    constraints = org.wip.plugintoolkit.features.flows.model.PortConstraints(
                        extensions = listOf("txt", "json", "csv") // Add custom supported extensions here
                    )
                )
            )

            "log" -> listOf(
                InputPort(
                    "level",
                    "Log Level",
                    DataType.Enum("LogLevel", listOf("INFO", "DEBUG", "WARN", "ERROR")),
                    defaultValue = "INFO"
                ),
                InputPort("message", "Message", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY), isRequired = false)
            )

            "delay" -> listOf(
                InputPort("duration", "Duration (ms)", DataType.Primitive(PrimitiveType.INT), defaultValue = 1000),
                InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY))
            )

            "convert" -> listOf(
                InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY)),
                InputPort(
                    "ignore_semantic_type",
                    "Ignore Semantic Type",
                    DataType.Primitive(PrimitiveType.BOOLEAN),
                    defaultValue = false
                )
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
                InputPort(
                    "message",
                    "Error Message",
                    DataType.Primitive(PrimitiveType.STRING),
                    defaultValue = "An error occurred during flow execution"
                ),
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY), isRequired = false)
            )

            "comparator" -> listOf(
                InputPort("a", "Value A", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("b", "Value B", DataType.Primitive(PrimitiveType.ANY))
            )

            "for" -> listOf(
                InputPort(
                    "subflow_name",
                    "Subflow Name",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("flow")
                ),
                InputPort("start", "Start", DataType.Primitive(PrimitiveType.INT), defaultValue = 0),
                InputPort("end", "End", DataType.Primitive(PrimitiveType.INT), defaultValue = 10),
                InputPort("step", "Step", DataType.Primitive(PrimitiveType.INT), defaultValue = 1),
                InputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY))
            )

            "while" -> listOf(
                InputPort(
                    "subflow_name",
                    "Subflow Name",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("flow")
                ),
                InputPort(
                    "condition",
                    "Initial Condition",
                    DataType.Primitive(PrimitiveType.BOOLEAN),
                    defaultValue = true
                ),
                InputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY))
            )

            "create_folder" -> listOf(
                InputPort(
                    "path",
                    "Path",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("path")
                ),
                InputPort("folder_name", "Folder Name", DataType.Primitive(PrimitiveType.STRING))
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

            "save_file" -> listOf(
                OutputPort(
                    "saved_path",
                    "Saved Path",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("file")
                ),
                OutputPort("success", "Success", DataType.Primitive(PrimitiveType.BOOLEAN))
            )

            "save_folder" -> listOf(
                OutputPort(
                    "saved_path",
                    "Saved Path",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("folder")
                ),
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

            "create_folder" -> listOf(
                OutputPort(
                    "created_path",
                    "Path",
                    DataType.Primitive(PrimitiveType.STRING),
                    semanticTypes = parseSemanticTypes("path")
                ),
                OutputPort("success", "Success", DataType.Primitive(PrimitiveType.BOOLEAN))
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
        inferredSemantic: MutableMap<Pair<Long, String>, List<SemanticType>>
    ): Boolean {
        var changed = false
        val action = node.systemAction.lowercase()
        when (action) {
            "delay" -> {
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")].orEmpty()
                val outputSemantic = inferredSemantic[Pair(node.id, "output_data")].orEmpty()
                if (inputSemantic.isNotEmpty() && outputSemantic.isEmpty()) {
                    inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                    changed = true
                } else if (inputSemantic.isEmpty() && outputSemantic.isNotEmpty()) {
                    inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                    changed = true
                }
            }

            "convert" -> {
                val ignoreSemanticPort = node.inputs.find { it.id == "ignore_semantic_type" }
                val ignoreSemantic = when (val ignoreSemanticVal = ignoreSemanticPort?.value) {
                    is Boolean -> ignoreSemanticVal
                    is String -> ignoreSemanticVal.toBoolean()
                    is Number -> ignoreSemanticVal.toInt() != 0
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        ignoreSemanticVal.booleanOrNull ?: ignoreSemanticVal.content.toBoolean()
                    }

                    else -> false
                }
                if (!ignoreSemantic) {
                    val inputSemantic = inferredSemantic[Pair(node.id, "input_data")].orEmpty()
                    val outputSemantic = inferredSemantic[Pair(node.id, "output_data")].orEmpty()
                    if (inputSemantic.isNotEmpty() && outputSemantic.isEmpty()) {
                        inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                        changed = true
                    } else if (inputSemantic.isEmpty() && outputSemantic.isNotEmpty()) {
                        inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                        changed = true
                    }
                }
            }

            "merger" -> {
                val list1Semantic = inferredSemantic[Pair(node.id, "list1")].orEmpty()
                val list2Semantic = inferredSemantic[Pair(node.id, "list2")].orEmpty()
                val outputSemantic = inferredSemantic[Pair(node.id, "output")].orEmpty()

                val specificSemantic = when {
                    list1Semantic.isNotEmpty() -> list1Semantic
                    list2Semantic.isNotEmpty() -> list2Semantic
                    outputSemantic.isNotEmpty() -> outputSemantic
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
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")].orEmpty()
                val trueSemantic = inferredSemantic[Pair(node.id, "if_true")].orEmpty()
                val falseSemantic = inferredSemantic[Pair(node.id, "if_false")].orEmpty()

                val specificSemantic = when {
                    inputSemantic.isNotEmpty() -> inputSemantic
                    trueSemantic.isNotEmpty() -> trueSemantic
                    falseSemantic.isNotEmpty() -> falseSemantic
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
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")].orEmpty()
                val outputSemantic = inferredSemantic[Pair(node.id, "output_data")].orEmpty()
                if (inputSemantic.isNotEmpty() && outputSemantic != inputSemantic) {
                    inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                    changed = true
                } else if (outputSemantic.isNotEmpty() && inputSemantic != outputSemantic) {
                    inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                    changed = true
                }
            }

            "while" -> {
                val inputSemantic = inferredSemantic[Pair(node.id, "input_data")].orEmpty()
                val outputSemantic = inferredSemantic[Pair(node.id, "output_data")].orEmpty()
                if (inputSemantic.isNotEmpty() && outputSemantic != inputSemantic) {
                    inferredSemantic[Pair(node.id, "output_data")] = inputSemantic
                    changed = true
                } else if (outputSemantic.isNotEmpty() && inputSemantic != outputSemantic) {
                    inferredSemantic[Pair(node.id, "input_data")] = outputSemantic
                    changed = true
                }
            }
        }
        return changed
    }
}
