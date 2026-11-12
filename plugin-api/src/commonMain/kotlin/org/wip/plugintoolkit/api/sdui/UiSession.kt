package org.wip.plugintoolkit.api.sdui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.ProgressData

/**
 * Execution status for a capability or action triggered from within a UI session.
 */
@Serializable
enum class CapabilityJobStatus {
    QUEUED,
    RUNNING,
    PAUSE_REQUESTED,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Immutable snapshot of a capability job executed or observed by a UI session.
 */
@Serializable
data class CapabilityJobSnapshot(
    val jobId: String,
    val capabilityName: String,
    val status: CapabilityJobStatus = CapabilityJobStatus.QUEUED,
    val primaryProgress: ProgressData? = null,
    val secondaryProgress: ProgressData? = null,
    val subProgress: Map<String, ProgressData> = emptyMap(),
    val recentLogs: List<String> = emptyList(),
    val result: ExecutionResult? = null,
    val errorMessage: String? = null,
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null
) {
    val isTerminal: Boolean
        get() = status == CapabilityJobStatus.COMPLETED ||
                status == CapabilityJobStatus.FAILED ||
                status == CapabilityJobStatus.CANCELLED

    val isRunning: Boolean
        get() = status == CapabilityJobStatus.RUNNING
}

/**
 * Interface implemented by plugins that provide reactive, server-driven user interfaces.
 */
interface InteractiveUiProcessor {
    /**
     * Open a long-lived interactive UI session.
     *
     * @param context Execution context providing plugin services, capability execution, and reactive state flows.
     * @param events Inbound stream of user interaction events (clicks, text changes, spatial board actions).
     * @return Outbound reactive stream of UI node trees rendered in the host sandbox.
     */
    fun openUiSession(context: UiSessionContext, events: Flow<UiEvent>): Flow<UiNodeTree>
}

/**
 * Context provided to an interactive UI session, extending PluginContext with lifecycle termination controls,
 * background capability execution, live job monitoring, and reactive state flows.
 */
interface UiSessionContext : PluginContext {
    /**
     * Unique session identifier assigned by the host.
     */
    val sessionId: String get() = ""

    /**
     * Capability name that opened this interactive UI session.
     */
    val capabilityName: String get() = ""

    /**
     * Initial input parameters passed to this interactive capability.
     */
    val initialParameters: Map<String, JsonElement> get() = emptyMap()

    /**
     * Reactive stream of this plugin's persisted settings.
     * Emits whenever settings are updated in the host or via [updateSetting].
     */
    val settingsFlow: StateFlow<Map<String, JsonElement>> get() = MutableStateFlow(settings)

    /**
     * Reactive stream of capability locks for this plugin.
     * Map key is lock identifier, value is true if currently locked.
     */
    val locksFlow: StateFlow<Map<String, Boolean>> get() = MutableStateFlow(emptyMap())

    /**
     * Reactive stream of active and recently completed capability jobs launched by this session or plugin.
     */
    val capabilityJobs: StateFlow<List<CapabilityJobSnapshot>> get() = MutableStateFlow(emptyList())

    /**
     * Terminate the interactive session successfully, returning a [PluginResponse] to the caller.
     * Completes the host job and resumes downstream connected flow nodes if running in a Flow.
     */
    fun completeSession(response: PluginResponse)

    /**
     * Terminate the interactive session with a cancellation or failure reason.
     */
    fun cancelSession(reason: String)

    /**
     * Launch a capability asynchronously.
     * If [runInBackgroundQueue] is true, the capability is queued to the host's background [JobManager].
     * If false, it executes inline on the session scope.
     *
     * @return The unique job ID assigned to this execution.
     */
    suspend fun launchCapability(
        capabilityName: String,
        parameters: Map<String, JsonElement> = emptyMap(),
        runInBackgroundQueue: Boolean = true
    ): String = ""

