package org.wip.plugintoolkit.features.job.logic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType

/**
 * Executor for the "convert" system node.
 * Converts input data to a specified target data type.
 */
class ConvertNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val inputData = context.getInputValue("input_data", null)
        val targetType = context.runtimeInferredTypes[Pair(context.node.id, "output_data")]
            ?: DataType.Primitive(PrimitiveType.ANY)
        try {
            val converted = convertValue(inputData, targetType)
            context.setOutputValue("output_data", converted)
            context.setOutputValue("success", true)
        } catch (e: Exception) {
            context.addLog("Conversion warning: ${e.message}", "WARN")
            context.setOutputValue("output_data", null)
            context.setOutputValue("success", false)
        }
    }
}

/**
 * Executor for the "merger" system node.
 * Merges two lists into a single combined list.
 */
class MergerNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val list1 = context.getInputValue("list1", null)
        val list2 = context.getInputValue("list2", null)

        val merged = mutableListOf<Any?>()

        fun addToList(item: Any?) {
            when (item) {
                is List<*> -> merged.addAll(item)
                is Array<*> -> merged.addAll(item)
                is JsonArray -> {
                    item.forEach { je ->
                        val unwrapped = when (je) {
                            is JsonPrimitive -> {
                                if (je.isString) je.content
                                else je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                            }

                            else -> je
                        }
                        merged.add(unwrapped)
                    }
                }

                null -> {}
                else -> merged.add(item)
            }
        }

        addToList(list1)
        addToList(list2)

        context.setOutputValue("output", merged)
        context.addLog("Merged lists. Item count: ${merged.size}")
    }
}

/**
 * Executor for the "comparator" system node.
 * Compares two values and outputs relational flags (minor, major, equal, not_equal).
 */
class ComparatorNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val a = context.getInputValue("a", null)
        val b = context.getInputValue("b", null)

        var minorVal = false
        var majorVal = false
        var equalVal = false

        if (a != null && b != null) {
            val aNum = when (a) {
                is Number -> a.toDouble()
                else -> a.toString().toDoubleOrNull()
            }
            val bNum = when (b) {
                is Number -> b.toDouble()
                else -> b.toString().toDoubleOrNull()
            }

            if (aNum != null && bNum != null) {
                minorVal = aNum < bNum
                majorVal = aNum > bNum
                equalVal = aNum == bNum
            } else {
                val aStr = a.toString()
                val bStr = b.toString()
                val cmp = aStr.compareTo(bStr)
                minorVal = cmp < 0
                majorVal = cmp > 0
                equalVal = cmp == 0
            }
        } else {
            equalVal = (a == null && b == null)
            minorVal = (a == null && b != null)
            majorVal = (a != null && b == null)
        }

        val notEqualVal = !equalVal
        context.setOutputValue("minor", minorVal)
        context.setOutputValue("major", majorVal)
        context.setOutputValue("equal", equalVal)
        context.setOutputValue("not_equal", notEqualVal)
        context.addLog("Comparator result: minor=$minorVal, major=$majorVal, equal=$equalVal, not_equal=$notEqualVal")
    }
}
