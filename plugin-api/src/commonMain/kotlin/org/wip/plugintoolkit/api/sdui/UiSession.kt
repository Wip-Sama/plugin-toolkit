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
import kotlin.jvm.JvmOverloads

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
     * Reactive stream of this plugin's persisted settings merged over manifest defaults.
     * Emits whenever settings are updated in the host settings view or via [updateSetting],
     * [updateSettings], or [clearSetting].
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

    /**
     * Prompt the user via a native file dialog to pick a single file.
     * On selection, the host grants read access to that file for the active session's [hostFileSystem]
     * and local image previews.
     */
    suspend fun pickFile(
        title: String? = null,
        allowedExtensions: List<String> = emptyList()
    ): String? = null

    /**
     * Prompt the user via a native file dialog to select one or more files.
     * Selected file paths are granted read access on the active session's [hostFileSystem].
     */
    suspend fun pickFiles(
        title: String? = null,
        allowedExtensions: List<String> = emptyList()
    ): List<String> = emptyList()

    /**
     * Prompt the user via a native folder dialog to choose a directory.
     * Read/write access to the chosen directory subtree is granted on the active session's [hostFileSystem].
     */
    suspend fun pickDirectory(title: String? = null): String? = null

    /**
     * Prompt the user via a native Save dialog and persist [bytes] to the chosen location.
     *
     * @return Saved absolute file path, or null if cancelled.
     */
    suspend fun saveFile(
        defaultName: String,
        extension: String,
        bytes: ByteArray
    ): String? = null

    /**
     * Navigate to another `@InteractiveTool` capability exposed by this plugin (or reopen the current tool),
     * optionally passing [initialParameters] to pre-populate the target session.
     */
    fun openInteractiveTool(
        capabilityName: String,
        initialParameters: Map<String, JsonElement> = emptyMap()
    ) {}
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
        context.settingsFlow,
        context.locksFlow
    ) { currentState, _, _, _ ->
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
        reorderable: Boolean = false,
        block: ColumnBuilder.() -> Unit
    ) {
        val builder = ColumnBuilder(id, spacing, modifier, reorderable).apply(block)
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
        surfaceRole: SemanticColor? = null,
        outlined: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: CardBuilder.() -> Unit
    ) {
        val builder = CardBuilder(id, title, surfaceRole, outlined, modifier).apply(block)
        add(builder.build())
    }

    fun scrollableColumn(
        id: String = generateNodeId("scroll"),
        spacing: SpacingSize = SpacingSize.NONE,
        modifier: UiModifier = UiModifier.None,
        reorderable: Boolean = false,
        block: ScrollableColumnBuilder.() -> Unit
    ) {
        val builder = ScrollableColumnBuilder(id, spacing, modifier, reorderable).apply(block)
        add(builder.build())
    }

    fun grid(
        id: String = generateNodeId("grid"),
        columns: Int? = null,
        minItemWidthDp: Float = 156f,
        spacing: SpacingSize = SpacingSize.SMALL,
        reorderable: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: GridBuilder.() -> Unit
    ) {
        val builder = GridBuilder(id, columns, minItemWidthDp, spacing, reorderable, modifier).apply(block)
        add(builder.build())
    }

    @JvmOverloads
    fun text(
        text: String,
        id: String = generateNodeId("txt"),
        role: TypographyRole = TypographyRole.BODY_MEDIUM,
        color: SemanticColor? = null,
        modifier: UiModifier = UiModifier.None,
        selectable: Boolean = false,
        maxLines: Int? = null,
        overflowEllipsis: Boolean = false
    ) {
        add(
            TextNode(
                id = id,
                text = text,
                role = role,
                color = color,
                modifier = modifier,
                selectable = selectable,
                maxLines = maxLines,
                overflowEllipsis = overflowEllipsis
            )
        )
    }

    fun button(
        label: String,
        id: String = generateNodeId("btn"),
        action: String? = null,
        isPrimary: Boolean = true,
        isDestructive: Boolean = false,
        isTonal: Boolean = false,
        iconName: String? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            ButtonNode(
                id = id,
                label = label,
                action = action,
                isPrimary = isPrimary,
                isDestructive = isDestructive,
                modifier = modifier,
                isTonal = isTonal,
                iconName = iconName
            )
        )
    }

    /**
     * Builds a connected Material 3 Expressive pill [ButtonGroupNode].
     */
    fun buttonGroup(
        id: String = generateNodeId("btn_group"),
        selectionMode: Boolean = true,
        modifier: UiModifier = UiModifier.None,
        block: ButtonGroupBuilder.() -> Unit
    ) {
        val builder = ButtonGroupBuilder().apply(block)
        add(
            ButtonGroupNode(
                id = id,
                items = builder.buildItems(),
                selectionMode = selectionMode,
                modifier = modifier
            )
        )
    }

    /**
     * Builds a Material 3 Expressive [SplitButtonNode] with a primary pill button and a
     * connected chevron popup menu.
     */
    fun splitButton(
        label: String,
        primaryAction: String,
        id: String = generateNodeId("split_btn"),
        iconName: String? = null,
        isTonal: Boolean = false,
        menuTooltip: String? = null,
        modifier: UiModifier = UiModifier.None,
        block: SplitButtonMenuBuilder.() -> Unit
    ) {
        val builder = SplitButtonMenuBuilder().apply(block)
        add(
            SplitButtonNode(
                id = id,
                label = label,
                primaryAction = primaryAction,
                iconName = iconName,
                isTonal = isTonal,
                menuItems = builder.buildItems(),
                menuTooltip = menuTooltip,
                modifier = modifier
            )
        )
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
        isSecret: Boolean = false,
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
                modifier = modifier,
                isSecret = isSecret
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

    fun radioGroup(
        options: List<RadioOptionItem>,
        id: String = generateNodeId("radio"),
        selectedKey: String? = null,
        label: String? = null,
        horizontal: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            RadioGroupNode(
                id = id,
                options = options,
                selectedKey = selectedKey,
                label = label,
                horizontal = horizontal,
                modifier = modifier
            )
        )
    }

    fun radioGroup(
        id: String = generateNodeId("radio"),
        selectedKey: String? = null,
        label: String? = null,
        horizontal: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: RadioGroupBuilder.() -> Unit
    ) {
        val builder = RadioGroupBuilder().apply(block)
        add(
            RadioGroupNode(
                id = id,
                options = builder.buildOptions(),
                selectedKey = selectedKey,
                label = label,
                horizontal = horizontal,
                modifier = modifier
            )
        )
    }

    fun numberInput(
        id: String = generateNodeId("num"),
        value: Double = 0.0,
        min: Double? = null,
        max: Double? = null,
        step: Double = 1.0,
        isDecimal: Boolean = false,
        unitSuffix: String? = null,
        label: String? = null,
        placeholder: String? = null,
        revision: Int = 0,
        forceOverwrite: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            NumberInputNode(
                id = id,
                value = value,
                min = min,
                max = max,
                step = step,
                isDecimal = isDecimal,
                unitSuffix = unitSuffix,
                label = label,
                placeholder = placeholder,
                revision = revision,
                forceOverwrite = forceOverwrite,
                modifier = modifier
            )
        )
    }

    fun searchBar(
        id: String = generateNodeId("search"),
        query: String = "",
        suggestions: List<String> = emptyList(),
        placeholder: String? = null,
        label: String? = null,
        showClearButton: Boolean = true,
        action: String? = null,
        debounceMs: Long = 150L,
        revision: Int = 0,
        forceOverwrite: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            SearchBarNode(
                id = id,
                query = query,
                suggestions = suggestions,
                placeholder = placeholder,
                label = label,
                showClearButton = showClearButton,
                action = action,
                debounceMs = debounceMs,
                revision = revision,
                forceOverwrite = forceOverwrite,
                modifier = modifier
            )
        )
    }

    @JvmOverloads
    fun image(
        asset: AssetReference,
        id: String = generateNodeId("img"),
        contentDescription: String? = null,
        contentScale: ImageScale = ImageScale.FIT,
        clickable: Boolean = false,
        action: String? = null,
        selected: Boolean = false,
        badgeText: String? = null,
        modifier: UiModifier = UiModifier.None,
        zoomable: Boolean = false
    ) {
        add(
            ImageNode(
                id = id,
                asset = asset,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                clickable = clickable,
                action = action,
                selected = selected,
                badgeText = badgeText,
                zoomable = zoomable
            )
        )
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

    @JvmOverloads
    fun progressBar(
        id: String = generateNodeId("progress"),
        progress: Float? = null,
        secondaryProgress: Float? = null,
        label: String? = null,
        statusText: String? = null,
        isCircular: Boolean = false,
        color: SemanticColor = SemanticColor.PRIMARY,
        modifier: UiModifier = UiModifier.None,
        style: ProgressBarStyle = ProgressBarStyle.STANDARD
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
                modifier = modifier,
                style = style
            )
        )
    }

    fun loadingIndicator(
        label: String? = null,
        id: String = generateNodeId("loading"),
        contained: Boolean = false,
        progress: Float? = null,
        color: SemanticColor = SemanticColor.PRIMARY,
        size: ControlSize = ControlSize.MEDIUM,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            LoadingIndicatorNode(
                id = id,
                label = label,
                contained = contained,
                progress = progress,
                color = color,
                size = size,
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
        leadingAsset: AssetReference? = null,
        badgeText: String? = null,
        badgeColor: SemanticColor? = null,
        selected: Boolean = false,
        selectable: Boolean = false,
        action: String? = null,
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
                modifier = modifier,
                leadingAsset = leadingAsset,
                action = action
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
        resizable: Boolean = true,
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
                modifier = modifier,
                resizable = resizable
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
        compact: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: FloatingPanelBuilder.() -> Unit
    ) {
        val builder = FloatingPanelBuilder(id, anchor, title, isCollapsible, isCollapsed, compact, modifier).apply(block)
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

    fun accordion(
        title: String,
        id: String = generateNodeId("accordion"),
        subtitle: String? = null,
        expanded: Boolean = false,
        leadingIcon: String? = null,
        badgeText: String? = null,
        badgeColor: SemanticColor? = null,
        modifier: UiModifier = UiModifier.None,
        block: AccordionBuilder.() -> Unit
    ) {
        val builder = AccordionBuilder(
            id = id,
            title = title,
            subtitle = subtitle,
            expanded = expanded,
            leadingIcon = leadingIcon,
            badgeText = badgeText,
            badgeColor = badgeColor,
            modifier = modifier
        ).apply(block)
        add(builder.build())
    }

    fun codeBlock(
        code: String,
        id: String = generateNodeId("code"),
        language: String? = null,
        title: String? = null,
        showLineNumbers: Boolean = true,
        wrapLines: Boolean = false,
        showCopyButton: Boolean = true,
        maxHeightDp: Int? = null,
        modifier: UiModifier = UiModifier.None
    ) {
        add(
            CodeBlockNode(
                id = id,
                code = code,
                language = language,
                title = title,
                showLineNumbers = showLineNumbers,
                wrapLines = wrapLines,
                showCopyButton = showCopyButton,
                maxHeightDp = maxHeightDp,
                modifier = modifier
            )
        )
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
    val modifier: UiModifier,
    val reorderable: Boolean = false
) : UiContainerBuilder() {
    fun build(): ColumnNode = ColumnNode(
        id = id,
        spacing = spacing,
        modifier = modifier,
        reorderable = reorderable,
        children = children.toList()
    )
}

class RowBuilder(
    val id: String,
    val spacing: SpacingSize,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): RowNode = RowNode(id, children.toList(), spacing, modifier)
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
    val surfaceRole: SemanticColor?,
    val outlined: Boolean,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): CardNode = CardNode(
        id = id,
        title = title,
        children = children.toList(),
        modifier = modifier,
        surfaceRole = surfaceRole,
        outlined = outlined
    )
}

