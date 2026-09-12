package org.wip.plugintoolkit.features.shortcuts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutAction
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutConflict
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutInputMode
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutTrigger
import org.wip.plugintoolkit.features.shortcuts.utils.KeyMapping
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.shortcuts_add_trigger
import plugintoolkit.composeapp.generated.resources.shortcuts_button_label
import plugintoolkit.composeapp.generated.resources.shortcuts_conflict_warning
import plugintoolkit.composeapp.generated.resources.shortcuts_current_binding
import plugintoolkit.composeapp.generated.resources.shortcuts_gesture_label
import plugintoolkit.composeapp.generated.resources.shortcuts_key_label
import plugintoolkit.composeapp.generated.resources.shortcuts_listening_key
import plugintoolkit.composeapp.generated.resources.shortcuts_modifiers_label
import plugintoolkit.composeapp.generated.resources.shortcuts_press_key_prompt
import plugintoolkit.composeapp.generated.resources.shortcuts_relative_priority
import plugintoolkit.composeapp.generated.resources.shortcuts_relative_priority_desc
import plugintoolkit.composeapp.generated.resources.shortcuts_remap_title
import plugintoolkit.composeapp.generated.resources.shortcuts_remove_trigger
import plugintoolkit.composeapp.generated.resources.shortcuts_reset
import plugintoolkit.composeapp.generated.resources.shortcuts_save
import plugintoolkit.composeapp.generated.resources.shortcuts_trigger_number

