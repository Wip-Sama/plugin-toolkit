package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.DatePickerNode
import org.wip.plugintoolkit.api.sdui.FabMenuNode
import org.wip.plugintoolkit.api.sdui.FabNode
import org.wip.plugintoolkit.api.sdui.FabSize
import org.wip.plugintoolkit.api.sdui.FloatingToolbarNode
import org.wip.plugintoolkit.api.sdui.ImageCompareNode
import org.wip.plugintoolkit.api.sdui.MediaKind
import org.wip.plugintoolkit.api.sdui.MediaPlayerNode
import org.wip.plugintoolkit.api.sdui.SideSheetAttachPoint
import org.wip.plugintoolkit.api.sdui.SideSheetNode
import org.wip.plugintoolkit.api.sdui.TimePickerNode
import org.wip.plugintoolkit.api.sdui.ToolbarOrientation
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitFabMenu
import org.wip.plugintoolkit.shared.components.ToolkitFabMenuItem
import org.wip.plugintoolkit.shared.components.ToolkitImageCompare
import org.wip.plugintoolkit.shared.components.ToolkitMediaPlayer
import org.wip.plugintoolkit.shared.components.ToolkitSideSheet
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_cancel
import plugintoolkit.composeapp.generated.resources.sdui_date_picker_confirm
import plugintoolkit.composeapp.generated.resources.sdui_date_picker_placeholder
import plugintoolkit.composeapp.generated.resources.sdui_date_picker_select
import plugintoolkit.composeapp.generated.resources.sdui_image_compare_after
import plugintoolkit.composeapp.generated.resources.sdui_image_compare_before
import plugintoolkit.composeapp.generated.resources.sdui_time_picker_select

private const val MILLIS_PER_DAY = 86_400_000L
private const val MEDIA_DEFAULT_TITLE = "Media"

/**
 * Renders [FloatingToolbarNode] using Material 3 Expressive [HorizontalFloatingToolbar] or
 * [VerticalFloatingToolbar], with optional standard or vibrant container colors and attached FAB.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RenderFloatingToolbarNode(
    node: FloatingToolbarNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = node.modifier.enabled
    val colors = if (node.vibrant) {
        FloatingToolbarDefaults.vibrantFloatingToolbarColors()
    } else {
        FloatingToolbarDefaults.standardFloatingToolbarColors()
    }
    val actionContent: @Composable () -> Unit = {
        node.items.forEach { item ->
            val itemEnabled = enabled && item.enabled
            val itemIcon = SduiIconRegistry.resolve(item.iconName ?: "bolt")
            ToolkitTooltipBox(tooltip = item.label) {
                IconButton(
                    onClick = { onEvent(UiEvent.Click(node.id, item.action ?: item.key)) },
                    enabled = itemEnabled,
                    modifier = Modifier.pointerHoverIcon(
                        if (itemEnabled) PointerIcon.Hand else PointerIcon.Default
                    )
                ) {
                    Icon(
                        imageVector = itemIcon,
                        contentDescription = item.label,
                        tint = if (item.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            LocalContentColor.current
                        }
                    )
                }
            }
        }
    }
    val fabSlot: @Composable () -> Unit = {
        node.primaryAction?.let { primary ->
            val primaryEnabled = enabled && primary.enabled
            val fabIcon = SduiIconRegistry.resolve(primary.iconName ?: "play_arrow")
            ToolkitTooltipBox(tooltip = primary.label) {
                if (node.vibrant) {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = {
                            if (primaryEnabled) onEvent(UiEvent.Click(node.id, primary.action ?: primary.key))
                        },
                        modifier = Modifier.pointerHoverIcon(
                            if (primaryEnabled) PointerIcon.Hand else PointerIcon.Default
                        )
                    ) {
                        Icon(imageVector = fabIcon, contentDescription = primary.label)
                    }
                } else {
                    FloatingToolbarDefaults.StandardFloatingActionButton(
                        onClick = {
                            if (primaryEnabled) onEvent(UiEvent.Click(node.id, primary.action ?: primary.key))
                        },
                        modifier = Modifier.pointerHoverIcon(
                            if (primaryEnabled) PointerIcon.Hand else PointerIcon.Default
                        )
                    ) {
                        Icon(imageVector = fabIcon, contentDescription = primary.label)
                    }
                }
            }
        }
    }

    when (node.orientation) {
        ToolbarOrientation.HORIZONTAL -> {
            if (node.primaryAction != null) {
                HorizontalFloatingToolbar(
                    expanded = node.expanded,
                    floatingActionButton = fabSlot,
                    modifier = modifier,
                    colors = colors
                ) {
                    actionContent()
                }
            } else {
                HorizontalFloatingToolbar(
                    expanded = node.expanded,
                    modifier = modifier,
                    colors = colors
                ) {
                    actionContent()
                }
            }
        }
        ToolbarOrientation.VERTICAL -> {
            if (node.primaryAction != null) {
                VerticalFloatingToolbar(
                    expanded = node.expanded,
                    floatingActionButton = fabSlot,
                    modifier = modifier,
                    colors = colors
                ) {
                    actionContent()
                }
            } else {
                VerticalFloatingToolbar(
                    expanded = node.expanded,
                    modifier = modifier,
                    colors = colors
                ) {
                    actionContent()
                }
            }
        }
    }
}

/**
 * Renders [FabNode] using Material 3 Expressive Floating Action Button size variants.
 */
