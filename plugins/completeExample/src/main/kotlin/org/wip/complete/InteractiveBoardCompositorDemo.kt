package org.wip.complete

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.sdui.BoardGridStyle
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardShapeType
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.BoardViewportConfig
import org.wip.plugintoolkit.api.sdui.CapabilityJobStatus
import org.wip.plugintoolkit.api.sdui.ControlSize
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.uiTree

private data class BoardCompositorState(
    val boardTitle: String = "Chapter 42 — Page 01 Compositor",
    val activeToolType: String = "SELECT",
    val activeColor: SemanticColor = SemanticColor.PRIMARY,
    val gridStyle: BoardGridStyle = BoardGridStyle.DOTS,
    val snapToGrid: Boolean = true,
    val brushWidth: Double = 4.0,
    val counter: Int = 0,
    val clearRevision: Int = 0,
    val pageApproved: Boolean = false,
    val assets: BoardAssetBundle? = null,
    val selectedLayerIds: Set<String> = setOf("text_1"),
    val layers: List<BoardLayer> = defaultCompositorLayers(),
    val lastExportJobId: String? = null,
    val bannerMessage: String? = null
)

private fun defaultCompositorLayers(stampAsset: org.wip.plugintoolkit.api.sdui.AssetReference? = null): List<BoardLayer> {
    val base = mutableListOf<BoardLayer>(
        BoardLayer.ShapeLayer(
            id = "callout_1",
            shapeType = BoardShapeType.CALLOUT_BUBBLE,
            position = NormalizedPoint(0.08f, 0.08f),
            size = NormalizedSize(0.42f, 0.14f),
            strokeColor = SemanticColor.PRIMARY,
            fillColor = SemanticColor.SURFACE,
            strokeWidth = 3f,
            text = "Class-S Spatial Gate Opening!",
            zIndex = 1
        ),
        BoardLayer.TextLayer(
            id = "text_1",
            text = "Click to edit caption inline",
            position = NormalizedPoint(0.10f, 0.36f),
            color = SemanticColor.ON_SURFACE,
            backgroundColor = SemanticColor.SURFACE,
            borderColor = SemanticColor.PRIMARY,
            role = TypographyRole.TITLE_MEDIUM,
            zIndex = 2
        ),
        BoardLayer.ShapeLayer(
            id = "narration_box",
            shapeType = BoardShapeType.ROUNDED_RECT,
            position = NormalizedPoint(0.52f, 0.67f),
            size = NormalizedSize(0.40f, 0.12f),
            strokeColor = SemanticColor.WARNING,
            fillColor = SemanticColor.SURFACE,
            strokeWidth = 2.5f,
            text = "SECTOR 07 // OVERRIDE",
            zIndex = 3
        )
    )
    if (stampAsset != null) {
        base.add(
            BoardLayer.ImageLayer(
                id = "stamp_img_1",
                asset = stampAsset,
                position = NormalizedPoint(0.62f, 0.08f),
                size = NormalizedSize(0.28f, 0.09f),
                opacity = 0.95f,
                zIndex = 4
            )
        )
    }
    return base
}

/**
 * Interactive Stage 5 Demo 1: Spatial Component & Image Compositor Board.
 * Showcases Background Asset rendering, ImageLayer, inline-editable TextLayer, ShapeLayer callouts,
 * interactive ComponentLayer stamps, and live capability export with ProgressBarNode feedback.
 */
fun runBoardCompositorSession(
    context: UiSessionContext,
    events: Flow<UiEvent>
): Flow<UiNodeTree> = channelFlow {
    val assets = CompleteExampleSampleAssets.ensureBoardAssets(context)
    val stateFlow = MutableStateFlow(
        BoardCompositorState(
            assets = assets,
            layers = defaultCompositorLayers(assets.stampAsset)
        )
    )

    launch {
        events.collect { event ->
            handleBoardCompositorEvent(event, context, stateFlow)
        }
    }

    combine(stateFlow, context.capabilityJobs) { state, jobs ->
        renderBoardCompositorTree(state, jobs)
    }.collect { tree ->
        send(tree)
    }
}

