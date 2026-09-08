package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.geometry.Offset as ComposeOffset
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import kotlin.math.round

fun ModelOffset.toComposeOffset(): ComposeOffset = ComposeOffset(x, y)
fun ComposeOffset.toModelOffset(): ModelOffset = ModelOffset(x, y)

operator fun ModelOffset.plus(other: ComposeOffset): ModelOffset = ModelOffset(x + other.x, y + other.y)
operator fun ComposeOffset.plus(other: ModelOffset): ComposeOffset = ComposeOffset(x + other.x, y + other.y)
operator fun ModelOffset.minus(other: ComposeOffset): ModelOffset = ModelOffset(x - other.x, y - other.y)
operator fun ComposeOffset.minus(other: ModelOffset): ComposeOffset = ComposeOffset(x - other.x, y - other.y)

fun ModelOffset.snapToGrid(gridSize: Float = 50f): ModelOffset {
    val snappedX = round(x / gridSize) * gridSize
    val snappedY = round(y / gridSize) * gridSize
    return ModelOffset(if (snappedX == -0f) 0f else snappedX, if (snappedY == -0f) 0f else snappedY)
}

fun ComposeOffset.snapToGrid(gridSize: Float = 50f): ComposeOffset {
    val snappedX = round(x / gridSize) * gridSize
    val snappedY = round(y / gridSize) * gridSize
    return ComposeOffset(if (snappedX == -0f) 0f else snappedX, if (snappedY == -0f) 0f else snappedY)
}