@Composable
internal fun RenderFabNode(
    node: FabNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = node.modifier.enabled
    val iconVector = SduiIconRegistry.resolve(node.iconName)
    val contentDesc = node.label ?: node.tooltip ?: node.action
    val pointerModifier = modifier.pointerHoverIcon(
        if (enabled) PointerIcon.Hand else PointerIcon.Default
    )
    val onClick = {
        if (enabled) {
            onEvent(UiEvent.Click(node.id, node.action))
        }
    }

    val effectiveTooltip = node.tooltip ?: if (node.size != FabSize.EXTENDED) node.label else null
    ToolkitTooltipBox(tooltip = effectiveTooltip) {
        when (node.size) {
            FabSize.SMALL -> SmallFloatingActionButton(onClick = onClick, modifier = pointerModifier) {
                Icon(imageVector = iconVector, contentDescription = contentDesc)
            }
            FabSize.STANDARD -> FloatingActionButton(onClick = onClick, modifier = pointerModifier) {
                Icon(imageVector = iconVector, contentDescription = contentDesc)
            }
            FabSize.LARGE -> LargeFloatingActionButton(onClick = onClick, modifier = pointerModifier) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = contentDesc,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                )
            }
            FabSize.EXTENDED -> ExtendedFloatingActionButton(
                onClick = onClick,
                icon = { Icon(imageVector = iconVector, contentDescription = null) },
                text = { Text(text = node.label ?: node.action) },
                modifier = pointerModifier
            )
        }
    }
}

/**
 * Renders [FabMenuNode] using [ToolkitFabMenu] backed by M3 Expressive [FloatingActionButtonMenu].
 */
@Composable
internal fun RenderFabMenuNode(
    node: FabMenuNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(node.id, node.expanded) { mutableStateOf(node.expanded) }
    val enabled = node.modifier.enabled
    val menuItems = remember(node.items) {
        node.items.map { item ->
            ToolkitFabMenuItem(
                key = item.key,
                label = item.label,
                icon = item.iconName?.let { SduiIconRegistry.resolve(it) } ?: Icons.Default.Add,
                enabled = item.enabled && enabled,
                isDestructive = item.isDestructive
            )
        }
    }

    ToolkitFabMenu(
        expanded = expanded,
        onExpandedChange = { next -> if (enabled) expanded = next },
        items = menuItems,
        onItemClick = { clicked ->
            val rawAction = node.items.find { it.key == clicked.key }?.action ?: clicked.key
            onEvent(UiEvent.Click(node.id, rawAction))
        },
        icon = SduiIconRegistry.resolve(node.iconName),
        tooltip = node.tooltip ?: node.label,
        modifier = modifier
    )
}

/**
 * Renders [SideSheetNode] using the standardized desktop [ToolkitSideSheet] docked to [SideSheetAttachPoint].
 */
