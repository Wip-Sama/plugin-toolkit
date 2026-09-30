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
        val targetTypeInput = context.getInputValue("target_type", "AUTO")?.toString()?.uppercase() ?: "AUTO"
        val targetType = when (targetTypeInput) {
            "STRING" -> DataType.Primitive(PrimitiveType.STRING)
            "INT" -> DataType.Primitive(PrimitiveType.INT)
            "DOUBLE" -> DataType.Primitive(PrimitiveType.DOUBLE)
            "BOOLEAN" -> DataType.Primitive(PrimitiveType.BOOLEAN)
            "LONG" -> DataType.Primitive(PrimitiveType.LONG)
            "FLOAT" -> DataType.Primitive(PrimitiveType.FLOAT)
            else -> context.runtimeInferredTypes[Pair(context.node.id, "output_data")]
                ?: DataType.Primitive(PrimitiveType.ANY)
        }
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
 * Executor for the "string_merger" system node.
 * Merges a collection of strings into a single string with an optional separator, prefix, and postfix.
 */
class StringMergerNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val input = context.getInputValue("strings", null)
            ?: context.getInputValue("input_data", null)
            ?: context.getInputValue("collection", null)
        val separator = context.getInputValue("separator", "")?.toString() ?: ""
        val prefix = context.getInputValue("prefix", "")?.toString() ?: ""
        val postfix = context.getInputValue("postfix", "")?.toString() ?: ""

        val items = mutableListOf<String>()

        fun extractStrings(item: Any?) {
            when (item) {
                is Iterable<*> -> item.forEach { extractStrings(it) }
                is Array<*> -> item.forEach { extractStrings(it) }
                is JsonArray -> {
                    item.forEach { je ->
                        val unwrapped = when (je) {
                            is JsonPrimitive -> je.content
                            else -> je.toString()
                        }
                        items.add(unwrapped)
                    }
                }
                null -> {}
                else -> items.add(item.toString())
            }
        }

        extractStrings(input)

        val merged = items.joinToString(separator = separator, prefix = prefix, postfix = postfix)
        context.setOutputValue("output", merged)
        context.setOutputValue("output_data", merged)
        context.addLog("String merger produced string of length ${merged.length} from ${items.size} items")
    }
}

/**
 * Executor for the "merger" system node.
 * Merges two lists or collections into a single combined list.
 */
class MergerNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val list1 = context.getInputValue("list1", null)
        val list2 = context.getInputValue("list2", null)

        val merged = mutableListOf<Any?>()

        fun addToList(item: Any?) {
            when (item) {
                is Iterable<*> -> item.forEach { merged.add(it) }
                is Array<*> -> item.forEach { merged.add(it) }
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

/**
 * Safely converts an input object (Collection, Array, JsonArray, or single object)
 * into a List<Any?> without flattening nested sublists.
 */
internal fun extractAnyList(input: Any?): List<Any?> {
    return when (input) {
        is List<*> -> input
        is Collection<*> -> input.toList()
        is Iterable<*> -> input.toList()
        is Array<*> -> input.toList()
        is JsonArray -> input.map { je ->
            when (je) {
                is JsonPrimitive -> {
                    if (je.isString) je.content
                    else je.booleanOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                }
                else -> je
            }
        }
        null -> emptyList()
        else -> listOf(input)
    }
}

/**
 * Slices a list using Python-like slice syntax (e.g. "x:y:z", "x:y", ":y", "x:", "::-1", "[-1]", "-1").
 * When a single index without a colon is provided, returns a single-element list if the element exists,
 * or an empty list if the index is out of bounds.
 */
internal fun <T> sliceList(list: List<T>, rawPattern: String): List<T> {
    val pattern = rawPattern.trim().removePrefix("[").removeSuffix("]").trim()
    if (pattern.isEmpty() || pattern == ":") {
        return list
    }
    val size = list.size
    if (size == 0) return emptyList()

    // Single index (no colon in pattern)
    if (!pattern.contains(':')) {
        val index = pattern.toIntOrNull() ?: return emptyList()
        val actualIndex = if (index < 0) size + index else index
        return if (actualIndex in 0 until size) {
            listOf(list[actualIndex])
        } else {
            emptyList()
        }
    }

    val parts = pattern.split(':')
    if (parts.size > 3) return emptyList()

    val stepStr = if (parts.size >= 3) parts[2].trim() else ""
    val step = if (stepStr.isNotEmpty()) stepStr.toIntOrNull() ?: 1 else 1
    if (step == 0) return emptyList()

    val startStr = parts.getOrNull(0)?.trim().orEmpty()
    val stopStr = parts.getOrNull(1)?.trim().orEmpty()

    val rawStart = if (startStr.isNotEmpty()) startStr.toIntOrNull() else null
    val rawStop = if (stopStr.isNotEmpty()) stopStr.toIntOrNull() else null

    val result = mutableListOf<T>()

    if (step > 0) {
        val start = when {
            rawStart == null -> 0
            rawStart < 0 -> (size + rawStart).coerceAtLeast(0)
            else -> rawStart.coerceAtMost(size)
        }
        val stop = when {
            rawStop == null -> size
            rawStop < 0 -> (size + rawStop).coerceAtLeast(0)
            else -> rawStop.coerceAtMost(size)
        }
        var i = start
        while (i < stop) {
            result.add(list[i])
            i += step
        }
    } else {
        // step < 0
        val start = when {
            rawStart == null -> size - 1
            rawStart < 0 -> size + rawStart
            else -> rawStart.coerceAtMost(size - 1)
        }
        val stop = when {
            rawStop == null -> -1
            rawStop < 0 -> size + rawStop
            else -> rawStop
        }
        var i = start
        while (i > stop && i >= 0 && i < size) {
            result.add(list[i])
            i += step
        }
    }

    return result
}

/**
 * Executor for the "extract_from_string" system node.
 * Extracts regex matches from an input string, with support for capture groups.
 */
class ExtractFromStringNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val text = (context.getInputValue("string", null)
            ?: context.getInputValue("text", null)
            ?: context.getInputValue("input_data", ""))?.toString() ?: ""
        val pattern = (context.getInputValue("regex", null)
            ?: context.getInputValue("pattern", ""))?.toString() ?: ""

        val groupIndexInput = context.getInputValue("group_index", null)
        val groupIndex = when (groupIndexInput) {
            is Number -> groupIndexInput.toInt()
            is String -> groupIndexInput.toIntOrNull()
            is JsonPrimitive -> groupIndexInput.intOrNull
            else -> null
        }

        if (pattern.isEmpty() || text.isEmpty()) {
            context.setOutputValue("output", emptyList<String>())
            context.setOutputValue("matches", emptyList<String>())
            return
        }

        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            context.addLog("Invalid regex pattern: ${e.message}", "ERROR")
            context.setOutputValue("output", emptyList<String>())
            context.setOutputValue("matches", emptyList<String>())
            return
        }

        val matches = regex.findAll(text).toList()
        val results = mutableListOf<String>()

        for (match in matches) {
            val extracted = if (groupIndex != null) {
                if (groupIndex in 0 until match.groups.size) {
                    match.groups[groupIndex]?.value
                } else null
            } else {
                if (match.groups.size > 1) {
                    match.groupValues.getOrNull(1)
                } else {
                    match.value
                }
            }
            if (extracted != null) {
                results.add(extracted)
            }
        }

        context.setOutputValue("output", results)
        context.setOutputValue("matches", results)
        context.addLog("Extract from string produced ${results.size} matches")
    }
}

/**
 * Executor for the "list_filter" system node.
 * Filters or slices a list using Python-like slice syntax (e.g. x:y:z, index, or intervals).
 */
class ListFilterNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val input = context.getInputValue("items", null)
            ?: context.getInputValue("input_data", null)
            ?: context.getInputValue("list", null)
            ?: context.getInputValue("collection", null)
        val pattern = context.getInputValue("pattern", ":")?.toString() ?: ":"

        val items = extractAnyList(input)
        val filtered = sliceList(items, pattern)

        context.setOutputValue("output", filtered)
        context.setOutputValue("items", filtered)
        context.addLog("List filter with pattern '$pattern' produced ${filtered.size} items from ${items.size} input items")
    }
}

/**
 * Executor for the "list_check" system node.
 * Checks whether the length of a list is within specified min and max bounds.
 * Outputs the list (or empty list if check fails) and a boolean result (true if passed, false if failed).
 */
class ListCheckNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val input = context.getInputValue("items", null)
            ?: context.getInputValue("input_data", null)
            ?: context.getInputValue("list", null)
            ?: context.getInputValue("collection", null)

        val minLengthInput = context.getInputValue("min_length", null)
            ?: context.getInputValue("min", null)
        val maxLengthInput = context.getInputValue("max_length", null)
            ?: context.getInputValue("max", null)

        val minLength = when (minLengthInput) {
            is Number -> minLengthInput.toInt()
            is String -> minLengthInput.toIntOrNull()
            is JsonPrimitive -> minLengthInput.intOrNull
            else -> null
        }

        val maxLength = when (maxLengthInput) {
            is Number -> maxLengthInput.toInt()
            is String -> maxLengthInput.toIntOrNull()
            is JsonPrimitive -> maxLengthInput.intOrNull
            else -> null
        }

        val items = extractAnyList(input)
        val size = items.size

        var passes = true
        if (minLength != null && size < minLength) {
            passes = false
        }
        if (maxLength != null && maxLength >= 0 && size > maxLength) {
            passes = false
        }

        val output = if (passes) items else emptyList<Any?>()
        context.setOutputValue("result", passes)
        context.setOutputValue("output", output)
        context.setOutputValue("items", output)
        context.addLog("List check (size=$size, min=$minLength, max=$maxLength) result: $passes")
    }
}