    /**
     * Execute a capability inline and await its final [ExecutionResult].
     * Live progress and logs are streamed to [capabilityJobs] during execution.
     */
    suspend fun executeCapability(
        capabilityName: String,
        parameters: Map<String, JsonElement> = emptyMap()
    ): ExecutionResult = ExecutionResult.Error("Not implemented")

    /**
     * Launch an action defined by the plugin.
     */
    suspend fun launchAction(
        actionName: String,
        parameters: Map<String, JsonElement> = emptyMap()
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Not implemented"))

    /**
     * Observe live progress, logs, and status updates for a specific job launched via [launchCapability].
     */
    fun observeCapabilityJob(jobId: String): Flow<CapabilityJobSnapshot> = emptyFlow()

    /**
     * Request pausing a running capability job.
     */
    suspend fun pauseCapabilityJob(jobId: String) {}

    /**
     * Resume a paused capability job.
     */
    suspend fun resumeCapabilityJob(jobId: String) {}

    /**
     * Cancel a queued or running capability job.
     */
    suspend fun cancelCapabilityJob(jobId: String) {}
}

/**
 * Interactive session scope providing convenience functions to update state,
 * launch capabilities, and query settings/locks within a reactive UI session.
 */
class InteractiveSessionScope<S>(
    val context: UiSessionContext,
    private val stateFlow: MutableStateFlow<S>
) {
    val state: S get() = stateFlow.value

    fun updateState(transform: (S) -> S) {
        stateFlow.update(transform)
    }

    fun setState(newState: S) {
        stateFlow.value = newState
    }
}

/**
 * High-level helper for implementing [InteractiveUiProcessor.openUiSession].
 *
 * Automatically manages local reactive state [S], processes incoming [events],
 * and emits a stream of [UiNodeTree] whenever the local state, plugin settings,
 * or capability job statuses change.
 *
 * @param initialState Initial UI state.
 * @param context The session context provided by the host.
 * @param events Stream of user interaction events.
 * @param onEvent Handler mapping user events to state updates or capability launches.
 * @param render Pure function rendering the UI node tree for the current state.
 */
fun <S> interactiveUiSession(
    initialState: S,
    context: UiSessionContext,
    events: Flow<UiEvent>,
    onEvent: suspend InteractiveSessionScope<S>.(UiEvent) -> Unit,
    render: S.(UiSessionContext) -> UiNodeTree
): Flow<UiNodeTree> = channelFlow {
    val stateFlow = MutableStateFlow(initialState)
    val sessionScope = InteractiveSessionScope(context, stateFlow)

    launch {
        events.collect { event ->
            sessionScope.onEvent(event)
        }
    }

    combine(
        stateFlow,
        context.capabilityJobs,
        context.settingsFlow
    ) { currentState, _, _ ->
        currentState.render(context)
    }.collect { tree ->
        send(tree)
    }
}

// ── Declarative SDUI Builder DSL ───────────────────────────────────────────

@DslMarker
annotation class UiDslMarker

@UiDslMarker
abstract class UiContainerBuilder {
    protected val children = mutableListOf<UiNode>()

    fun add(node: UiNode) {
        children.add(node)
    }

    fun column(
        id: String = generateNodeId("col"),
        spacing: SpacingSize = SpacingSize.NONE,
        modifier: UiModifier = UiModifier.None,
        block: ColumnBuilder.() -> Unit
    ) {
        val builder = ColumnBuilder(id, spacing, modifier).apply(block)
        add(builder.build())
    }

    fun row(
        id: String = generateNodeId("row"),
        spacing: SpacingSize = SpacingSize.NONE,
        modifier: UiModifier = UiModifier.None,
        block: RowBuilder.() -> Unit
    ) {
        val builder = RowBuilder(id, spacing, modifier).apply(block)
        add(builder.build())
    }

    fun card(
        id: String = generateNodeId("card"),
        title: String? = null,
        modifier: UiModifier = UiModifier.None,
        block: CardBuilder.() -> Unit
    ) {
        val builder = CardBuilder(id, title, modifier).apply(block)
        add(builder.build())
    }

