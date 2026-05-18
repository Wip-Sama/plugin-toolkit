package org.wip.plugintoolkit.features.flows.viewmodel

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.Node

object SystemNodesRegistry {
    fun getInputs(action: String): List<InputPort> {
        val actionLower = action.lowercase()
        return when (actionLower) {
            "save" -> listOf(
                InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("file_path", "File Path", DataType.Primitive(PrimitiveType.STRING), defaultValue = "output.txt")
            )
            "load" -> listOf(
                InputPort("file_path", "File Path", DataType.Primitive(PrimitiveType.STRING), defaultValue = "output.txt")
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
                InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY))
            )
            "merger" -> listOf(
                InputPort("list1", "List 1", DataType.Array(DataType.Primitive(PrimitiveType.ANY))),
                InputPort("list2", "List 2", DataType.Array(DataType.Primitive(PrimitiveType.ANY)))
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
                OutputPort("output_data", "Output", DataType.Primitive(PrimitiveType.ANY))
            )
            "merger" -> listOf(
                OutputPort("output", "Output", DataType.Array(DataType.Primitive(PrimitiveType.ANY)))
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
        }
        return changed
    }
}