private suspend fun handleBoardCompositorEvent(
    event: UiEvent,
    context: UiSessionContext,
    stateFlow: MutableStateFlow<BoardCompositorState>
) {
    when (event) {
        is UiEvent.Click -> {
            when (event.action ?: event.nodeId) {
                "clear_canvas", "clearBtn" -> {
                    stateFlow.update {
                        it.copy(
                            layers = emptyList(),
                            selectedLayerIds = emptySet(),
                            clearRevision = it.clearRevision + 1,
                            bannerMessage = "Board layers cleared."
                        )
                    }
                }
                "increment_counter", "counterBtn", "boardWidgetBtn" -> {
                    stateFlow.update {
                        it.copy(
                            counter = it.counter + 1,
                            pageApproved = !it.pageApproved
                        )
                    }
                }
                "add_text_layer" -> {
                    stateFlow.update { s ->
                        val nextId = "text_${s.layers.size + 1}"
                        val newLayer = BoardLayer.TextLayer(
                            id = nextId,
                            text = "New Overlay Text",
                            position = NormalizedPoint(0.25f, 0.48f),
                            color = s.activeColor,
                            backgroundColor = SemanticColor.SURFACE,
                            borderColor = s.activeColor,
                            role = TypographyRole.TITLE_MEDIUM,
                            zIndex = s.layers.size + 10
                        )
                        s.copy(
                            layers = s.layers + newLayer,
                            selectedLayerIds = setOf(nextId),
                            activeToolType = "SELECT"
                        )
                    }
                }
                "add_callout_layer" -> {
                    stateFlow.update { s ->
                        val nextId = "callout_${s.layers.size + 1}"
                        val newLayer = BoardLayer.ShapeLayer(
                            id = nextId,
                            shapeType = BoardShapeType.CALLOUT_BUBBLE,
                            position = NormalizedPoint(0.22f, 0.22f),
                            size = NormalizedSize(0.32f, 0.14f),
                            strokeColor = s.activeColor,
                            fillColor = SemanticColor.SURFACE,
                            strokeWidth = s.brushWidth.toFloat(),
                            text = "SFX / Dialogue",
                            zIndex = s.layers.size + 10
                        )
                        s.copy(
                            layers = s.layers + newLayer,
                            selectedLayerIds = setOf(nextId),
                            activeToolType = "SELECT"
                        )
                    }
                }
                "add_stamp_layer" -> {
                    stateFlow.update { s ->
                        val stampAsset = s.assets?.stampAsset ?: return@update s
                        val nextId = "stamp_${s.layers.size + 1}"
                        val newLayer = BoardLayer.ImageLayer(
                            id = nextId,
                            asset = stampAsset,
                            position = NormalizedPoint(0.35f, 0.78f),
                            size = NormalizedSize(0.28f, 0.09f),
                            opacity = 1.0f,
                            zIndex = s.layers.size + 10
                        )
                        s.copy(
                            layers = s.layers + newLayer,
                            selectedLayerIds = setOf(nextId),
                            activeToolType = "SELECT"
                        )
                    }
                }
                "export_page" -> {
                    val snapshot = stateFlow.value
                    val encodedLayers = Json.encodeToString(snapshot.layers)
                    val jobId = context.launchCapability(
                        capabilityName = "renderCompositedPage",
                        parameters = mapOf(
                            "boardTitle" to JsonPrimitive(snapshot.boardTitle),
                            "layersJson" to JsonPrimitive(encodedLayers)
                        ),
                        runInBackgroundQueue = false
                    )
                    stateFlow.update {
                        it.copy(
                            lastExportJobId = jobId,
                            bannerMessage = "Started export job $jobId"
                        )
                    }
                }
                "finish_session" -> {
                    val s = stateFlow.value
                    context.completeSession(
                        PluginResponse(
                            result = JsonPrimitive("Completed compositor '${s.boardTitle}' with ${s.layers.size} layers")
                        )
                    )
                }
                "dismiss_banner" -> {
                    stateFlow.update { it.copy(bannerMessage = null) }
                }
                else -> {
                    val actionStr = event.action ?: event.nodeId
                    when {
                        actionStr.startsWith("select_layer:") -> {
                            val id = actionStr.removePrefix("select_layer:")
                            stateFlow.update { it.copy(selectedLayerIds = setOf(id)) }
                        }
                        actionStr.startsWith("toggle_vis:") -> {
                            val id = actionStr.removePrefix("toggle_vis:")
                            stateFlow.update { s ->
                                s.copy(
                                    layers = s.layers.map { l ->
                                        if (l.id != id) l else when (l) {
                                            is BoardLayer.TextLayer -> l.copy(visible = !l.visible)
                                            is BoardLayer.ShapeLayer -> l.copy(visible = !l.visible)
                                            is BoardLayer.ImageLayer -> l.copy(visible = !l.visible)
                                            is BoardLayer.ComponentLayer -> l.copy(visible = !l.visible)
                                            is BoardLayer.PathLayer -> l.copy(visible = !l.visible)
                                        }
                                    }
                                )
                            }
                        }
                        actionStr.startsWith("toggle_lock:") -> {
                            val id = actionStr.removePrefix("toggle_lock:")
                            stateFlow.update { s ->
                                s.copy(
                                    layers = s.layers.map { l ->
                                        if (l.id != id) l else when (l) {
                                            is BoardLayer.TextLayer -> l.copy(locked = !l.locked)
                                            is BoardLayer.ShapeLayer -> l.copy(locked = !l.locked)
                                            is BoardLayer.ImageLayer -> l.copy(locked = !l.locked)
                                            is BoardLayer.ComponentLayer -> l.copy(locked = !l.locked)
                                            is BoardLayer.PathLayer -> l.copy(locked = !l.locked)
                                        }
                                    }
                                )
                            }
                        }
                        actionStr.startsWith("delete_layer:") -> {
                            val id = actionStr.removePrefix("delete_layer:")
                            stateFlow.update { s ->
                                s.copy(
                                    layers = s.layers.filterNot { it.id == id },
                                    selectedLayerIds = s.selectedLayerIds - id
                                )
                            }
                        }
                    }
                }
            }
        }
        is UiEvent.TextChanged -> {
            when (event.nodeId) {
                "titleInput" -> stateFlow.update { it.copy(boardTitle = event.text) }
                "selectedLayerTextInput" -> {
                    stateFlow.update { s ->
                        val selectedId = s.selectedLayerIds.firstOrNull() ?: return@update s
                        s.copy(
                            layers = s.layers.map { layer ->
                                if (layer.id != selectedId) layer else when (layer) {
                                    is BoardLayer.TextLayer -> layer.copy(text = event.text)
                                    is BoardLayer.ShapeLayer -> layer.copy(text = event.text)
                                    else -> layer
                                }
                            }
                        )
                    }
                }
            }
        }
        is UiEvent.ValueChanged -> {
            if (event.nodeId == "widthSlider") {
                stateFlow.update { it.copy(brushWidth = event.value) }
            }
        }
        is UiEvent.ToggleChanged -> {
            if (event.nodeId == "snapToggle") {
                stateFlow.update { it.copy(snapToGrid = event.checked) }
            }
        }
        is UiEvent.OptionSelected -> {
            when (event.nodeId) {
                "toolDropdown" -> stateFlow.update { it.copy(activeToolType = event.selectedKey) }
                "colorDropdown" -> {
                    val parsed = runCatching { SemanticColor.valueOf(event.selectedKey) }
                        .getOrDefault(SemanticColor.PRIMARY)
                    stateFlow.update { it.copy(activeColor = parsed) }
                }
                "gridStyleDropdown" -> {
                    val parsed = runCatching { BoardGridStyle.valueOf(event.selectedKey) }
                        .getOrDefault(BoardGridStyle.DOTS)
                    stateFlow.update { it.copy(gridStyle = parsed) }
                }
            }
        }
        is UiEvent.PathCommitted -> {
            if (event.nodeId == "drawingBoard") {
                stateFlow.update { s ->
                    val newPath = BoardLayer.PathLayer(
                        id = "path_${s.layers.size + 1}",
                        path = event.path,
                        zIndex = s.layers.size + 10
                    )
                    s.copy(layers = s.layers + newPath)
                }
            }
        }
        is UiEvent.BoardLayerSelected -> {
            stateFlow.update { it.copy(selectedLayerIds = event.selectedLayerIds.toSet()) }
        }
        is UiEvent.BoardLayerMoved -> {
            stateFlow.update { s ->
                s.copy(
                    layers = s.layers.map { existing ->
                        if (existing.id != event.layerId) existing else when (existing) {
                            is BoardLayer.TextLayer -> existing.copy(position = event.position)
                            is BoardLayer.ImageLayer -> existing.copy(position = event.position)
                            is BoardLayer.ShapeLayer -> existing.copy(position = event.position)
                            is BoardLayer.ComponentLayer -> existing.copy(position = event.position)
                            is BoardLayer.PathLayer -> existing
                        }
                    }
                )
            }
        }
        is UiEvent.BoardLayerResized -> {
            stateFlow.update { s ->
                s.copy(
                    layers = s.layers.map { existing ->
                        if (existing.id != event.layerId) existing else when (existing) {
                            is BoardLayer.TextLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.ImageLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.ShapeLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.ComponentLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.PathLayer -> existing
                        }
                    }
                )
            }
        }
        is UiEvent.BoardTextEdited -> {
            stateFlow.update { s ->
                s.copy(
                    layers = s.layers.map { existing ->
                        if (existing.id == event.layerId) {
                            when (existing) {
                                is BoardLayer.TextLayer -> existing.copy(text = event.text)
                                is BoardLayer.ShapeLayer -> existing.copy(text = event.text)
                                else -> existing
                            }
                        } else {
                            existing
                        }
                    }
                )
            }
        }
        is UiEvent.BoardTextPlaced -> {
            stateFlow.update { s ->
                val newId = "text_${s.layers.size + 1}"
                val placed = BoardLayer.TextLayer(
                    id = newId,
                    text = event.text,
                    position = event.position,
                    color = s.activeColor,
                    backgroundColor = SemanticColor.SURFACE,
                    borderColor = s.activeColor,
                    zIndex = s.layers.size + 10
                )
                s.copy(
                    layers = s.layers + placed,
                    selectedLayerIds = setOf(newId),
                    activeToolType = "SELECT"
                )
            }
        }
        is UiEvent.BoardShapeCommitted -> {
            stateFlow.update { s ->
                val newId = "shape_${s.layers.size + 1}"
                val committed = BoardLayer.ShapeLayer(
                    id = newId,
                    shapeType = event.shapeType,
                    position = event.position,
                    size = event.size,
                    strokeColor = event.strokeColor,
                    fillColor = event.fillColor,
                    strokeWidth = event.strokeWidth,
                    zIndex = s.layers.size + 10
                )
                s.copy(
                    layers = s.layers + committed,
                    selectedLayerIds = setOf(newId),
                    activeToolType = "SELECT"
                )
            }
        }
        is UiEvent.BoardLayerDeleted -> {
            val toDelete = event.layerIds.toSet()
            stateFlow.update { s ->
                s.copy(
                    layers = s.layers.filterNot { it.id in toDelete },
                    selectedLayerIds = s.selectedLayerIds - toDelete
                )
            }
        }
        is UiEvent.BoardCanvasClicked -> {
            stateFlow.update { it.copy(selectedLayerIds = emptySet()) }
        }
        is UiEvent.Custom -> {
            when (event.action) {
                "SELECT_TOOL" -> {
                    val toolKey = when (event.payload?.jsonPrimitive?.content) {
                        "select" -> "SELECT"
                        "pan" -> "PAN"
                        "text" -> "TEXT"
                        "shape" -> "SHAPE"
                        "brush" -> "PEN"
                        "highlighter" -> "HIGHLIGHTER"
                        "eraser" -> "ERASER"
                        else -> null
                    }
                    if (toolKey != null) {
                        stateFlow.update { it.copy(activeToolType = toolKey) }
                    }
                }
                "CLEAR" -> {
                    stateFlow.update {
                        it.copy(
                            layers = emptyList(),
                            selectedLayerIds = emptySet(),
                            clearRevision = it.clearRevision + 1
                        )
                    }
                }
            }
        }
        is UiEvent.TabSelected,
        is UiEvent.ChipSelected,
        is UiEvent.DialogAction -> Unit
    }
}