    fun scrollableColumn(
        id: String = generateNodeId("scroll"),
        spacing: SpacingSize = SpacingSize.NONE,
        modifier: UiModifier = UiModifier.None,
        block: ScrollableColumnBuilder.() -> Unit
    ) {
        val builder = ScrollableColumnBuilder(id, spacing, modifier).apply(block)
        add(builder.build())
    }

    fun text(
        text: String,
        id: String = generateNodeId("txt"),
        role: TypographyRole = TypographyRole.BODY_MEDIUM,
        color: SemanticColor? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(TextNode(id = id, text = text, role = role, color = color, modifier = modifier))
    }

    fun button(
        label: String,
        id: String = generateNodeId("btn"),
        action: String? = null,
        isPrimary: Boolean = true,
        isDestructive: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(ButtonNode(id = id, label = label, action = action, isPrimary = isPrimary, isDestructive = isDestructive, modifier = modifier))
    }

    fun textInput(
        id: String = generateNodeId("input"),
        text: String = "",
        label: String? = null,
        placeholder: String? = null,
        singleLine: Boolean = true,
        debounceMs: Long = 150L,
        revision: Int = 0,
        forceOverwrite: Boolean = false,
        regexPattern: String? = null,
        maxLength: Int? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            TextInputNode(
                id = id,
                text = text,
                label = label,
                placeholder = placeholder,
                singleLine = singleLine,
                debounceMs = debounceMs,
                revision = revision,
                forceOverwrite = forceOverwrite,
                regexPattern = regexPattern,
                maxLength = maxLength,
                modifier = modifier
            )
        )
    }

    fun toggle(
        id: String = generateNodeId("toggle"),
        checked: Boolean,
        label: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(ToggleNode(id = id, checked = checked, label = label, modifier = modifier))
    }

    fun slider(
        id: String = generateNodeId("slider"),
        value: Double,
        min: Double = 0.0,
        max: Double = 1.0,
        steps: Int = 0,
        label: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(SliderNode(id = id, value = value, min = min, max = max, steps = steps, label = label, modifier = modifier))
    }

    fun dropdown(
        id: String = generateNodeId("dropdown"),
        selectedKey: String,
        options: Map<String, String>,
        label: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(DropdownNode(id = id, selectedKey = selectedKey, options = options, label = label, modifier = modifier))
    }

    fun image(
        asset: AssetReference,
        id: String = generateNodeId("img"),
        contentDescription: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(ImageNode(id = id, asset = asset, contentDescription = contentDescription, modifier = modifier))
    }

    fun icon(
        name: String,
        id: String = generateNodeId("icon"),
        tint: SemanticColor? = null,
        size: ControlSize = ControlSize.MEDIUM,
        modifier: UiModifier = UiModifier.None
    ) {
        add(IconNode(id = id, name = name, tint = tint, size = size, modifier = modifier))
    }

    fun iconButton(
        iconName: String,
        id: String = generateNodeId("icon_btn"),
        action: String? = null,
        tooltip: String? = null,
        tint: SemanticColor? = null,
        isTonal: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            IconButtonNode(
                id = id,
                iconName = iconName,
                action = action,
                tooltip = tooltip,
                tint = tint,
                isTonal = isTonal,
                modifier = modifier
            )
        )
    }

