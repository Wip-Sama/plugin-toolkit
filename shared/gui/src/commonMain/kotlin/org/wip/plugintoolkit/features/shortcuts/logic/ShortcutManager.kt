package org.wip.plugintoolkit.features.shortcuts.logic

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import org.wip.plugintoolkit.core.model.resolveNonComposable
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.shortcuts.model.EatenEventInfo
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutAction
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutConflict
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorities
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorityMode
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutSituation
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutTrigger
import org.wip.plugintoolkit.features.shortcuts.utils.KeyMapping

/**
 * Central stateful coordinator for application-wide shortcuts and gestures.
 *
 * Resolves default and user-configured shortcut triggers, supports multiple triggers per action,
 * detects intra-situation binding conflicts, formats shortcuts canonically, and provides event
 * hit-testing for both keyboard and pointer events across the UI hierarchy.
 */
class ShortcutManager(
    private val settingsRepository: SettingsRepository? = null,
    initialActions: List<ShortcutAction> = DefaultShortcutCatalog.actions
) {
    private val actionsMap: Map<String, ShortcutAction> = initialActions.associateBy { it.id }

    val allActions: List<ShortcutAction> = initialActions

    val settings: StateFlow<AppSettings>?
        get() = settingsRepository?.settings

    private val _activeSituations = MutableStateFlow<Set<ShortcutSituation>>(setOf(ShortcutSituation.Global))
    val activeSituations: StateFlow<Set<ShortcutSituation>> = _activeSituations.asStateFlow()

    private val _pointerSituation = MutableStateFlow<ShortcutSituation?>(null)
    val pointerSituation: StateFlow<ShortcutSituation?> = _pointerSituation.asStateFlow()

    private val _lastEatenEvent = MutableStateFlow<EatenEventInfo?>(null)
    val lastEatenEvent: StateFlow<EatenEventInfo?> = _lastEatenEvent.asStateFlow()

    private val _situationElevations = MutableStateFlow<Map<ShortcutSituation, Float>>(emptyMap())
    val situationElevations: StateFlow<Map<ShortcutSituation, Float>> = _situationElevations.asStateFlow()

    fun setActiveSituations(situations: Set<ShortcutSituation>) {
        _activeSituations.value = situations + ShortcutSituation.Global
    }

    fun setPointerSituation(situation: ShortcutSituation?) {
        _pointerSituation.value = situation
    }

    /**
     * Sets or removes a dynamic elevation / z-index for [situation].
     */
    fun setSituationElevation(situation: ShortcutSituation, elevation: Float?) {
        val current = _situationElevations.value.toMutableMap()
        if (elevation == null) {
            current.remove(situation)
        } else {
            current[situation] = elevation
        }
        _situationElevations.value = current
        Logger.d { "Situation '${situation.displayLabel}' elevation updated to $elevation" }
    }

    /**
     * Retrieves the current priority resolution mode from settings.
     */
    fun getPriorityMode(): ShortcutPriorityMode =
        settingsRepository?.settings?.value?.shortcuts?.priorityMode ?: ShortcutPriorityMode.ZIndexAndPriority

    /**
     * Updates the priority resolution mode in settings.
     */
    fun setPriorityMode(mode: ShortcutPriorityMode) {
        settingsRepository?.updateSettings { current ->
            current.copy(
                shortcuts = current.shortcuts.copy(priorityMode = mode)
            )
        }
        Logger.i { "Shortcut priority mode changed to: $mode" }
    }

    /**
     * Resolves the effective relative priority for an action.
     * Checks user custom relative priority in settings first, falling back to [ShortcutAction.relativePriority].
     */
    fun getEffectiveRelativePriority(actionId: String): Int {
        val custom = settingsRepository?.settings?.value?.shortcuts?.customRelativePriorities?.get(actionId)
        if (custom != null) return custom
        return getAction(actionId)?.relativePriority ?: ShortcutPriorities.Relative.NORMAL
    }

    /**
     * Sets a user override for an action's relative priority.
     */
    fun updateRelativePriority(actionId: String, relativePriority: Int) {
        settingsRepository?.updateSettings { current ->
            current.copy(
                shortcuts = current.shortcuts.copy(
                    customRelativePriorities = current.shortcuts.customRelativePriorities + (actionId to relativePriority)
                )
            )
        }
        Logger.i { "Updated relative priority for $actionId to $relativePriority" }
    }

    /**
     * Resets an action's relative priority back to its default.
     */
    fun resetRelativePriority(actionId: String) {
        settingsRepository?.updateSettings { current ->
            current.copy(
                shortcuts = current.shortcuts.copy(
                    customRelativePriorities = current.shortcuts.customRelativePriorities - actionId
                )
            )
        }
        Logger.i { "Reset relative priority for $actionId to default" }
    }

    /**
     * Resolves the effective base priority for [situation].
     * In [ShortcutPriorityMode.ZIndexAndPriority], dynamic elevation / z-index is used if present.
     * In [ShortcutPriorityMode.PriorityOnly], dynamic elevation is bypassed, strictly using [ShortcutSituation.defaultPriority].
     */
    fun getEffectivePriority(situation: ShortcutSituation): Float {
        val mode = getPriorityMode()
        val elevation = _situationElevations.value[situation]
        return if (mode == ShortcutPriorityMode.ZIndexAndPriority && elevation != null) {
            elevation
        } else {
            situation.defaultPriority.toFloat()
        }
    }

    /**
     * Resolves the total effective priority for a specific action:
     * `effectivePriority = basePriority(situation) + relativePriority(action)`.
     */
    fun getEffectiveActionPriority(actionId: String): Float {
        val action = getAction(actionId) ?: return 0f
        val base = getEffectivePriority(action.situation)
        val rel = getEffectiveRelativePriority(actionId).toFloat()
        return base + rel
    }

    /**
     * Checks if any change in [event] has already been consumed ("eaten") by an upstream or peer handler.
     */
    fun isConsumed(event: PointerEvent): Boolean {
        return event.changes.any { it.isConsumed }
    }

    /**
     * Consumes ("eats") all pointer input changes in [event] and records live telemetry for the action.
     */
    fun eat(event: PointerEvent, actionId: String? = null) {
        event.changes.forEach { it.consume() }
        val action = actionId?.let { getAction(it) }
        val situation = action?.situation ?: _pointerSituation.value ?: ShortcutSituation.Global
        val triggerDesc = actionId?.let { formatEffectiveTriggersCompact(it) } ?: "Pointer Event"
        val effectivePriority = actionId?.let { getEffectiveActionPriority(it) } ?: getEffectivePriority(situation)
        val info = EatenEventInfo(
            situation = situation,
            actionId = actionId ?: "unknown",
            triggerDescription = triggerDesc,
            priority = effectivePriority,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        _lastEatenEvent.value = info
        Logger.d { "Pointer event eaten by action '${actionId ?: "unknown"}' in situation '${situation.displayLabel}' (priority: $effectivePriority, mode: ${getPriorityMode()})" }
    }

    init {
        Logger.i { "ShortcutManager initialized with ${initialActions.size} actions" }
    }

    /**
     * Returns the action definition for the given unique ID.
     */
    fun getAction(actionId: String): ShortcutAction? = actionsMap[actionId]

    /**
     * Returns all actions for a specific situation / operational context.
     */
    fun getActionsForSituation(situation: ShortcutSituation): List<ShortcutAction> {
        return allActions.filter { it.situation == situation }
    }

    /**
     * Resolves all active triggers for [actionId], prioritizing custom user bindings
     * from settings over default triggers.
     */
    fun getEffectiveTriggers(actionId: String): List<ShortcutTrigger> {
        val action = actionsMap[actionId]
        val customBindings = settingsRepository?.settings?.value?.shortcuts?.customBindings?.get(actionId)
        return customBindings ?: action?.defaultTriggers ?: emptyList()
    }

    /**
     * Resolves the primary active trigger for [actionId].
     */
    fun getEffectiveTrigger(actionId: String): ShortcutTrigger {
        return getEffectiveTriggers(actionId).firstOrNull() ?: ShortcutTrigger()
    }

    /**
     * Returns true if the action's triggers have been customized by the user.
     */
    fun isCustomized(actionId: String): Boolean {
        val hasBindings = settingsRepository?.settings?.value?.shortcuts?.customBindings?.containsKey(actionId) == true
        val hasRelative = settingsRepository?.settings?.value?.shortcuts?.customRelativePriorities?.containsKey(actionId) == true
        return hasBindings || hasRelative
    }

    /**
     * Checks whether an input event matches any effective trigger of [actionId].
     */
    fun matches(
        actionId: String,
        isCtrl: Boolean,
        isShift: Boolean,
        isAlt: Boolean,
        isMeta: Boolean = false,
        button: ShortcutPointerButton = ShortcutPointerButton.None,
        gesture: ShortcutGesture = ShortcutGesture.None,
        key: ShortcutKey? = null
    ): Boolean {
        val triggers = getEffectiveTriggers(actionId)
        return triggers.any { trigger ->
            val modifiersMatch = trigger.matchesModifiers(ctrl = isCtrl, shift = isShift, alt = isAlt, meta = isMeta)
            val buttonMatch = trigger.pointerButton == button
            val gestureMatch = trigger.gesture == gesture
            val keyMatch = trigger.key == key
            modifiersMatch && buttonMatch && gestureMatch && keyMatch
        }
    }

    /**
     * Checks if a keyboard key event matches [actionId].
     */
    fun isKeyActionTriggered(
        actionId: String,
        key: Key,
        isCtrl: Boolean,
        isShift: Boolean,
        isAlt: Boolean,
        isMeta: Boolean = false
    ): Boolean {
        val shortcutKey = KeyMapping.fromComposeKey(key)
        return matches(
            actionId = actionId,
            isCtrl = isCtrl,
            isShift = isShift,
            isAlt = isAlt,
            isMeta = isMeta,
            button = ShortcutPointerButton.None,
            gesture = ShortcutGesture.None,
            key = shortcutKey
        )
    }

    /**
     * Convenience check for pointer events holding keyboard modifiers.
     */
    fun isActionTriggered(
        actionId: String,
        modifiers: PointerKeyboardModifiers,
        button: ShortcutPointerButton = ShortcutPointerButton.Left,
        gesture: ShortcutGesture = ShortcutGesture.Click,
        key: ShortcutKey? = null
    ): Boolean {
        return matches(
            actionId = actionId,
            isCtrl = modifiers.isCtrlPressed,
            isShift = modifiers.isShiftPressed,
            isAlt = modifiers.isAltPressed,
            isMeta = modifiers.isMetaPressed,
            button = button,
            gesture = gesture,
            key = key
        )
    }

    /**
     * Convenience check for pointer events with explicit modifier flags.
     */
    fun isActionTriggered(
        actionId: String,
        isCtrl: Boolean,
        isShift: Boolean,
        isAlt: Boolean,
        isMeta: Boolean = false,
        button: ShortcutPointerButton = ShortcutPointerButton.Left,
        gesture: ShortcutGesture = ShortcutGesture.Click,
        key: ShortcutKey? = null
    ): Boolean {
        return matches(
            actionId = actionId,
            isCtrl = isCtrl,
            isShift = isShift,
            isAlt = isAlt,
            isMeta = isMeta,
            button = button,
            gesture = gesture,
            key = key
        )
    }

    /**
     * Checks if a pointer event matches any effective trigger of [actionId] for the given [gesture].
     *
     * @param consume When true, automatically consumes ("eats") the event if matched and not already consumed.
     * @param allowConsumed When false (default), will not match if the event has already been consumed by another handler.
     */
    fun matchesPointer(
        actionId: String,
        event: PointerEvent,
        gesture: ShortcutGesture,
        consume: Boolean = false,
        allowConsumed: Boolean = false
    ): Boolean {
        if (!allowConsumed && isConsumed(event)) {
            Logger.v { "Action '$actionId' skipped: pointer event is already consumed" }
            return false
        }

        val isCtrl = event.keyboardModifiers.isCtrlPressed
        val isShift = event.keyboardModifiers.isShiftPressed
        val isAlt = event.keyboardModifiers.isAltPressed
        val isMeta = event.keyboardModifiers.isMetaPressed

        val button = when {
            event.buttons.isPrimaryPressed -> ShortcutPointerButton.Left
            event.buttons.isSecondaryPressed -> ShortcutPointerButton.Right
            event.buttons.isTertiaryPressed -> ShortcutPointerButton.Middle
            event.buttons.isBackPressed -> ShortcutPointerButton.Back
            event.buttons.isForwardPressed -> ShortcutPointerButton.Forward
            else -> ShortcutPointerButton.None
        }

        val matched = matches(
            actionId = actionId,
            isCtrl = isCtrl,
            isShift = isShift,
            isAlt = isAlt,
            isMeta = isMeta,
            button = button,
            gesture = gesture
        )

        if (matched && consume) {
            eat(event, actionId)
        }

        return matched
    }

    /**
     * Checks if an action matches and consumes ("eats") the pointer event in a single atomic call.
     */
    fun tryConsumePointer(
        actionId: String,
        event: PointerEvent,
        gesture: ShortcutGesture
    ): Boolean {
        return matchesPointer(actionId, event, gesture, consume = true, allowConsumed = false)
    }

    /**
     * Formats all active triggers for [actionId] separated by " / " (e.g. `[ Right + Drag ] / [ Middle + Drag ]`).
     */
    fun formatEffectiveTriggers(actionId: String): String {
        val triggers = getEffectiveTriggers(actionId)
        return if (triggers.isEmpty()) "[ None ]" else triggers.joinToString(" / ") { it.format() }
    }

    /**
     * Formats triggers in a clean compact string for badges and tooltips (e.g. "Right / Middle Drag" or "P / B").
     */
    fun formatEffectiveTriggersCompact(actionId: String): String {
        val triggers = getEffectiveTriggers(actionId)
        if (triggers.isEmpty()) return "None"
        return triggers.joinToString(" / ") {
            it.format().removePrefix("[ ").removeSuffix(" ]").replace(" + ", " ")
        }
    }

    /**
     * Formats the entire shortcut string strictly into:
     * `[ trigger1 ] / [ trigger2 ] (situation) behavior`
     */
    fun formatAction(actionId: String): String {
        val action = actionsMap[actionId] ?: return "[ Unknown ]"
        val triggerFormatted = formatEffectiveTriggers(actionId)
        val titleStr = action.title.resolveNonComposable()
        return "$triggerFormatted (${action.situation.displayLabel}) $titleStr"
    }

    /**
     * Detects conflicting shortcuts across registered actions within the same situation.
     */
    fun findConflicts(customBindingsOverride: Map<String, List<ShortcutTrigger>>? = null): List<ShortcutConflict> {
        val effectiveBindings = allActions.associate { action ->
            val custom = customBindingsOverride?.get(action.id)
                ?: settingsRepository?.settings?.value?.shortcuts?.customBindings?.get(action.id)
            action.id to (custom ?: action.defaultTriggers)
        }

        val conflicts = mutableListOf<ShortcutConflict>()

        for (i in allActions.indices) {
            val a1 = allActions[i]
            val t1List = effectiveBindings[a1.id] ?: a1.defaultTriggers
            for (j in i + 1 until allActions.size) {
                val a2 = allActions[j]
                val t2List = effectiveBindings[a2.id] ?: a2.defaultTriggers

                if (a1.situation == a2.situation) {
                    for (t1 in t1List) {
                        for (t2 in t2List) {
                            if (t1 == t2) {
                                conflicts.add(ShortcutConflict(a1, a2, a1.situation, t1))
                            }
                        }
                    }
                }
            }
        }
        return conflicts
    }

    /**
     * Checks if assigning [candidateTrigger] to [actionId] would produce a conflict
     * with another existing action in the same situation.
     */
    fun checkConflictForTrigger(actionId: String, candidateTrigger: ShortcutTrigger): ShortcutConflict? {
        val targetAction = actionsMap[actionId] ?: return null
        for (otherAction in allActions) {
            if (otherAction.id != actionId && otherAction.situation == targetAction.situation) {
                val otherTriggers = getEffectiveTriggers(otherAction.id)
                if (otherTriggers.any { it == candidateTrigger }) {
                    return ShortcutConflict(targetAction, otherAction, targetAction.situation, candidateTrigger)
                }
            }
        }
        return null
    }

    /**
     * Updates or rebinds an action to a new list of custom triggers.
     */
    fun updateBindings(actionId: String, triggers: List<ShortcutTrigger>) {
        settingsRepository?.updateSettings { current ->
            current.copy(
                shortcuts = current.shortcuts.copy(
                    customBindings = current.shortcuts.customBindings + (actionId to triggers)
                )
            )
        }
        Logger.i { "Updated shortcut bindings for $actionId: ${triggers.joinToString { it.format() }}" }
    }

    /**
     * Convenience method to update single trigger.
     */
    fun updateBinding(actionId: String, trigger: ShortcutTrigger) {
        updateBindings(actionId, listOf(trigger))
    }

    /**
     * Resets a specific action back to its default triggers.
     */
    fun resetBinding(actionId: String) {
        settingsRepository?.updateSettings { current ->
            current.copy(
                shortcuts = current.shortcuts.copy(
                    customBindings = current.shortcuts.customBindings - actionId,
                    customRelativePriorities = current.shortcuts.customRelativePriorities - actionId
                )
            )
        }
        Logger.i { "Reset shortcut binding and relative priority: $actionId to default" }
    }

    /**
     * Resets all customized shortcut bindings and relative priorities to their defaults.
     */
    fun resetAllToDefaults() {
        settingsRepository?.updateSettings { current ->
            current.copy(
                shortcuts = current.shortcuts.copy(
                    customBindings = emptyMap(),
                    customRelativePriorities = emptyMap()
                )
            )
        }
        Logger.i { "Reset all shortcuts and relative priorities to defaults" }
    }

    /**
     * Checks if actions across or within currently active situations share conflicting triggers,
     * including modifier subsumption / overlap (e.g. `Left Drag` vs `Ctrl + Left Drag`).
     * Uses total effective action priority (`zIndex + relativePriority` or `basePriority + relativePriority`)
     * to determine hierarchical shadowing (`isShadowed`).
     */
    fun findActiveZoneConflicts(): List<CrossZoneConflict> {
        val active = _activeSituations.value
        if (active.isEmpty()) return emptyList()

        val conflicts = mutableListOf<CrossZoneConflict>()
        val activeList = active.toList()

        // 1. Cross-zone conflicts between distinct active situations
        for (i in activeList.indices) {
            val s1 = activeList[i]
            val actions1 = getActionsForSituation(s1)

            for (j in i + 1 until activeList.size) {
                val s2 = activeList[j]
                val actions2 = getActionsForSituation(s2)

                for (a1 in actions1) {
                    val p1 = getEffectiveActionPriority(a1.id)
                    val t1List = getEffectiveTriggers(a1.id)
                    for (a2 in actions2) {
                        val p2 = getEffectiveActionPriority(a2.id)
                        val t2List = getEffectiveTriggers(a2.id)
                        for (t1 in t1List) {
                            for (t2 in t2List) {
                                if (t1.conflictsWith(t2, allowSubsumption = true)) {
                                    val isShadowed = p1 != p2
                                    val dominant = if (p1 >= p2) a1 else a2
                                    val shadowed = if (p1 >= p2) a2 else a1
                                    val conflict = CrossZoneConflict(
                                        actionA = a1,
                                        actionB = a2,
                                        triggerA = t1,
                                        triggerB = t2,
                                        isShadowed = isShadowed,
                                        dominantAction = dominant,
                                        shadowedAction = shadowed
                                    )
                                    conflicts.add(conflict)
                                    Logger.d {
                                        "Cross-zone conflict between ${a1.id} (${s1.displayLabel}, pri $p1) and ${a2.id} (${s2.displayLabel}, pri $p2) [isShadowed=$isShadowed]"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Intra-element / intra-zone conflicts within the same situation
        for (s in activeList) {
            val actions = getActionsForSituation(s)
            for (i in actions.indices) {
                val a1 = actions[i]
                val p1 = getEffectiveActionPriority(a1.id)
                val t1List = getEffectiveTriggers(a1.id)
                for (j in i + 1 until actions.size) {
                    val a2 = actions[j]
                    val p2 = getEffectiveActionPriority(a2.id)
                    val t2List = getEffectiveTriggers(a2.id)
                    for (t1 in t1List) {
                        for (t2 in t2List) {
                            if (t1.conflictsWith(t2, allowSubsumption = true)) {
                                val isShadowed = p1 != p2
                                val dominant = if (p1 >= p2) a1 else a2
                                val shadowed = if (p1 >= p2) a2 else a1
                                val conflict = CrossZoneConflict(
                                    actionA = a1,
                                    actionB = a2,
                                    triggerA = t1,
                                    triggerB = t2,
                                    isShadowed = isShadowed,
                                    dominantAction = dominant,
                                    shadowedAction = shadowed
                                )
                                conflicts.add(conflict)
                                Logger.d {
                                    "Intra-zone conflict between ${a1.id} (pri $p1) and ${a2.id} (pri $p2) in ${s.displayLabel} [isShadowed=$isShadowed]"
                                }
                            }
                        }
                    }
                }
            }
        }

        return conflicts
    }
}

/**
 * Represents a trigger collision or overlap between two actions in distinct situations that are both active.
 *
 * @param isShadowed True when the two situations have differing effective priorities, meaning the higher-priority
 * situation ("dominantAction") consumes the event before the lower-priority situation ("shadowedAction") can receive it.
 */
data class CrossZoneConflict(
    val actionA: ShortcutAction,
    val actionB: ShortcutAction,
    val triggerA: ShortcutTrigger,
    val triggerB: ShortcutTrigger = triggerA,
    val isShadowed: Boolean = false,
    val dominantAction: ShortcutAction = actionA,
    val shadowedAction: ShortcutAction = actionB
) {
    val trigger: ShortcutTrigger get() = triggerA
    val isExact: Boolean get() = triggerA == triggerB
}