@UiDslMarker
class ButtonGroupBuilder {
    private val items = mutableListOf<ButtonGroupItem>()

    fun item(
        key: String,
        label: String,
        action: String? = null,
        iconName: String? = null,
        selected: Boolean = false,
        color: SemanticColor? = null,
        enabled: Boolean = true,
        tooltip: String? = null
    ) {
        items.add(
            ButtonGroupItem(
                key = key,
                label = label,
                action = action,
                iconName = iconName,
                selected = selected,
                color = color,
                enabled = enabled,
                tooltip = tooltip
            )
        )
    }

    internal fun buildItems(): List<ButtonGroupItem> = items.toList()
}

@UiDslMarker
open class ActionMenuBuilder {
    private val items = mutableListOf<ActionMenuItem>()

    fun item(
        key: String,
        label: String,
        action: String? = null,
        iconName: String? = null,
        isDestructive: Boolean = false,
        enabled: Boolean = true,
        startsSection: Boolean = false
    ) {
        items.add(
            ActionMenuItem(
                key = key,
                label = label,
                action = action,
                iconName = iconName,
                isDestructive = isDestructive,
                enabled = enabled,
                startsSection = startsSection
            )
        )
    }

    internal fun buildItems(): List<ActionMenuItem> = items.toList()
}

