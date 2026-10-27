package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.sdui.PluginCustomUiCard
import org.wip.plugintoolkit.features.plugin.viewmodel.InteractiveToolItem
import org.wip.plugintoolkit.features.plugin.viewmodel.InteractiveToolsViewModel
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close_session
import plugintoolkit.composeapp.generated.resources.action_launch_session
import plugintoolkit.composeapp.generated.resources.action_restart_session
import plugintoolkit.composeapp.generated.resources.interactive_tools_empty
import plugintoolkit.composeapp.generated.resources.interactive_tools_hint
import plugintoolkit.composeapp.generated.resources.interactive_tools_title
import plugintoolkit.composeapp.generated.resources.search_capabilities_placeholder
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dedicated host screen for running interactive SDUI plugin tools.
 *
 * Provides a dedicated workspace separate from automated capabilities and visual flows,
 * offering a live bidirectional GUI viewport with session lifecycle management.
 */
@Composable
fun InteractiveToolsScreen(
    initialPluginId: String? = null,
    initialCapabilityName: String? = null,
    viewModel: InteractiveToolsViewModel = koinInject()
) {
    val tools by viewModel.interactiveTools.collectAsState()
    val selectedTool by viewModel.selectedTool.collectAsState()
    val activeSessionHandle by viewModel.activeSessionHandle.collectAsState()

    // Handle initial navigation target
    LaunchedEffect(initialPluginId, initialCapabilityName, tools) {
        if (initialPluginId != null && selectedTool == null && tools.isNotEmpty()) {
            val matching = tools.find {
                val manifest = it.plugin.getManifest().getOrNull()
                manifest?.plugin?.id == initialPluginId &&
                    (initialCapabilityName == null || it.capability.name == initialCapabilityName)
            } ?: tools.firstOrNull()
            matching?.let { viewModel.selectTool(it) }
        }
    }

    val loadedPlugins = remember(tools) {
        org.wip.plugintoolkit.features.plugin.logic.PluginLoader.getPlugins()
    }
    val pluginsWithTools = remember(tools) {
        tools.map { it.plugin }.distinctBy { it.getManifest().getOrNull()?.plugin?.id }
    }

    var selectedPluginId by remember {
        mutableStateOf(
            initialPluginId
                ?: selectedTool?.plugin?.getManifest()?.getOrNull()?.plugin?.id
                ?: if (pluginsWithTools.size == 1) pluginsWithTools.first().getManifest().getOrNull()?.plugin?.id else null
        )
    }

    LaunchedEffect(selectedTool) {
        selectedTool?.plugin?.getManifest()?.getOrNull()?.plugin?.id?.let { id ->
            selectedPluginId = id
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DirectExecutionSidebar(
            loadedPlugins = loadedPlugins,
            selectedPluginId = selectedPluginId,
            onPluginSelected = { pluginId ->
                selectedPluginId = pluginId
                val matching = tools.find { it.plugin.getManifest().getOrNull()?.plugin?.id == pluginId }
                if (matching != null && selectedTool == null) {
                    viewModel.selectTool(matching)
                }
            },
            onBackToPlugins = {
                selectedPluginId = null
            },
            selectedCapability = if (selectedTool?.plugin?.getManifest()?.getOrNull()?.plugin?.id == selectedPluginId) {
                selectedTool?.capability
            } else null,
            onCapabilitySelected = { capability ->
                val matching = tools.find {
                    it.plugin.getManifest().getOrNull()?.plugin?.id == selectedPluginId &&
                        it.capability.name == capability.name
                }
                if (matching != null) {
                    viewModel.selectTool(matching)
                }
            },
            isInteractiveOnly = true
        )

        // Right Pane: Full Interactive Workspace (covers the whole section)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val currentTool = selectedTool
            if (currentTool == null) {
                if (tools.isEmpty()) {
                    EmptyState(stringResource(Res.string.interactive_tools_empty))
                } else {
                    EmptyState(stringResource(Res.string.interactive_tools_hint))
                }
            } else {
                val handle = activeSessionHandle
                if (handle != null && handle.isActive) {
                    // Full section addressable viewport
                    PluginCustomUiCard(
                        handle = handle,
                        modifier = Modifier.fillMaxSize(),
                        onClose = { viewModel.closeSession() }
                    )

                    // Hidden floating pill bar with low presence that animates on hover (as in Image 4)
                    val pluginMeta = currentTool.plugin.getManifest().getOrNull()?.plugin
                    val pluginLabel = if (pluginMeta != null) "${pluginMeta.name} v${pluginMeta.version}" else ""

                    InteractiveFloatingBottomBar(
                        toolName = currentTool.capability.name,
                        pluginName = pluginLabel,
                        onRestart = { viewModel.restartSession() },
                        onClose = { viewModel.closeSession() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    // Centered idle state launcher card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ToolkitTheme.spacing.large),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .width(400.dp)
                                .border(
                                    width = ToolkitTheme.dimensions.borderThin,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ToolkitTheme.spacing.extraLarge),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                                )
                                Text(
                                    text = currentTool.capability.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentTool.capability.description ?: stringResource(Res.string.interactive_tools_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.startSession(currentTool) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                    Spacer(Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text(stringResource(Res.string.action_launch_session))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SPATIAL_SPRING_STIFFNESS = 900f
private const val EFFECTS_SPRING_STIFFNESS = 900f
private val COLLAPSED_CORNER_RADIUS = 14.dp
private val EXPANDED_CORNER_RADIUS = 28.dp
private val COLLAPSED_BOTTOM_CORNER_RADIUS = 0.dp
private val EXPANDED_BOTTOM_CORNER_RADIUS = 28.dp
private val EXPANDED_BOTTOM_OFFSET = 20.dp
private val COLLAPSED_BOTTOM_OFFSET = 0.dp
private val COLLAPSED_ICON_CONTAINER_SIZE = 20.dp
private val EXPANDED_ICON_CONTAINER_SIZE = 36.dp
private const val ACTION_ENTER_EXIT_SCALE = 0f
private const val ARROW_ENTER_EXIT_SCALE = 0f
private val HOVER_EXIT_DEBOUNCE_MS = 120L.milliseconds
private const val COLLAPSED_HANDLE_ALPHA = 0.7f
private const val EXPANDED_HANDLE_ALPHA = 0.35f
private val DOCKED_HANDLE_WIDTH = 36.dp
private val DOCKED_HANDLE_HEIGHT = 4.dp
private val ACTION_DIVIDER_HEIGHT = 28.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InteractiveFloatingBottomBar(
    toolName: String,
    pluginName: String,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }
    val hoverScope = rememberCoroutineScope()
    var exitJob by remember { mutableStateOf<Job?>(null) }

    val transition = updateTransition(targetState = isHovered, label = "floatingBottomBarTransition")

    // Expressive bouncy spring when expanding on hover
    val spatialSpringDp = remember {
        spring<Dp>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = SPATIAL_SPRING_STIFFNESS
        )
    }
    // Critically damped (NoBouncy) spring for collapse to dock immediately without overshoot
    val spatialSpringDpNoBouncy = remember {
        spring<Dp>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = SPATIAL_SPRING_STIFFNESS
        )
    }
    // Critically damped (NoBouncy) spring for layout bounds in AnimatedVisibility
    // so exit animations complete immediately without hanging on sub-pixel oscillation.
    val layoutSizeSpring = remember {
        spring<IntSize>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = SPATIAL_SPRING_STIFFNESS,
            visibilityThreshold = IntSize(1, 1)
        )
    }
    val spatialSpringFloat = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = SPATIAL_SPRING_STIFFNESS
        )
    }
    val effectsSpringFloat = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = EFFECTS_SPRING_STIFFNESS
        )
    }
    val effectsSpringColor = remember {
        spring<Color>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = EFFECTS_SPRING_STIFFNESS
        )
    }

    val bottomOffset by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "bottomOffset"
    ) { hovered ->
        if (hovered) EXPANDED_BOTTOM_OFFSET else COLLAPSED_BOTTOM_OFFSET
    }
    val cornerRadius by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "cornerRadius"
    ) { hovered ->
        if (hovered) EXPANDED_CORNER_RADIUS else COLLAPSED_CORNER_RADIUS
    }
    val bottomCornerRadius by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "bottomCornerRadius"
    ) { hovered ->
        if (hovered) EXPANDED_BOTTOM_CORNER_RADIUS else COLLAPSED_BOTTOM_CORNER_RADIUS
    }
    val verticalPadding by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "verticalPadding"
    ) { hovered ->
        if (hovered) ToolkitTheme.spacing.smallMedium else ToolkitTheme.spacing.extraSmall
    }
    val handleBottomPadding by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "handleBottomPadding"
    ) { hovered ->
        if (hovered) ToolkitTheme.spacing.extraSmall else 0.dp
    }
    val handleBarAlpha by transition.animateFloat(
        transitionSpec = { effectsSpringFloat },
        label = "handleBarAlpha"
    ) { hovered ->
        if (hovered) EXPANDED_HANDLE_ALPHA else COLLAPSED_HANDLE_ALPHA
    }
    val iconContainerSize by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "iconContainerSize"
    ) { hovered ->
        if (hovered) EXPANDED_ICON_CONTAINER_SIZE else COLLAPSED_ICON_CONTAINER_SIZE
    }
    val iconSize by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "iconSize"
    ) { hovered ->
        if (hovered) ToolkitTheme.dimensions.iconMediumSmall else ToolkitTheme.dimensions.iconSmall
    }
    val elevation by transition.animateDp(
        transitionSpec = { if (targetState) spatialSpringDp else spatialSpringDpNoBouncy },
        label = "elevation"
    ) { hovered ->
        if (hovered) ToolkitTheme.dimensions.elevationMedium else ToolkitTheme.dimensions.cardElevation
    }
    val iconBgColor by transition.animateColor(
        transitionSpec = { effectsSpringColor },
        label = "iconBgColor"
    ) { hovered ->
        if (hovered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    }
    val titleColor by transition.animateColor(
        transitionSpec = { effectsSpringColor },
        label = "titleColor"
    ) { hovered ->
        if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) {
                exitJob?.cancel()
                isHovered = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                exitJob?.cancel()
                exitJob = hoverScope.launch {
                    delay(HOVER_EXIT_DEBOUNCE_MS)
                    isHovered = false
                }
            }
            .testTag("interactive_floating_bottom_bar"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.padding(bottom = bottomOffset.coerceAtLeast(0.dp)),
            shape = RoundedCornerShape(
                topStart = cornerRadius.coerceAtLeast(0.dp),
                topEnd = cornerRadius.coerceAtLeast(0.dp),
                bottomStart = bottomCornerRadius.coerceAtLeast(0.dp),
                bottomEnd = bottomCornerRadius.coerceAtLeast(0.dp)
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = elevation.coerceAtLeast(0.dp),
            shadowElevation = elevation.coerceAtLeast(0.dp),
            border = BorderStroke(
                width = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = verticalPadding.coerceAtLeast(0.dp)
                )
            ) {
                // Docked handle bar
                Box(
                    modifier = Modifier
                        .padding(bottom = handleBottomPadding.coerceAtLeast(0.dp))
                        .size(width = DOCKED_HANDLE_WIDTH, height = DOCKED_HANDLE_HEIGHT)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = handleBarAlpha))
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                ) {
                    // Persistent TouchApp Icon in adaptive container
                    Surface(
                        shape = CircleShape,
                        color = iconBgColor,
                        modifier = Modifier.size(iconContainerSize.coerceAtLeast(0.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(iconSize.coerceAtLeast(0.dp))
                            )
                        }
                    }

                    Spacer(Modifier.width(ToolkitTheme.spacing.small))

                    // Tool title & optional plugin subtitle
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        transition.AnimatedVisibility(
                            visible = { hovered -> hovered && pluginName.isNotBlank() },
                            enter = fadeIn(animationSpec = effectsSpringFloat) +
                                    scaleIn(
                                        initialScale = 0f,
                                        transformOrigin = TransformOrigin(0f, 0f),
                                        animationSpec = spatialSpringFloat
                                    ) +
                                    expandVertically(
                                        animationSpec = layoutSizeSpring,
                                        expandFrom = Alignment.Top,
                                        clip = false
                                    ),
                            exit = fadeOut(animationSpec = effectsSpringFloat) +
                                    scaleOut(
                                        targetScale = 0f,
                                        transformOrigin = TransformOrigin(0f, 0f),
                                        animationSpec = effectsSpringFloat
                                    ) +
                                    shrinkVertically(
                                        animationSpec = layoutSizeSpring,
                                        shrinkTowards = Alignment.Top,
                                        clip = false
                                    )
                        ) {
                            Text(
                                text = pluginName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Collapsed up-arrow indicator

                    transition.AnimatedVisibility(
                        visible = { hovered -> !hovered },
                        enter = fadeIn(animationSpec = effectsSpringFloat) +
                                scaleIn(
                                    initialScale = ARROW_ENTER_EXIT_SCALE,
                                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                                    animationSpec = effectsSpringFloat
                                ) +
                                expandHorizontally(
                                    animationSpec = layoutSizeSpring,
                                    expandFrom = Alignment.Start,
                                    clip = false
                                ),
                        exit = fadeOut(animationSpec = effectsSpringFloat) +
                                scaleOut(
                                    targetScale = ARROW_ENTER_EXIT_SCALE,
                                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                                    animationSpec = effectsSpringFloat
                                ) +
                                shrinkHorizontally(
                                    animationSpec = layoutSizeSpring,
                                    shrinkTowards = Alignment.Start,
                                    clip = false
                                )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        }
                    }

                    // Expanded action controls (VerticalDivider, Restart Button, Close Button)
                    transition.AnimatedVisibility(
                        visible = { hovered -> hovered },
                        enter = fadeIn(animationSpec = effectsSpringFloat) +
                                scaleIn(
                                    initialScale = ACTION_ENTER_EXIT_SCALE,
                                    transformOrigin = TransformOrigin(0f, 0.5f),
                                    animationSpec = spatialSpringFloat
                                ) +
                                expandHorizontally(
                                    animationSpec = layoutSizeSpring,
                                    expandFrom = Alignment.Start,
                                    clip = false
                                ) +
                                expandVertically(
                                    animationSpec = layoutSizeSpring,
                                    expandFrom = Alignment.CenterVertically,
                                    clip = false
                                ),
                        exit = fadeOut(animationSpec = effectsSpringFloat) +
                                scaleOut(
                                    targetScale = ACTION_ENTER_EXIT_SCALE,
                                    transformOrigin = TransformOrigin(0f, 0.5f),
                                    animationSpec = effectsSpringFloat
                                ) +
                                shrinkHorizontally(
                                    animationSpec = layoutSizeSpring,
                                    shrinkTowards = Alignment.Start,
                                    clip = false
                                ) +
                                shrinkVertically(
                                    animationSpec = layoutSizeSpring,
                                    shrinkTowards = Alignment.CenterVertically,
                                    clip = false
                                )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(ToolkitTheme.spacing.mediumSmall))

                            VerticalDivider(
                                modifier = Modifier
                                    .height(ACTION_DIVIDER_HEIGHT)
                                    .width(ToolkitTheme.dimensions.borderThin),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Spacer(Modifier.width(ToolkitTheme.spacing.smallMedium))

                            FilledTonalButton(
                                onClick = onRestart,
                                contentPadding = PaddingValues(
                                    horizontal = ToolkitTheme.spacing.mediumSmall,
                                    vertical = 0.dp
                                ),
                                modifier = Modifier.height(ToolkitTheme.dimensions.standardButtonHeight)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                                Spacer(Modifier.width(ToolkitTheme.spacing.extraSmall))
                                Text(
                                    text = stringResource(Res.string.action_restart_session),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Spacer(Modifier.width(ToolkitTheme.spacing.smallMedium))

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.action_close_session),
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}