@Composable
internal fun RenderSideSheetNode(
    node: SideSheetNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!node.isOpen) return

    val alignment = when (node.attachPoint) {
        SideSheetAttachPoint.START -> Alignment.CenterStart
        SideSheetAttachPoint.END -> Alignment.CenterEnd
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.align(alignment)) {
            ToolkitSideSheet(
                isOpen = node.isOpen,
                title = node.title,
                onDismiss = {
                    val action = node.dismissAction
                    if (!action.isNullOrBlank()) {
                        onEvent(UiEvent.Click(node.id, action))
                    } else {
                        onEvent(UiEvent.DialogAction(node.id, isConfirmed = false))
                    }
                }
            ) {
                node.children.forEach { child ->
                    RenderUiNode(node = child, onEvent = onEvent)
                }
            }
        }
    }
}

/**
 * Renders [DatePickerNode] inline or as a compact field button opening Material 3 [DatePickerDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenderDatePickerNode(
    node: DatePickerNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = node.modifier.enabled
    var dialogOpen by remember(node.id) { mutableStateOf(false) }
    val minMillis = remember(node.minDateIso) { parseIsoDateMillisOrNull(node.minDateIso) }
    val maxMillis = remember(node.maxDateIso) { parseIsoDateMillisOrNull(node.maxDateIso) }
    val initialMillis = remember(node.selectedDateIso) { parseIsoDateMillisOrNull(node.selectedDateIso) }

    val selectableDates = remember(minMillis, maxMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val afterMin = minMillis == null || utcTimeMillis >= minMillis
                val beforeMax = maxMillis == null || utcTimeMillis <= maxMillis
                return afterMin && beforeMax
            }
        }
    }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = selectableDates
    )

    if (node.inline) {
        LaunchedEffect(state.selectedDateMillis) {
            val selectedIso = state.selectedDateMillis?.let { formatEpochMillisIso(it) }
            if (selectedIso != null && selectedIso != node.selectedDateIso && enabled) {
                onEvent(UiEvent.TextChanged(node.id, selectedIso))
            }
        }
        Surface(
            modifier = modifier.withFormWidthConstraint(node.modifier),
            shape = ToolkitTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            DatePicker(state = state)
        }
        return
    }

    Column(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        node.label?.takeIf { it.isNotBlank() }?.let { fieldLabel ->
            Text(
                text = fieldLabel,
                style = ToolkitTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { if (enabled) dialogOpen = true },
            enabled = enabled,
            shape = ToolkitTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.sm))
            Text(
                text = node.selectedDateIso?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.sdui_date_picker_placeholder),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(Res.string.sdui_date_picker_select),
                style = ToolkitTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (dialogOpen) {
        DatePickerDialog(
            onDismissRequest = { dialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedIso = state.selectedDateMillis?.let { formatEpochMillisIso(it) }
                        if (selectedIso != null) {
                            onEvent(UiEvent.TextChanged(node.id, selectedIso))
                        }
                        dialogOpen = false
                    }
                ) {
                    Text(stringResource(Res.string.sdui_date_picker_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(Res.string.sdui_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Renders [TimePickerNode] inline via Material 3 [TimeInput] or via a compact trigger card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenderTimePickerNode(
    node: TimePickerNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = node.modifier.enabled
    var showInlineInput by remember(node.id, node.inline) { mutableStateOf(node.inline) }
    val state = rememberTimePickerState(
        initialHour = node.hour.coerceIn(0, 23),
        initialMinute = node.minute.coerceIn(0, 59),
        is24Hour = node.is24Hour
    )
    val currentFormatted = remember(state.hour, state.minute) {
        formatTimeHm(state.hour, state.minute)
    }

    LaunchedEffect(node.hour, node.minute) {
        state.hour = node.hour.coerceIn(0, 23)
        state.minute = node.minute.coerceIn(0, 59)
    }

    Column(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        node.label?.takeIf { it.isNotBlank() }?.let { fieldLabel ->
            Text(
                text = fieldLabel,
                style = ToolkitTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!showInlineInput) {
            OutlinedButton(
                onClick = { if (enabled) showInlineInput = true },
                enabled = enabled,
                shape = ToolkitTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.sm))
                Text(
                    text = formatTimeHm(node.hour.coerceIn(0, 23), node.minute.coerceIn(0, 59)),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(Res.string.sdui_time_picker_select),
                    style = ToolkitTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Surface(
                shape = ToolkitTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    ToolkitTheme.dimensions.borderUnselected,
                    MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(ToolkitTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    TimeInput(state = state)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!node.inline) {
                            OutlinedButton(
                                onClick = { showInlineInput = false },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(stringResource(Res.string.sdui_cancel))
                            }
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.sm))
                        }
                        Button(
                            onClick = {
                                if (enabled) {
                                    onEvent(UiEvent.TextChanged(node.id, currentFormatted))
                                }
                                if (!node.inline) {
                                    showInlineInput = false
                                }
                            },
                            enabled = enabled,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.pointerHoverIcon(
                                if (enabled) PointerIcon.Hand else PointerIcon.Default
                            )
                        ) {
                            Text(stringResource(Res.string.sdui_date_picker_confirm))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders [ImageCompareNode] using [ToolkitImageCompare] and cached plugin asset imagery.
 */
