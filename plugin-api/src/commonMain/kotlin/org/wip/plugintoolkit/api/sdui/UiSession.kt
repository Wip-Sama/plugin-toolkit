package org.wip.plugintoolkit.api.sdui

import kotlinx.coroutines.flow.Flow
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginResponse

/**
 * Interface implemented by plugins that provide reactive, server-driven user interfaces.
 */
interface InteractiveUiProcessor {
    /**
     * Open a long-lived interactive UI session.
     *
     * @param context Execution context providing plugin services and session completion methods.
     * @param events Inbound stream of user interaction events (clicks, text changes, committed vector paths).
     * @return Outbound reactive stream of UI node trees rendered in the host sandbox.
     */
    fun openUiSession(context: UiSessionContext, events: Flow<UiEvent>): Flow<UiNodeTree>
}

/**
 * Context provided to an interactive UI session, extending PluginContext with lifecycle termination controls.
 */
interface UiSessionContext : PluginContext {
    /**
     * Terminate the interactive session successfully, returning a [PluginResponse] to the caller.
     * Completes the host job and resumes downstream connected flow nodes if running in a Flow.
     */
    fun completeSession(response: PluginResponse)

    /**
     * Terminate the interactive session with a cancellation or failure reason.
     */
    fun cancelSession(reason: String)
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
        isPrimary: Boolean = true,
        isDestructive: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(ButtonNode(id = id, label = label, isPrimary = isPrimary, isDestructive = isDestructive, modifier = modifier))
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

    fun board(
        id: String = generateNodeId("board"),
        backgroundAsset: AssetReference? = null,
        layers: List<BoardLayer> = emptyList(),
        activeTool: BoardTool = BoardTool.Brush(),
        clearRevision: Int = 0,
        showToolbar: Boolean = false,
        modifier: UiModifier = UiModifier.None
    ) {
        add(BoardNode(id = id, backgroundAsset = backgroundAsset, layers = layers, activeTool = activeTool, clearRevision = clearRevision, showToolbar = showToolbar, modifier = modifier))
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