/**
 * Versatile dialog for remapping a shortcut action's triggers.
 *
 * Input mode is strictly governed by [ShortcutAction.inputMode]. Pointer controls use
 * Material 3 segmented button groups for mouse buttons, gesture types, and modifiers.
 * Keyboard controls use direct key listening without requiring manual modifier toggles.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RemapShortcutDialog(
    action: ShortcutAction,
    currentTriggers: List<ShortcutTrigger>,
    shortcutManager: ShortcutManager,
    onDismiss: () -> Unit,
    onSave: (List<ShortcutTrigger>, Int) -> Unit,
    onReset: () -> Unit
) {
    val triggers = remember {
        mutableStateListOf<ShortcutTrigger>().apply {
            addAll(currentTriggers.ifEmpty { action.defaultTriggers })
        }
    }

    var relativePriority by remember {
        mutableStateOf(shortcutManager.getEffectiveRelativePriority(action.id))
    }

    var selectedIndex by remember { mutableStateOf(0) }
    if (selectedIndex >= triggers.size) {
        selectedIndex = (triggers.size - 1).coerceAtLeast(0)
    }

    val activeTrigger = if (triggers.isNotEmpty()) triggers[selectedIndex] else ShortcutTrigger()

    var isRecordingKey by remember { mutableStateOf(false) }
    var firstPressedKey by remember { mutableStateOf<Key?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isRecordingKey) {
        if (isRecordingKey) {
            firstPressedKey = null
            focusRequester.requestFocus()
        }
    }

    // Check conflicts across all candidate triggers
    val conflict: ShortcutConflict? = remember(triggers.toList()) {
        triggers.firstNotNullOfOrNull { trigger ->
            shortcutManager.checkConflictForTrigger(action.id, trigger)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = ToolkitTheme.dimensions.minWidthMedium, max = ToolkitTheme.dimensions.maxWidthLarge)
                .padding(ToolkitTheme.spacing.medium),
            shape = ToolkitTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = ToolkitTheme.dimensions.elevationHigh,
            shadowElevation = ToolkitTheme.dimensions.elevationMedium,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderThin,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(ToolkitTheme.spacing.large)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                // Header
                Column {
                    Text(
                        text = stringResource(Res.string.shortcuts_remap_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(ToolkitTheme.spacing.extraSmall))
                    Text(
                        text = "${action.title.resolve()} (${action.situation.displayLabel})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = action.description.resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trigger Tabs / List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                        modifier = Modifier.weight(1f)
                    ) {
                        triggers.forEachIndexed { index, trig ->
                            FilterChip(
                                selected = selectedIndex == index,
                                onClick = {
                                    selectedIndex = index
                                    isRecordingKey = false
                                },
                                label = {
                                    Text(
                                        stringResource(Res.string.shortcuts_trigger_number, index + 1) +
                                                ": " + trig.format()
                                    )
                                },
                                trailingIcon = if (triggers.size > 1) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(Res.string.shortcuts_remove_trigger),
                                            modifier = Modifier
                                                .size(ToolkitTheme.dimensions.iconExtraSmall)
                                                .clickable {
                                                    triggers.removeAt(index)
                                                    if (selectedIndex >= triggers.size) {
                                                        selectedIndex = (triggers.size - 1).coerceAtLeast(0)
                                                    }
                                                }
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val newTrigger = when (action.inputMode) {
                                ShortcutInputMode.Pointer -> ShortcutTrigger(
                                    pointerButton = ShortcutPointerButton.Left,
                                    gesture = ShortcutGesture.Click
                                )
                                ShortcutInputMode.Keyboard -> ShortcutTrigger(
                                    key = ShortcutKey.A
                                )
                                ShortcutInputMode.Hybrid -> ShortcutTrigger(
                                    key = ShortcutKey.A,
                                    pointerButton = ShortcutPointerButton.Left,
                                    gesture = ShortcutGesture.Click
                                )
                            }
                            triggers.add(newTrigger)
                            selectedIndex = triggers.size - 1
                            isRecordingKey = false
                        },
                        modifier = Modifier.padding(start = ToolkitTheme.spacing.small)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        Spacer(Modifier.width(ToolkitTheme.spacing.extraSmall))
                        Text(stringResource(Res.string.shortcuts_add_trigger))
                    }
                }

                // Keyboard Recording Section (for Keyboard or Hybrid actions)
                if (action.inputMode == ShortcutInputMode.Keyboard || action.inputMode == ShortcutInputMode.Hybrid) {
                    Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                        Text(
                            text = stringResource(Res.string.shortcuts_key_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (!isRecordingKey) return@onPreviewKeyEvent false

                                    if (event.type == KeyEventType.KeyDown) {
                                        if (firstPressedKey == null) {
                                            firstPressedKey = event.key
                                        }
                                        val composeKey = event.key
                                        val isBareModifier = composeKey == Key.CtrlLeft || composeKey == Key.CtrlRight ||
                                                composeKey == Key.ShiftLeft || composeKey == Key.ShiftRight ||
                                                composeKey == Key.AltLeft || composeKey == Key.AltRight ||
                                                composeKey == Key.MetaLeft || composeKey == Key.MetaRight

                                        val mappedKey = if (!isBareModifier) KeyMapping.fromComposeKey(composeKey) else activeTrigger.key

                                        triggers[selectedIndex] = activeTrigger.copy(
                                            key = mappedKey,
                                            isCtrl = event.isCtrlPressed || composeKey == Key.CtrlLeft || composeKey == Key.CtrlRight,
                                            isShift = event.isShiftPressed || composeKey == Key.ShiftLeft || composeKey == Key.ShiftRight,
                                            isAlt = event.isAltPressed || composeKey == Key.AltLeft || composeKey == Key.AltRight,
                                            isMeta = event.isMetaPressed || composeKey == Key.MetaLeft || composeKey == Key.MetaRight
                                        )
                                        true
                                    } else if (event.type == KeyEventType.KeyUp) {
                                        if (firstPressedKey != null && (event.key == firstPressedKey || activeTrigger.key != null)) {
                                            isRecordingKey = false
                                            firstPressedKey = null
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .background(
                                    color = if (isRecordingKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = ToolkitTheme.shapes.small
                                )
                                .border(
                                    width = ToolkitTheme.dimensions.borderThin,
                                    color = if (isRecordingKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = ToolkitTheme.shapes.small
                                )
                                .clickable { isRecordingKey = !isRecordingKey }
                                .padding(
                                    horizontal = ToolkitTheme.spacing.medium,
                                    vertical = ToolkitTheme.spacing.mediumSmall
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isRecordingKey) stringResource(Res.string.shortcuts_listening_key)
                                        else (activeTrigger.key?.label?.let { "Key: $it" } ?: stringResource(Res.string.shortcuts_press_key_prompt)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRecordingKey) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isRecordingKey) "Press keys together. Release first key to finish."
                                        else "Click to record shortcut...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null,
                                    tint = if (isRecordingKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Pointer Controls (Segmented buttons for Mouse Button & Gesture Type)
                if (action.inputMode == ShortcutInputMode.Pointer || action.inputMode == ShortcutInputMode.Hybrid) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                    ) {
                        // Mouse Button Segmented Row
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                        ) {
                            Text(
                                text = stringResource(Res.string.shortcuts_button_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PointerButtonSegmentedRow(
                                selectedButton = activeTrigger.pointerButton,
                                onButtonSelected = { btn ->
                                    triggers[selectedIndex] = activeTrigger.copy(pointerButton = btn)
                                }
                            )
                        }

                        // Gesture Type Segmented Row
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                        ) {
                            Text(
                                text = stringResource(Res.string.shortcuts_gesture_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            GestureSegmentedRow(
                                selectedGesture = activeTrigger.gesture,
                                onGestureSelected = { gest ->
                                    triggers[selectedIndex] = activeTrigger.copy(gesture = gest)
                                }
                            )
                        }
                    }

                    // Optional Pointer Modifier Segmented Row
                    Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                        Text(
                            text = stringResource(Res.string.shortcuts_modifiers_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PointerModifierSegmentedRow(
                            trigger = activeTrigger,
                            onModifierSelected = { mod ->
                                triggers[selectedIndex] = when (mod) {
                                    "None" -> activeTrigger.copy(isCtrl = false, isShift = false, isAlt = false, isMeta = false)
                                    "Ctrl" -> activeTrigger.copy(isCtrl = true, isShift = false, isAlt = false, isMeta = false)
                                    "Shift" -> activeTrigger.copy(isCtrl = false, isShift = true, isAlt = false, isMeta = false)
                                    "Alt" -> activeTrigger.copy(isCtrl = false, isShift = false, isAlt = true, isMeta = false)
                                    else -> activeTrigger
                                }
                            }
                        )
                    }
                }

                // Combined Live Preview
                Surface(
                    shape = ToolkitTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        ToolkitTheme.dimensions.borderThin,
                        MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.shortcuts_current_binding),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = triggers.joinToString(" / ") { it.format() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Conflict Warning
                AnimatedVisibility(
                    visible = conflict != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (conflict != null) {
                        val otherAction = if (conflict.action1.id == action.id) conflict.action2 else conflict.action1
                        Surface(
                            shape = ToolkitTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(
                                ToolkitTheme.dimensions.borderThin,
                                MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                )
                                Text(
                                    text = stringResource(
                                        Res.string.shortcuts_conflict_warning,
                                        otherAction.title.resolve(),
                                        conflict.situation.displayLabel
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // ── Relative Priority ──────────────────────────────────────────
                Surface(
                    shape = ToolkitTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.settingsItemDefault),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ToolkitTheme.spacing.mediumSmall),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = ToolkitTheme.spacing.small)) {
                            Text(
                                text = stringResource(Res.string.shortcuts_relative_priority),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(Res.string.shortcuts_relative_priority_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                        ) {
                            OutlinedButton(
                                onClick = { relativePriority -= 1 },
                                contentPadding = PaddingValues(ToolkitTheme.spacing.extraSmall),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (relativePriority >= 0) "+$relativePriority" else "$relativePriority",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small)
                            )
                            OutlinedButton(
                                onClick = { relativePriority += 1 },
                                contentPadding = PaddingValues(ToolkitTheme.spacing.extraSmall),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Dialog Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onReset) {
                        Text(stringResource(Res.string.shortcuts_reset))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.dialog_cancel))
                        }
                        Button(onClick = { onSave(triggers.toList(), relativePriority) }) {
                            Text(stringResource(Res.string.shortcuts_save))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointerButtonSegmentedRow(
    selectedButton: ShortcutPointerButton,
    onButtonSelected: (ShortcutPointerButton) -> Unit
) {
    val buttons = listOf(
        ShortcutPointerButton.Left,
        ShortcutPointerButton.Middle,
        ShortcutPointerButton.Right
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        buttons.forEachIndexed { index, button ->
            SegmentedButton(
                selected = selectedButton == button,
                onClick = { onButtonSelected(button) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = buttons.size),
                label = {
                    Text(
                        text = button.displayName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureSegmentedRow(
    selectedGesture: ShortcutGesture,
    onGestureSelected: (ShortcutGesture) -> Unit
) {
    val gestures = listOf(
        ShortcutGesture.Click,
        ShortcutGesture.DoubleClick,
        ShortcutGesture.Drag,
        ShortcutGesture.Wheel
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        gestures.forEachIndexed { index, gesture ->
            SegmentedButton(
                selected = selectedGesture == gesture,
                onClick = { onGestureSelected(gesture) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = gestures.size),
                label = {
                    Text(
                        text = when (gesture) {
                            ShortcutGesture.DoubleClick -> "DblClick"
                            else -> gesture.displayName
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointerModifierSegmentedRow(
    trigger: ShortcutTrigger,
    onModifierSelected: (String) -> Unit
) {
    val modifiers = listOf("None", "Ctrl", "Shift", "Alt")
    val currentSelected = when {
        trigger.isCtrl -> "Ctrl"
        trigger.isShift -> "Shift"
        trigger.isAlt -> "Alt"
        else -> "None"
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modifiers.forEachIndexed { index, mod ->
            SegmentedButton(
                selected = currentSelected == mod,
                onClick = { onModifierSelected(mod) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modifiers.size),
                label = {
                    Text(
                        text = mod,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}
