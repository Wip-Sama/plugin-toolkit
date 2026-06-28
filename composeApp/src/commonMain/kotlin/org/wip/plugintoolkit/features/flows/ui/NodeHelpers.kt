package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.booleanOrNull
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Node

@Composable
fun PortCircle(
    color: Color,
    isHighlighted: Boolean = false,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (isShiftPressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var cumulativeOffset by remember { mutableStateOf(Offset.Zero) }
    var isMouseHovered by remember { mutableStateOf(false) }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val displayColor = if (isHighlighted) {
        color
    } else if (isMouseHovered) {
        MaterialTheme.colorScheme.primary
    } else {
        color
    }

    val backgroundDisplayColor = if (isHighlighted) {
        color.copy(alpha = 0.4f)
    } else if (isMouseHovered) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(backgroundDisplayColor)
            .border(if (isHighlighted || isMouseHovered) 3.dp else 2.dp, displayColor, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter) {
                            isMouseHovered = true
                        } else if (event.type == PointerEventType.Exit) {
                            isMouseHovered = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        cumulativeOffset = Offset.Zero
                        currentOnDragStart()

                        var isShift = false

                        drag(down.id) { change ->
                            change.consume()
                            cumulativeOffset += change.position - change.previousPosition
                            currentOnDrag(cumulativeOffset)

                            isShift = currentEvent.keyboardModifiers.isShiftPressed
                        }

                        currentOnDragEnd(isShift)
                    }
                }
            }
    )
}

fun formatDataType(type: DataType): String {
    return when (type) {
        is DataType.Primitive -> type.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
        is DataType.Array -> "List<${formatDataType(type.items)}>"
        is DataType.MapType -> "Map<String, ${formatDataType(type.valueType)}>"
        is DataType.Enum -> type.className.substringAfterLast('.')
        is DataType.Object -> type.className.substringAfterLast('.')
    }
}

fun getPortValueString(value: Any?, type: DataType): String {
    if (value == null) return ""
    val jsonElement = org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils.anyToJsonElement(value)
    return org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.jsonToString(jsonElement, type)
}

fun getBooleanValue(value: Any?): Boolean {
    if (value == null) return false
    if (value is Boolean) return value
    if (value is kotlinx.serialization.json.JsonPrimitive) {
        return value.booleanOrNull ?: (value.content.lowercase().toBooleanStrictOrNull() ?: false)
    }
    return value.toString().lowercase().toBooleanStrictOrNull() ?: false
}

fun getNodeDescription(node: Node): String {
    return when (node) {
        is Node.CapabilityNode -> node.capability.description ?: "Executes the '${node.capability.name}' capability."
        is Node.SystemNode -> {
            when (node.systemAction.lowercase()) {
                "save" -> "Saves data to a file at the specified file path."
                "load" -> "Loads data from a file at the specified file path."
                "log" -> "Logs a message to the console/run logs with the specified severity."
                "delay" -> "Pauses flow execution for a specified duration in milliseconds."
                "convert" -> "Converts inputs safely between primitive types (String, Int, Double, Boolean). Provides a success status."
                "merger" -> "Merges two list arrays into a single combined list."
                "conditional" -> "Routes input data to either the 'If True' or 'If False' output port based on a boolean condition."
                "error" -> "Halts flow execution immediately with a custom error message."
                "comparator" -> "Compares two values (numeric or string) and outputs minor (<), major (>), equal (==), and not equal (!=) boolean results."
                "for" -> "Runs a subflow iteratively for a range of index values, accumulating data."
                "while" -> "Runs a subflow repeatedly while a boolean condition remains true."
                else -> "System operation: ${node.title}."
            }
        }

        is Node.FlowInputNode -> "Defines an input boundary for this flow."
        is Node.FlowOutputNode -> "Defines an output boundary for this flow."
        is Node.SubFlowNode -> "Executes the sub-flow '${node.flowName}' and maps its inputs/outputs."
    }
}

fun parseColorString(colorStr: String): Color {
    val lastColor = colorStr.split(",").lastOrNull { it.trim().isNotEmpty() }?.trim() ?: colorStr
    val trimmed = lastColor.trim()
    if (trimmed.isEmpty()) return Color.Transparent
    if (trimmed.startsWith("#")) {
        return try {
            val hex = trimmed.substring(1)
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }

                4 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    val a = hex[3].toString().repeat(2).toInt(16) / 255f
                    Color(r, g, b, a)
                }

                6 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }

                8 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    val a = hex.substring(6, 8).toInt(16) / 255f
                    Color(r, g, b, a)
                }

                else -> Color.Gray
            }
        } catch (e: Exception) {
            Color.Gray
        }
    }
    if (trimmed.startsWith("rgba", ignoreCase = true)) {
        return try {
            val parts = trimmed.substringAfter("(").substringBefore(")").split(",")
            val r = parts[0].trim().toFloat() / 255f
            val g = parts[1].trim().toFloat() / 255f
            val b = parts[2].trim().toFloat() / 255f
            val a = parts[3].trim().toFloat()
            Color(r, g, b, a)
        } catch (e: Exception) {
            Color.Gray
        }
    }
    if (trimmed.startsWith("rgb", ignoreCase = true)) {
        return try {
            val parts = trimmed.substringAfter("(").substringBefore(")").split(",")
            val r = parts[0].trim().toFloat() / 255f
            val g = parts[1].trim().toFloat() / 255f
            val b = parts[2].trim().toFloat() / 255f
            Color(r, g, b, 1f)
        } catch (e: Exception) {
            Color.Gray
        }
    }
    return when (trimmed.lowercase()) {
        "red" -> Color.Red
        "green" -> Color.Green
        "blue" -> Color.Blue
        "yellow" -> Color.Yellow
        "cyan" -> Color.Cyan
        "magenta" -> Color.Magenta
        "black" -> Color.Black
        "white" -> Color.White
        "gray" -> Color.Gray
        "transparent" -> Color.Transparent
        else -> Color.Gray
    }
}

fun getFileName(path: String): String {
    if (path.isEmpty()) return ""
    return path.substringAfterLast('/').substringAfterLast('\\')
}

fun appendPickedValue(existingValue: String, newValue: String, isArray: Boolean): String {
    if (!isArray) return newValue
    if (existingValue.isBlank()) return newValue
    val existingList = existingValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (newValue in existingList) return existingValue
    return (existingList + newValue).joinToString(", ")
}

fun getFileNames(path: String, isArray: Boolean): String {
    if (path.isEmpty()) return ""
    if (!isArray) return getFileName(path)
    return path.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { getFileName(it) }.joinToString(", ")
}