@UiDslMarker
class SplitButtonMenuBuilder : ActionMenuBuilder()

@UiDslMarker
class RadioGroupBuilder {
    private val options = mutableListOf<RadioOptionItem>()

    fun option(
        key: String,
        label: String,
        subtitle: String? = null,
        enabled: Boolean = true
    ) {
        options.add(
            RadioOptionItem(
                key = key,
                label = label,
                subtitle = subtitle,
                enabled = enabled
            )
        )
    }

    internal fun buildOptions(): List<RadioOptionItem> = options.toList()
}

class AccordionBuilder(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val expanded: Boolean = false,
    val leadingIcon: String? = null,
    val badgeText: String? = null,
    val badgeColor: SemanticColor? = null,
    val modifier: UiModifier = UiModifier.None
) : UiContainerBuilder() {
    fun build(): AccordionNode = AccordionNode(
        id = id,
        title = title,
        subtitle = subtitle,
        expanded = expanded,
        leadingIcon = leadingIcon,
        badgeText = badgeText,
        badgeColor = badgeColor,
        children = children.toList(),
        modifier = modifier
    )
}

class ScrollableColumnBuilder(
    val id: String,
    val spacing: SpacingSize,
    val modifier: UiModifier,
    val reorderable: Boolean = false
) : UiContainerBuilder() {
    fun build(): ScrollableColumnNode = ScrollableColumnNode(
        id = id,
        spacing = spacing,
        modifier = modifier,
        reorderable = reorderable,
        children = children.toList()
    )
}

class GridBuilder(
    val id: String,
    val columns: Int?,
    val minItemWidthDp: Float,
    val spacing: SpacingSize,
    val reorderable: Boolean,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): GridNode = GridNode(
        id = id,
        columns = columns,
        minItemWidthDp = minItemWidthDp,
        spacing = spacing,
        reorderable = reorderable,
        children = children.toList(),
        modifier = modifier
    )
}

class FloatingPanelBuilder(
    val id: String,
    val anchor: FloatingAnchor,
    val title: String?,
    val isCollapsible: Boolean,
    val isCollapsed: Boolean,
    val compact: Boolean,
    val modifier: UiModifier
) : UiContainerBuilder() {
    fun build(): FloatingPanelNode = FloatingPanelNode(
        id = id,
        anchor = anchor,
        title = title,
        isCollapsible = isCollapsible,
        isCollapsed = isCollapsed,
        modifier = modifier,
        children = children.toList(),
        compact = compact
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
        compact: Boolean = false,
        modifier: UiModifier = UiModifier.None,
        block: FloatingPanelBuilder.() -> Unit
    ) {
        val builder = FloatingPanelBuilder(id, anchor, title, isCollapsible, isCollapsed, compact, modifier).apply(block)
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
