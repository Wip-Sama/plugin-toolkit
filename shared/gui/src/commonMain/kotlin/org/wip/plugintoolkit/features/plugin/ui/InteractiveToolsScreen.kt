package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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

/**
 * Low-presence floating bottom bar for active interactive tools.
 * Sits as a subtle docked tab at the bottom edge, expanding and animating up smoothly into a floating pill when hovered (Image 4).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InteractiveFloatingBottomBar(
    toolName: String,
    pluginName: String,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }
    val hoverScope = rememberCoroutineScope()
    var exitJob by remember { mutableStateOf<Job?>(null) }

    // M3 Expressive Spatial Springs: X/Y position, scaling, size, corner radius
    // Employs snappy take-offs, bouncy overshoot (LowBouncy), and gradual softer landings
    val spatialSpringDp = remember {
        spring<Dp>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
    val spatialSpringSize = remember {
        spring<IntSize>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    // M3 Expressive Effects Springs: Opacity, color blending, crossfades
    // Strict damping with zero overshoot (NoBouncy) to prevent visual artifacts and flickering
    val effectsSpringFloat = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    val bottomOffset by animateDpAsState(
        targetValue = if (isHovered) 20.dp else 0.dp,
        animationSpec = spatialSpringDp,
        label = "bottomOffset"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isHovered) 28.dp else 14.dp,
        animationSpec = spatialSpringDp,
        label = "cornerRadius"
    )
    val bottomCornerRadius by animateDpAsState(
        targetValue = if (isHovered) 28.dp else 0.dp,
        animationSpec = spatialSpringDp,
        label = "bottomCornerRadius"
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (isHovered) ToolkitTheme.spacing.smallMedium else ToolkitTheme.spacing.extraSmall,
        animationSpec = spatialSpringDp,
        label = "verticalPadding"
    )
    val handleBarAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.35f else 0.7f,
        animationSpec = effectsSpringFloat,
        label = "handleBarAlpha"
    )

    // Outer hit-box stays grounded at the bottom edge so the 20.dp gap below the floating Surface
    // remains inside the hover area, avoiding rapid cursor re-interception loops.
    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) {
                exitJob?.cancel()
                isHovered = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                exitJob?.cancel()
                exitJob = hoverScope.launch {
                    delay(120)
                    isHovered = false
                }
            }
            .testTag("interactive_floating_bottom_bar"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(bottom = bottomOffset.coerceAtLeast(0.dp))
                .animateContentSize(animationSpec = spatialSpringSize),
            shape = RoundedCornerShape(
                topStart = cornerRadius.coerceAtLeast(0.dp),
                topEnd = cornerRadius.coerceAtLeast(0.dp),
                bottomStart = bottomCornerRadius.coerceAtLeast(0.dp),
                bottomEnd = bottomCornerRadius.coerceAtLeast(0.dp)
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isHovered) ToolkitTheme.dimensions.elevationMedium else ToolkitTheme.dimensions.cardElevation,
            shadowElevation = if (isHovered) ToolkitTheme.dimensions.elevationMedium else 3.dp,
            border = BorderStroke(
                width = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = verticalPadding
                )
            ) {
                // Docked handle bar (Image 4 left drawing)
                Box(
                    modifier = Modifier
                        .padding(bottom = if (isHovered) ToolkitTheme.spacing.extraSmall else 0.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = handleBarAlpha))
                )

                AnimatedContent(
                    targetState = isHovered,
                    transitionSpec = {
                        fadeIn(animationSpec = effectsSpringFloat) togetherWith
                            fadeOut(animationSpec = effectsSpringFloat)
                    },
                    contentAlignment = Alignment.Center,
                    label = "floatingBarContent"
                ) { hovered ->
                    if (!hovered) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                            modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Text(
                                text = toolName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        }
                    } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall),
                        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = toolName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = pluginName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                        VerticalDivider(
                            modifier = Modifier
                                .height(28.dp)
                                .width(ToolkitTheme.dimensions.borderThin),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        FilledTonalButton(
                            onClick = onRestart,
                            contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = 0.dp),
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