    fun progressBar(
        id: String = generateNodeId("progress"),
        progress: Float? = null,
        secondaryProgress: Float? = null,
        label: String? = null,
        statusText: String? = null,
        isCircular: Boolean = false,
        color: SemanticColor = SemanticColor.PRIMARY,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            ProgressBarNode(
                id = id,
                progress = progress,
                secondaryProgress = secondaryProgress,
                label = label,
                statusText = statusText,
                isCircular = isCircular,
                color = color,
                modifier = modifier
            )
        )
    }

    fun tabs(
        selectedTabKey: String,
        tabs: List<TabItem>,
        id: String = generateNodeId("tabs"),
        modifier: UiModifier = UiModifier.None,
        contentBlock: UiContainerBuilder.() -> Unit
    ) {
        val contentBuilder = BoxBuilder(generateNodeId("tab_content"), UiModifier.None).apply(contentBlock)
        add(
            TabsNode(
                id = id,
                tabs = tabs,
                selectedTabKey = selectedTabKey,
                content = contentBuilder.build(),
                modifier = modifier
            )
        )
    }

    fun listItem(
        title: String,
        id: String = generateNodeId("item"),
        subtitle: String? = null,
        leadingIcon: String? = null,
        badgeText: String? = null,
        badgeColor: SemanticColor? = null,
        selected: Boolean = false,
        selectable: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        trailingBlock: (UiContainerBuilder.() -> Unit)? = null
    ) {
        val trailingContent = if (trailingBlock != null) {
            val builder = BoxBuilder(generateNodeId("trailing"), UiModifier.None).apply(trailingBlock)
            builder.buildChildren()
        } else {
            emptyList()
        }
        add(
            ListItemNode(
                id = id,
                title = title,
                subtitle = subtitle,
                leadingIcon = leadingIcon,
                badgeText = badgeText,
                badgeColor = badgeColor,
                selected = selected,
                selectable = selectable,
                trailingContent = trailingContent,
                modifier = modifier
            )
        )
    }

    fun checkbox(
        checked: Boolean,
        id: String = generateNodeId("chk"),
        label: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(CheckboxNode(id = id, checked = checked, label = label, modifier = modifier))
    }

    fun chipGroup(
        chips: List<ChipItem>,
        id: String = generateNodeId("chips"),
        selectedKeys: Set<String> = emptySet(),
        multiSelect: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(ChipGroupNode(id = id, chips = chips, selectedKeys = selectedKeys, multiSelect = multiSelect, modifier = modifier))
    }

    fun alertBanner(
        message: String,
        id: String = generateNodeId("alert"),
        title: String? = null,
        severity: SemanticColor = SemanticColor.INFO,
        iconName: String? = null,
        actionLabel: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            AlertBannerNode(
                id = id,
                title = title,
                message = message,
                severity = severity,
                iconName = iconName,
                actionLabel = actionLabel,
                modifier = modifier
            )
        )
    }

    fun emptyState(
        title: String,
        id: String = generateNodeId("empty"),
        description: String? = null,
        iconName: String = "info",
        actionLabel: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            EmptyStateNode(
                id = id,
                title = title,
                description = description,
                iconName = iconName,
                actionLabel = actionLabel,
                modifier = modifier
            )
        )
    }

    fun dialog(
        title: String,
        id: String = generateNodeId("dialog"),
        isOpen: Boolean = true,
        confirmLabel: String = "OK",
        dismissLabel: String? = "Cancel",
        isDestructive: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: UiContainerBuilder.() -> Unit
    ) {
        val builder = BoxBuilder(generateNodeId("dialog_content"), UiModifier.None).apply(block)
        add(
            DialogNode(
                id = id,
                title = title,
                isOpen = isOpen,
                confirmLabel = confirmLabel,
                dismissLabel = dismissLabel,
                isDestructive = isDestructive,
                children = builder.buildChildren(),
                modifier = modifier
            )
        )
    }

    fun splitPane(
        id: String = generateNodeId("split"),
        splitFraction: Float = 0.35f,
        isHorizontal: Boolean = true,
        modifier: UiModifier = UiModifier.None,
        first: UiContainerBuilder.() -> Unit,
        second: UiContainerBuilder.() -> Unit
    ) {
        val firstBuilder = BoxBuilder(generateNodeId("split_first"), UiModifier.None).apply(first)
        val secondBuilder = BoxBuilder(generateNodeId("split_second"), UiModifier.None).apply(second)
        add(
            SplitPaneNode(
                id = id,
                first = firstBuilder.build(),
                second = secondBuilder.build(),
                splitFraction = splitFraction,
                isHorizontal = isHorizontal,
                modifier = modifier
            )
        )
    }

    fun spacer(
        size: SpacingSize = SpacingSize.MEDIUM,
        id: String = generateNodeId("spacer"),
        modifier: UiModifier = UiModifier.None
    ) {
        add(SpacerNode(id = id, size = size, modifier = modifier))
    }

    fun scrollableRow(
        id: String = generateNodeId("scroll_row"),
        spacing: SpacingSize = SpacingSize.NONE,
        modifier: UiModifier = UiModifier.None,
        block: ScrollableRowBuilder.() -> Unit
    ) {
        val builder = ScrollableRowBuilder(id, spacing, modifier).apply(block)
        add(builder.build())
    }

    fun board(
        id: String = generateNodeId("board"),
        backgroundAsset: AssetReference? = null,
        viewportConfig: BoardViewportConfig = BoardViewportConfig(),
        layers: List<BoardLayer> = emptyList(),
        selectedLayerIds: Set<String> = emptySet(),
        activeTool: BoardTool = BoardTool.Brush(),
        clearRevision: Int = 0,
        showToolbar: Boolean = false,
        showZoomControls: Boolean = true,
        modifier: UiModifier = UiModifier.None,
        block: (BoardBuilder.() -> Unit)? = null
    ) {
        val builtLayers = if (block != null) {
            val builder = BoardBuilder(layers.toMutableList()).apply(block)
            builder.buildLayers()
        } else {
            layers
        }
        add(
            BoardNode(
                id = id,
                backgroundAsset = backgroundAsset,
                viewportConfig = viewportConfig,
                layers = builtLayers,
                selectedLayerIds = selectedLayerIds,
                activeTool = activeTool,
                clearRevision = clearRevision,
                showToolbar = showToolbar,
                showZoomControls = showZoomControls,
                modifier = modifier
            )
        )
    }

    fun badge(
        text: String,
        id: String = generateNodeId("badge"),
        color: SemanticColor = SemanticColor.PRIMARY,
        modifier: UiModifier = UiModifier.None
    ) {
        add(BadgeNode(id = id, text = text, color = color, modifier = modifier))
    }

    fun divider(
        id: String = generateNodeId("divider"),
        modifier: UiModifier = UiModifier.None
    ) {
        add(DividerNode(id = id, modifier = modifier))
    }

    fun box(
        id: String = generateNodeId("box"),
        modifier: UiModifier = UiModifier.None,
        block: BoxBuilder.() -> Unit
    ) {
        val builder = BoxBuilder(id, modifier).apply(block)
        add(builder.build())
    }

    fun scaffold(
        id: String = generateNodeId("scaffold"),
        modifier: UiModifier = UiModifier.None,
        block: ScaffoldBuilder.() -> Unit
    ) {
        val builder = ScaffoldBuilder(id, modifier).apply(block)
        add(builder.build())
    }

    fun floatingPanel(
        id: String = generateNodeId("floating"),
        anchor: FloatingAnchor = FloatingAnchor.TOP_END,
        title: String? = null,
        isCollapsible: Boolean = true,
        isCollapsed: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: FloatingPanelBuilder.() -> Unit
    ) {
        val builder = FloatingPanelBuilder(id, anchor, title, isCollapsible, isCollapsed, modifier).apply(block)
        add(builder.build())
    }

    fun sidebar(
        id: String = generateNodeId("sidebar"),
        attachPoint: SidebarAttachPoint = SidebarAttachPoint.LEFT,
        title: String? = null,
        isCollapsible: Boolean = true,
        isCollapsed: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: SidebarBuilder.() -> Unit
    ) {
        val builder = SidebarBuilder(id, attachPoint, title, isCollapsible, isCollapsed, modifier).apply(block)
        add(builder.build())
    }

    internal fun buildChildren(): List<UiNode> = children.toList()

    companion object {
        private var counter = 0
        internal fun generateNodeId(prefix: String): String = "${prefix}_${++counter}"
    }
}