@Composable
internal fun RenderImageCompareNode(
    node: ImageCompareNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var localSplit by remember(node.id, node.splitPosition) {
        mutableFloatStateOf(node.splitPosition.coerceIn(0f, 1f))
    }
    val beforeLabel = node.beforeLabel ?: stringResource(Res.string.sdui_image_compare_before)
    val afterLabel = node.afterLabel ?: stringResource(Res.string.sdui_image_compare_after)

    ToolkitImageCompare(
        beforeContent = {
            PluginAssetImage(
                asset = node.beforeAsset,
                contentDescription = beforeLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        },
        afterContent = {
            PluginAssetImage(
                asset = node.afterAsset,
                contentDescription = afterLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        },
        splitPosition = localSplit,
        onSplitPositionChange = { next ->
            localSplit = next
            onEvent(UiEvent.ValueChanged(node.id, next.toDouble()))
        },
        beforeLabel = beforeLabel,
        afterLabel = afterLabel,
        enabled = node.modifier.enabled,
        modifier = modifier
    )
}

/**
 * Renders [MediaPlayerNode] using [ToolkitMediaPlayer] with plugin-sandboxed asset loading.
 */
@Composable
internal fun RenderMediaPlayerNode(
    node: MediaPlayerNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val assetContext = LocalPluginAssetContext.current
    var resolvedBytes by remember(node.id, node.asset) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(node.asset, assetContext) {
        resolvedBytes = resolveAssetBytes(
            data = node.asset,
            fileSystem = assetContext.fileSystem,
            cacheFileSystem = assetContext.cacheFileSystem,
            hostFileSystem = assetContext.hostFileSystem,
            networkClient = assetContext.networkClient
        )
    }

    val isVideo = when (node.kind) {
        MediaKind.VIDEO -> true
        MediaKind.AUDIO -> false
        MediaKind.AUTO -> inferVideoAsset(node.asset)
    }

    ToolkitMediaPlayer(
        title = node.title ?: MEDIA_DEFAULT_TITLE,
        isVideo = isVideo,
        mediaBytes = resolvedBytes,
        autoPlay = node.autoPlay,
        showControls = node.showControls,
        enabled = node.modifier.enabled,
        onPlayStateChanged = { playing ->
            onEvent(UiEvent.Click(node.id, if (playing) "play" else "pause"))
        },
        onPositionFractionChanged = { fraction ->
            onEvent(UiEvent.ValueChanged(node.id, fraction.toDouble()))
        },
        modifier = modifier
    )
}

private fun parseIsoDateMillisOrNull(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        LocalDate.parse(iso.trim()).toEpochDays().toLong() * MILLIS_PER_DAY
    }.getOrNull()
}

private fun formatEpochMillisIso(millis: Long): String {
    val epochDays = (millis / MILLIS_PER_DAY).toInt()
    return LocalDate.fromEpochDays(epochDays).toString()
}

private fun formatTimeHm(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun inferVideoAsset(asset: AssetReference): Boolean {
    val raw = when (asset) {
        is AssetReference.Relative -> asset.path
        is AssetReference.Cached -> asset.assetId
        is AssetReference.Uri -> asset.uriString
    }.lowercase()
    return raw.endsWith(".mp4") || raw.endsWith(".webm") || raw.endsWith(".mkv") || raw.endsWith(".mov")
}
