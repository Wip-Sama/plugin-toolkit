package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.features.flows.model.Flow

data class ValidationError(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val message: String
)

data class PendingConnection(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val sourceType: DataType,
    val targetType: DataType
)

enum class ReadOnlyReason {
    Running,
    UsedInOtherFlows
}

data class FlowEditorState(
    val flow: Flow = Flow(""),
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val nextId: Long = 0L,
    val hasUnsavedChanges: Boolean = false,
    val draggedNodeId: Long? = null,
    val currentDragOffset: Offset = Offset.Zero,
    val ghostPosition: Offset? = null,
    val flows: List<Flow> = emptyList(),
    val inferredTypes: Map<Pair<Long, String>, DataType> = emptyMap(),
    val inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>> = emptyMap(),
    val validationErrors: List<ValidationError> = emptyList(),
    val selectedNodeIds: Set<Long> = emptySet(),
    val isReadOnly: Boolean = false,
    val readOnlyReasons: List<ReadOnlyReason> = emptyList(),
    val pendingConnection: PendingConnection? = null
)