class BoxBuilder(
    val id: String,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): BoxNode = BoxNode(id = id, children = children.toList(), modifier = modifier)
}

class ColumnBuilder(
    val id: String,
    val spacing: SpacingSize,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): ColumnNode = ColumnNode(id = id, spacing = spacing, modifier = modifier, children = children.toList())
}

class RowBuilder(
    val id: String,
    val spacing: SpacingSize,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): RowNode = RowNode(id = id, spacing = spacing, modifier = modifier, children = children.toList())
}

class ScrollableRowBuilder(
    val id: String,
    val spacing: SpacingSize,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): ScrollableRowNode = ScrollableRowNode(id = id, spacing = spacing, modifier = modifier, children = children.toList())
}

class CardBuilder(
    val id: String,
    val title: String?,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): CardNode = CardNode(id = id, title = title, modifier = modifier, children = children.toList())
}

class ScrollableColumnBuilder(
    val id: String,
    val spacing: SpacingSize,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): ScrollableColumnNode = ScrollableColumnNode(id = id, spacing = spacing, modifier = modifier, children = children.toList())
}

class FloatingPanelBuilder(
    val id: String,
    val anchor: FloatingAnchor,
    val title: String?,
    val isCollapsible: Boolean,
    val isCollapsed: Boolean,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): FloatingPanelNode = FloatingPanelNode(
        id = id,
        anchor = anchor,
        title = title,
        isCollapsible = isCollapsible,
        isCollapsed = isCollapsed,
        modifier = modifier,
        children = children.toList()
    )
}