private fun renderBoardCompositorTree(
    state: BoardCompositorState,
    jobs: List<org.wip.plugintoolkit.api.sdui.CapabilityJobSnapshot>
): UiNodeTree = uiTree {
    val activeBoardTool: BoardTool = when (state.activeToolType) {
        "SELECT" -> BoardTool.Select()
        "PAN" -> BoardTool.Pan
        "TEXT" -> BoardTool.PlaceText(defaultText = "New Overlay Caption", color = state.activeColor)
        "SHAPE" -> BoardTool.Shape(
            shapeType = BoardShapeType.CALLOUT_BUBBLE,
            strokeColor = state.activeColor,
            fillColor = SemanticColor.SURFACE,
            strokeWidth = state.brushWidth.toFloat()
        )
        "HIGHLIGHTER" -> BoardTool.Highlighter(color = state.activeColor, strokeWidth = (state.brushWidth * 2.5).toFloat())
        "ERASER" -> BoardTool.Eraser(radius = (state.brushWidth * 2).toFloat())
        else -> BoardTool.Brush(color = state.activeColor, strokeWidth = state.brushWidth.toFloat())
    }

    val activeExportJob = state.lastExportJobId?.let { id -> jobs.find { it.jobId == id } }
        ?: jobs.lastOrNull { it.capabilityName == "renderCompositedPage" }

    scaffold(id = "interactiveScaffold", modifier = UiModifier.fillMaxSize()) {
        topBar {
            row(
                id = "topToolbarRow",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxWidth().padding(SpacingSize.SMALL)
            ) {
                icon(id = "topIcon", name = "layers", tint = SemanticColor.PRIMARY, size = ControlSize.MEDIUM)
                text(
                    id = "topTitle",
                    text = state.boardTitle,
                    role = TypographyRole.TITLE_MEDIUM,
                    color = SemanticColor.ON_SURFACE,
                    modifier = UiModifier.weight(1f)
                )
                badge(
                    id = "layerCountBadge",
                    text = "${state.layers.size} layers",
                    color = SemanticColor.INFO
                )
                button(
                    id = "addTextBtn",
                    label = "+ Text",
                    action = "add_text_layer",
                    isPrimary = false
                )
                button(
                    id = "addCalloutBtn",
                    label = "+ Callout",
                    action = "add_callout_layer",
                    isPrimary = false
                )
                button(
                    id = "addStampBtn",
                    label = "+ Stamp",
                    action = "add_stamp_layer",
                    isPrimary = false
                )
                button(
                    id = "saveRenderBtn",
                    label = if (activeExportJob?.isRunning == true) "Rendering..." else "Save & Render Page",
                    action = "export_page",
                    isPrimary = true,
                    modifier = UiModifier.enabled(activeExportJob?.isRunning != true)
                )
            }
        }

        content {
            board(
                id = "drawingBoard",
                backgroundAsset = state.assets?.backgroundAsset,
                activeTool = activeBoardTool,
                viewportConfig = BoardViewportConfig(
                    gridStyle = state.gridStyle,
                    snapToGrid = state.snapToGrid,
                    gridStep = 0.05f,
                    artboardAspectRatio = 0.75f
                ),
                selectedLayerIds = state.selectedLayerIds,
                clearRevision = state.clearRevision,
                showToolbar = true,
                showZoomControls = true,
                modifier = UiModifier.fillMaxSize()
            ) {
                state.layers.forEach { layer(it) }
                componentLayer(
                    id = "inspector_widget",
                    position = NormalizedPoint(0.08f, 0.70f),
                    size = NormalizedSize(0.38f, 0.20f),
                    zIndex = 50
                ) {
                    column(id = "widget_col", spacing = SpacingSize.EXTRA_SMALL) {
                        row(id = "widget_header", spacing = SpacingSize.SMALL) {
                            text(
                                id = "widget_title",
                                text = "Page QC Stamp",
                                role = TypographyRole.TITLE_MEDIUM,
                                color = SemanticColor.PRIMARY
                            )
                            badge(
                                id = "widget_status",
                                text = if (state.pageApproved) "APPROVED" else "PENDING",
                                color = if (state.pageApproved) SemanticColor.SUCCESS else SemanticColor.WARNING
                            )
                        }
                        text(
                            id = "widget_sel",
                            text = "Selected: ${state.selectedLayerIds.joinToString().ifEmpty { "None" }}",
                            role = TypographyRole.BODY_SMALL
                        )
                        button(
                            id = "boardWidgetBtn",
                            label = if (state.pageApproved) "Revoke QC (${state.counter})" else "Approve Page (${state.counter})",
                            action = "increment_counter"
                        )
                    }
                }
            }
        }

        rightSidebar(
            id = "inspectorSidebar",
            title = "Layers & Export",
            isCollapsible = true,
            isCollapsed = false,
            modifier = UiModifier.width(320)
        ) {
            if (activeExportJob != null) {
                card(id = "exportJobCard", title = "Render Capability Job", modifier = UiModifier.fillMaxWidth()) {
                    val prog = activeExportJob.primaryProgress
                    progressBar(
                        id = "exportProgressBar",
                        progress = if (activeExportJob.status == CapabilityJobStatus.COMPLETED) 1f else prog?.fraction,
                        label = activeExportJob.status.name,
                        statusText = prog?.message ?: activeExportJob.errorMessage ?: "Processing...",
                        color = when (activeExportJob.status) {
                            CapabilityJobStatus.COMPLETED -> SemanticColor.SUCCESS
                            CapabilityJobStatus.FAILED -> SemanticColor.ERROR
                            else -> SemanticColor.PRIMARY
                        }
                    )
                    if (activeExportJob.recentLogs.isNotEmpty()) {
                        text(
                            id = "exportLatestLog",
                            text = activeExportJob.recentLogs.takeLast(2).joinToString("\n"),
                            role = TypographyRole.BODY_SMALL
                        )
                    }
                }
            }

            state.bannerMessage?.let { msg ->
                alertBanner(
                    id = "compositorBanner",
                    message = msg,
                    severity = SemanticColor.INFO,
                    actionLabel = "OK"
                )
            }

            val selectedLayer = state.layers.find { it.id in state.selectedLayerIds }
            if (selectedLayer != null) {
                card(id = "selectedInspectorCard", title = "Inspector: ${selectedLayer.id}", modifier = UiModifier.fillMaxWidth()) {
                    val editableText = when (selectedLayer) {
                        is BoardLayer.TextLayer -> selectedLayer.text
                        is BoardLayer.ShapeLayer -> selectedLayer.text.orEmpty()
                        else -> null
                    }
                    if (editableText != null) {
                        textInput(
                            id = "selectedLayerTextInput",
                            text = editableText,
                            label = "Layer Text"
                        )
                    } else {
                        text(
                            id = "selectedLayerTypeInfo",
                            text = "Layer type: ${selectedLayer::class.simpleName} (z=${selectedLayer.zIndex})",
                            role = TypographyRole.BODY_SMALL
                        )
                    }
                }
            }

            text(id = "layersListHeader", text = "Board Layers (${state.layers.size})", role = TypographyRole.TITLE_MEDIUM)
            if (state.layers.isEmpty()) {
                emptyState(
                    id = "noLayersEmpty",
                    title = "No Layers",
                    description = "Use + Text, + Callout, or drawing tools to annotate the page.",
                    iconName = "layers",
                    actionLabel = "+ Text"
                )
            } else {
                scrollableColumn(
                    id = "layersListScroll",
                    spacing = SpacingSize.EXTRA_SMALL,
                    modifier = UiModifier.fillMaxWidth().weight(1f)
                ) {
                    state.layers.sortedByDescending { it.zIndex }.forEach { layer ->
                        val label = when (layer) {
                            is BoardLayer.TextLayer -> layer.text
                            is BoardLayer.ShapeLayer -> layer.text ?: layer.shapeType.name
                            is BoardLayer.ImageLayer -> "Image Stamp"
                            is BoardLayer.PathLayer -> "Vector Stroke (${layer.path.points.size} pts)"
                            is BoardLayer.ComponentLayer -> "Widget Component"
                        }
                        val icon = when (layer) {
                            is BoardLayer.TextLayer -> "text_fields"
                            is BoardLayer.ShapeLayer -> "chat_bubble"
                            is BoardLayer.ImageLayer -> "image"
                            is BoardLayer.PathLayer -> "brush"
                            is BoardLayer.ComponentLayer -> "layers"
                        }
                        listItem(
                            id = "layer_row_${layer.id}",
                            title = label,
                            subtitle = "${layer.id} • z=${layer.zIndex}${if (layer.locked) " • Locked" else ""}",
                            leadingIcon = icon,
                            selected = layer.id in state.selectedLayerIds,
                            trailingBlock = {
                                iconButton(
                                    id = "layer_sel_${layer.id}",
                                    iconName = "select",
                                    action = "select_layer:${layer.id}",
                                    tooltip = "Select layer"
                                )
                                iconButton(
                                    id = "layer_vis_${layer.id}",
                                    iconName = if (layer.visible) "visibility" else "visibility_off",
                                    action = "toggle_vis:${layer.id}",
                                    tooltip = "Toggle visibility"
                                )
                                iconButton(
                                    id = "layer_lock_${layer.id}",
                                    iconName = if (layer.locked) "lock" else "unlock",
                                    action = "toggle_lock:${layer.id}",
                                    tooltip = "Toggle lock"
                                )
                                iconButton(
                                    id = "layer_del_${layer.id}",
                                    iconName = "delete",
                                    action = "delete_layer:${layer.id}",
                                    tint = SemanticColor.ERROR,
                                    tooltip = "Delete layer"
                                )
                            }
                        )
                    }
                }
            }
        }

        floatingPanel(
            id = "controlsPanel",
            anchor = FloatingAnchor.TOP_END,
            title = "Board Controls",
            isCollapsible = true,
            isCollapsed = true
        ) {
            textInput(
                id = "titleInput",
                text = state.boardTitle,
                label = "Board Title",
                placeholder = "Enter title..."
            )
            dropdown(
                id = "toolDropdown",
                label = "Active Tool",
                options = mapOf(
                    "SELECT" to "Select / Move / Edit",
                    "PAN" to "Pan Camera",
                    "TEXT" to "Place Text",
                    "SHAPE" to "Draw Callout Bubble",
                    "PEN" to "Freehand Pen",
                    "HIGHLIGHTER" to "Highlighter",
                    "ERASER" to "Eraser"
                ),
                selectedKey = state.activeToolType
            )
            dropdown(
                id = "colorDropdown",
                label = "Active Color",
                options = mapOf(
                    "PRIMARY" to "Primary Blue",
                    "SECONDARY" to "Secondary Gray",
                    "ACCENT" to "Accent Teal",
                    "HIGHLIGHT" to "Warning Highlight",
                    "ERROR" to "Error Red"
                ),
                selectedKey = state.activeColor.name
            )
            dropdown(
                id = "gridStyleDropdown",
                label = "Viewport Grid",
                options = mapOf(
                    "DOTS" to "Dots",
                    "LINES" to "Lines",
                    "CROSSHAIRS" to "Crosshairs",
                    "NONE" to "None"
                ),
                selectedKey = state.gridStyle.name
            )
            toggle(
                id = "snapToggle",
                label = "Snap to Grid",
                checked = state.snapToGrid
            )
            slider(
                id = "widthSlider",
                value = state.brushWidth,
                min = 1.0,
                max = 20.0,
                steps = 19,
                label = "Stroke Width: ${state.brushWidth.toInt()}px"
            )
            row(id = "controlsBtnRow", spacing = SpacingSize.SMALL) {
                button(
                    id = "clearBtn",
                    label = "Clear Board",
                    action = "clear_canvas",
                    isPrimary = false,
                    isDestructive = true
                )
                button(
                    id = "counterBtn",
                    label = "Taps: ${state.counter}",
                    action = "increment_counter"
                )
            }
            text(
                id = "footerStats",
                text = "Tool: ${state.activeToolType} | Color: ${state.activeColor.name} | Layers: ${state.layers.size + 1}",
                role = TypographyRole.LABEL,
                color = SemanticColor.ON_SURFACE
            )
        }
    }
}