class SidebarBuilder(
    val id: String,
    val attachPoint: SidebarAttachPoint,
    val title: String?,
    val isCollapsible: Boolean,
    val isCollapsed: Boolean,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): SidebarNode = SidebarNode(
        id = id,
        attachPoint = attachPoint,
        title = title,
        isCollapsible = isCollapsible,
        isCollapsed = isCollapsed,
        modifier = modifier,
        children = children.toList()
    )
}

@UiDslMarker
class ScaffoldBuilder(
    val id: String,
    val modifier: UiModifier
) {
    private var contentNode: UiNode? = null
    private var leftSidebarNode: UiNode? = null
    private var rightSidebarNode: UiNode? = null
    private var topBarNode: UiNode? = null
    private var bottomBarNode: UiNode? = null
    private val floatingPanels = mutableListOf<FloatingPanelNode>()

    fun content(node: UiNode) {
        contentNode = node
    }

    fun content(block: UiContainerBuilder.() -> Unit) {
        val container = object : UiContainerBuilder() {}
        container.block()
        contentNode = when {
            container.buildChildren().isEmpty() -> ColumnNode(id = "${id}_content_empty")
            container.buildChildren().size == 1 -> container.buildChildren().first()
            else -> ColumnNode(id = "${id}_content", children = container.buildChildren())
        }
    }

    fun leftSidebar(node: UiNode) {
        leftSidebarNode = node
    }

    fun leftSidebar(
        id: String = "${this.id}_left",
        title: String? = null,
        isCollapsible: Boolean = true,
        isCollapsed: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: SidebarBuilder.() -> Unit
    ) {
        leftSidebarNode = SidebarBuilder(id, SidebarAttachPoint.LEFT, title, isCollapsible, isCollapsed, modifier).apply(block).build()
    }

    fun rightSidebar(node: UiNode) {
        rightSidebarNode = node
    }

    fun rightSidebar(
        id: String = "${this.id}_right",
        title: String? = null,
        isCollapsible: Boolean = true,
        isCollapsed: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: SidebarBuilder.() -> Unit
    ) {
        rightSidebarNode = SidebarBuilder(id, SidebarAttachPoint.RIGHT, title, isCollapsible, isCollapsed, modifier).apply(block).build()
    }

    fun sidebar(
        id: String = "${this.id}_sidebar",
        attachPoint: SidebarAttachPoint = SidebarAttachPoint.LEFT,
        title: String? = null,
        isCollapsible: Boolean = true,
        isCollapsed: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: SidebarBuilder.() -> Unit
    ) {
        val node = SidebarBuilder(id, attachPoint, title, isCollapsible, isCollapsed, modifier).apply(block).build()
        when (attachPoint) {
            SidebarAttachPoint.LEFT -> leftSidebarNode = node
            SidebarAttachPoint.RIGHT -> rightSidebarNode = node
            SidebarAttachPoint.TOP -> topBarNode = node
            SidebarAttachPoint.BOTTOM -> bottomBarNode = node
        }
    }

    fun sidebar(node: SidebarNode) {
        when (node.attachPoint) {
            SidebarAttachPoint.LEFT -> leftSidebarNode = node
            SidebarAttachPoint.RIGHT -> rightSidebarNode = node
            SidebarAttachPoint.TOP -> topBarNode = node
            SidebarAttachPoint.BOTTOM -> bottomBarNode = node
        }
    }

    fun topBar(node: UiNode) {
        topBarNode = node
    }

    fun topBar(block: UiContainerBuilder.() -> Unit) {
        val container = object : UiContainerBuilder() {}
        container.block()
        topBarNode = when {
            container.buildChildren().isEmpty() -> null
            container.buildChildren().size == 1 -> container.buildChildren().first()
            else -> RowNode(id = "${id}_top", children = container.buildChildren())
        }
    }

    fun bottomBar(node: UiNode) {
        bottomBarNode = node
    }

    fun bottomBar(block: UiContainerBuilder.() -> Unit) {
        val container = object : UiContainerBuilder() {}
        container.block()
        bottomBarNode = when {
            container.buildChildren().isEmpty() -> null
            container.buildChildren().size == 1 -> container.buildChildren().first()
            else -> RowNode(id = "${id}_bottom", children = container.buildChildren())
        }
    }

    fun floatingPanel(panel: FloatingPanelNode) {
        floatingPanels.add(panel)
    }

    fun floatingPanel(
        id: String = "${this.id}_floating_${floatingPanels.size + 1}",
        anchor: FloatingAnchor = FloatingAnchor.TOP_END,
        title: String? = null,
        isCollapsible: Boolean = true,
        isCollapsed: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: FloatingPanelBuilder.() -> Unit
    ) {
        val builder = FloatingPanelBuilder(id, anchor, title, isCollapsible, isCollapsed, modifier).apply(block)
        floatingPanels.add(builder.build())
    }

    fun build(): ScaffoldNode {
        val content = contentNode ?: ColumnNode(id = "${id}_empty_content")
        return ScaffoldNode(
            id = id,
            content = content,
            leftSidebar = leftSidebarNode,
            rightSidebar = rightSidebarNode,
            topBar = topBarNode,
            bottomBar = bottomBarNode,
            floatingPanels = floatingPanels.toList(),
            modifier = modifier
        )
    }
}

/**
 * Declarative builder for spatial [BoardLayer] elements inside a [BoardNode].
 */
@UiDslMarker
class BoardBuilder(
    private val layers: MutableList<BoardLayer> = mutableListOf()
) {
    fun layer(layer: BoardLayer) {
        layers.add(layer)
    }

    fun imageLayer(
        id: String = UiContainerBuilder.generateNodeId("img_layer"),
        asset: AssetReference,
        position: NormalizedPoint = NormalizedPoint(0f, 0f),
        size: NormalizedSize = NormalizedSize(1f, 1f),
        opacity: Float = 1f,
        contentDescription: String? = null,
        zIndex: Int = layers.size,
        visible: Boolean = true,
        locked: Boolean = false,
        selectable: Boolean = true,
        draggable: Boolean = true,
        resizable: Boolean = true
    ) {
        layers.add(
            BoardLayer.ImageLayer(
                id = id,
                asset = asset,
                position = position,
                size = size,
                opacity = opacity,
                contentDescription = contentDescription,
                zIndex = zIndex,
                visible = visible,
                locked = locked,
                selectable = selectable,
                draggable = draggable,
                resizable = resizable
            )
        )
    }

    fun textLayer(
        id: String = UiContainerBuilder.generateNodeId("txt_layer"),
        text: String,
        position: NormalizedPoint,
        size: NormalizedSize? = null,
        color: SemanticColor = SemanticColor.PRIMARY,
        backgroundColor: SemanticColor? = null,
        borderColor: SemanticColor? = null,
        role: TypographyRole = TypographyRole.BODY_LARGE,
        zIndex: Int = layers.size,
        visible: Boolean = true,
        locked: Boolean = false,
        selectable: Boolean = true,
        draggable: Boolean = true,
        editable: Boolean = true
    ) {
        layers.add(
            BoardLayer.TextLayer(
                id = id,
                text = text,
                position = position,
                size = size,
                color = color,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                role = role,
                zIndex = zIndex,
                visible = visible,
                locked = locked,
                selectable = selectable,
                draggable = draggable,
                editable = editable
            )
        )
    }

    fun shapeLayer(
        id: String = UiContainerBuilder.generateNodeId("shape_layer"),
        shapeType: BoardShapeType = BoardShapeType.ROUNDED_RECT,
        position: NormalizedPoint,
        size: NormalizedSize,
        strokeColor: SemanticColor = SemanticColor.PRIMARY,
        fillColor: SemanticColor? = null,
        strokeWidth: Float = 2f,
        text: String? = null,
        zIndex: Int = layers.size,
        visible: Boolean = true,
        locked: Boolean = false,
        selectable: Boolean = true,
        draggable: Boolean = true,
        resizable: Boolean = true
    ) {
        layers.add(
            BoardLayer.ShapeLayer(
                id = id,
                shapeType = shapeType,
                position = position,
                size = size,
                strokeColor = strokeColor,
                fillColor = fillColor,
                strokeWidth = strokeWidth,
                text = text,
                zIndex = zIndex,
                visible = visible,
                locked = locked,
                selectable = selectable,
                draggable = draggable,
                resizable = resizable
            )
        )
    }

    fun pathLayer(
        id: String = UiContainerBuilder.generateNodeId("path_layer"),
        path: VectorPathData,
        zIndex: Int = layers.size,
        visible: Boolean = true,
        locked: Boolean = false,
        selectable: Boolean = false
    ) {
        layers.add(
            BoardLayer.PathLayer(
                id = id,
                path = path,
                zIndex = zIndex,
                visible = visible,
                locked = locked,
                selectable = selectable
            )
        )
    }

    fun componentLayer(
        id: String = UiContainerBuilder.generateNodeId("comp_layer"),
        position: NormalizedPoint,
        size: NormalizedSize? = null,
        zIndex: Int = layers.size,
        visible: Boolean = true,
        locked: Boolean = false,
        selectable: Boolean = true,
        draggable: Boolean = true,
        resizable: Boolean = false,
        block: UiContainerBuilder.() -> Unit
    ) {
        val container = object : UiContainerBuilder() {}
        container.block()
        val children = container.buildChildren()
        val contentNode = when {
            children.isEmpty() -> ColumnNode(id = "${id}_empty")
            children.size == 1 -> children.first()
            else -> ColumnNode(id = "${id}_col", children = children)
        }
        layers.add(
            BoardLayer.ComponentLayer(
                id = id,
                position = position,
                size = size,
                content = contentNode,
                zIndex = zIndex,
                visible = visible,
                locked = locked,
                selectable = selectable,
                draggable = draggable,
                resizable = resizable
            )
        )
    }

    internal fun buildLayers(): List<BoardLayer> = layers.toList()
}

/**
 * Top-level builder creating a complete [UiNodeTree].
 */
fun uiTree(
    version: Int = 1,
    rootBuilder: UiContainerBuilder.() -> Unit
): UiNodeTree {
    val rootContainer = object : UiContainerBuilder() {
        fun buildRoot(): UiNode {
            return when {
                children.isEmpty() -> ColumnNode(id = "root_empty")
                children.size == 1 -> children.first()
                else -> ColumnNode(id = "root_col", children = children.toList())
            }
        }
    }
    rootContainer.rootBuilder()
    return UiNodeTree(root = rootContainer.buildRoot(), version = version)
}
