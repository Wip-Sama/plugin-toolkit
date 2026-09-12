package org.wip.plugintoolkit.features.shortcuts.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.core.model.LocalizedString

/**
 * Representation of any keyboard key (standard letters, digits, functional, editing, or navigation).
 * Supports ANY key code to allow arbitrary user keyboard bindings.
 */
@Serializable
data class ShortcutKey(
    val code: String,
    val label: String = code
) {
    companion object {
        // Alphabet
        val A = ShortcutKey("A", "A")
        val B = ShortcutKey("B", "B")
        val C = ShortcutKey("C", "C")
        val D = ShortcutKey("D", "D")
        val E = ShortcutKey("E", "E")
        val F = ShortcutKey("F", "F")
        val G = ShortcutKey("G", "G")
        val H = ShortcutKey("H", "H")
        val I = ShortcutKey("I", "I")
        val J = ShortcutKey("J", "J")
        val K = ShortcutKey("K", "K")
        val L = ShortcutKey("L", "L")
        val M = ShortcutKey("M", "M")
        val N = ShortcutKey("N", "N")
        val O = ShortcutKey("O", "O")
        val P = ShortcutKey("P", "P")
        val Q = ShortcutKey("Q", "Q")
        val R = ShortcutKey("R", "R")
        val S = ShortcutKey("S", "S")
        val T = ShortcutKey("T", "T")
        val U = ShortcutKey("U", "U")
        val V = ShortcutKey("V", "V")
        val W = ShortcutKey("W", "W")
        val X = ShortcutKey("X", "X")
        val Y = ShortcutKey("Y", "Y")
        val Z = ShortcutKey("Z", "Z")

        // Numbers
        val Zero = ShortcutKey("0", "0")
        val One = ShortcutKey("1", "1")
        val Two = ShortcutKey("2", "2")
        val Three = ShortcutKey("3", "3")
        val Four = ShortcutKey("4", "4")
        val Five = ShortcutKey("5", "5")
        val Six = ShortcutKey("6", "6")
        val Seven = ShortcutKey("7", "7")
        val Eight = ShortcutKey("8", "8")
        val Nine = ShortcutKey("9", "9")

        // Function Keys
        val F1 = ShortcutKey("F1", "F1")
        val F2 = ShortcutKey("F2", "F2")
        val F3 = ShortcutKey("F3", "F3")
        val F4 = ShortcutKey("F4", "F4")
        val F5 = ShortcutKey("F5", "F5")
        val F6 = ShortcutKey("F6", "F6")
        val F7 = ShortcutKey("F7", "F7")
        val F8 = ShortcutKey("F8", "F8")
        val F9 = ShortcutKey("F9", "F9")
        val F10 = ShortcutKey("F10", "F10")
        val F11 = ShortcutKey("F11", "F11")
        val F12 = ShortcutKey("F12", "F12")

        // Editing & Navigation
        val Escape = ShortcutKey("Escape", "Esc")
        val Delete = ShortcutKey("Delete", "Del")
        val Backspace = ShortcutKey("Backspace", "Backspace")
        val Enter = ShortcutKey("Enter", "Enter")
        val Space = ShortcutKey("Space", "Space")
        val Tab = ShortcutKey("Tab", "Tab")
        val Insert = ShortcutKey("Insert", "Ins")
        val Home = ShortcutKey("Home", "Home")
        val End = ShortcutKey("End", "End")
        val PageUp = ShortcutKey("PageUp", "PageUp")
        val PageDown = ShortcutKey("PageDown", "PageDown")
        val ArrowUp = ShortcutKey("ArrowUp", "↑")
        val ArrowDown = ShortcutKey("ArrowDown", "↓")
        val ArrowLeft = ShortcutKey("ArrowLeft", "←")
        val ArrowRight = ShortcutKey("ArrowRight", "→")

        /** Creates or resolves a [ShortcutKey] from an arbitrary code or label. */
        fun fromCode(code: String, label: String = code): ShortcutKey = ShortcutKey(code = code, label = label)
    }
}

/**
 * Mouse button identifier involved in a shortcut trigger.
 */
@Serializable
enum class ShortcutPointerButton(val displayName: String) {
    None("None"),
    Left("Left"),
    Right("Right"),
    Middle("Middle"),
    Back("Back"),
    Forward("Forward")
}

/**
 * Type of gesture executed with mouse or pointer.
 */
@Serializable
enum class ShortcutGesture(val displayName: String) {
    None("None"),
    Click("Click"),
    DoubleClick("Double-Click"),
    Drag("Drag"),
    Press("Press"),
    Release("Release"),
    Wheel("Wheel")
}

/**
 * Operational context/situation where a shortcut is active.
 * Each situation has a [defaultPriority] defined in [ShortcutPriorities].
 */
@Serializable
enum class ShortcutSituation(
    val displayLabel: String,
    val defaultPriority: Int = ShortcutPriorities.Global.BASE
) {
    Global("Global", ShortcutPriorities.Global.BASE),
    FlowBoard("Flow Canvas", ShortcutPriorities.Flow.BOARD),
    FlowConnectionPoint("Connection Point", ShortcutPriorities.Flow.CONNECTION_POINT),
    FlowNode("Flow Node", ShortcutPriorities.Flow.NODE),
    FlowSelection("Flow Selection", ShortcutPriorities.Flow.SELECTION),
    FlowWire("Flow Wire", ShortcutPriorities.Flow.WIRE),
    JobTerminal("Job Terminal", ShortcutPriorities.Tools.JOB_TERMINAL),
    Settings("Settings", ShortcutPriorities.Tools.SETTINGS);

    val priority: Int get() = defaultPriority
}

/**
 * Telemetry record of a pointer or key event that was intercepted and consumed ("eaten") by a shortcut action.
 */
@Serializable
data class EatenEventInfo(
    val situation: ShortcutSituation,
    val actionId: String,
    val triggerDescription: String,
    val priority: Float = situation.priority.toFloat(),
    val timestamp: Long = 0L
)

/**
 * Complete combination of keyboard modifiers, optional key, pointer button, and pointer gesture.
 */
@Serializable
data class ShortcutTrigger(
    val key: ShortcutKey? = null,
    val isCtrl: Boolean = false,
    val isShift: Boolean = false,
    val isAlt: Boolean = false,
    val isMeta: Boolean = false,
    val pointerButton: ShortcutPointerButton = ShortcutPointerButton.None,
    val gesture: ShortcutGesture = ShortcutGesture.None
) {
    val isKeyboardOnly: Boolean
        get() = key != null && pointerButton == ShortcutPointerButton.None && gesture == ShortcutGesture.None

    val isPointerOnly: Boolean
        get() = key == null && (pointerButton != ShortcutPointerButton.None || gesture != ShortcutGesture.None)

    val isHybrid: Boolean
        get() = key != null && (pointerButton != ShortcutPointerButton.None || gesture != ShortcutGesture.None)

    /**
     * Formats the trigger into the canonical representation:
     * `[ Shift + Left + Click ]`, `[ P ]`, `[ Ctrl + Z ]`, `[ Right + Drag ]`, `[ L + Left + Click ]`
     */
    fun format(): String {
        val parts = mutableListOf<String>()
        if (isCtrl) parts.add("Ctrl")
        if (isAlt) parts.add("Alt")
        if (isShift) parts.add("Shift")
        if (isMeta) parts.add("Cmd")

        if (key != null) {
            parts.add(key.label)
        }

        when (pointerButton) {
            ShortcutPointerButton.Left -> parts.add("Left")
            ShortcutPointerButton.Right -> parts.add("Right")
            ShortcutPointerButton.Middle -> parts.add("Middle")
            ShortcutPointerButton.Back -> parts.add("Back")
            ShortcutPointerButton.Forward -> parts.add("Forward")
            ShortcutPointerButton.None -> {}
        }

        when (gesture) {
            ShortcutGesture.Click -> parts.add("Click")
            ShortcutGesture.DoubleClick -> parts.add("Double-Click")
            ShortcutGesture.Drag -> parts.add("Drag")
            ShortcutGesture.Press -> parts.add("Press")
            ShortcutGesture.Release -> parts.add("Release")
            ShortcutGesture.Wheel -> parts.add("Wheel")
            ShortcutGesture.None -> {}
        }

        return if (parts.isEmpty()) "[ None ]" else "[ ${parts.joinToString(" + ")} ]"
    }

    /**
     * Checks whether the given modifier flags match this trigger's configured modifiers.
     */
    fun matchesModifiers(
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean = false
    ): Boolean {
        return isCtrl == ctrl && isShift == shift && isAlt == alt && isMeta == meta
    }

    /**
     * Checks whether this trigger conflicts with [other].
     *
     * @param allowSubsumption When true, triggers that share the same button/gesture/key
     * where one's active modifier set is a subset of the other's (e.g. `Left Drag` vs `Ctrl + Left Drag`)
     * are reported as conflicting, as the unadorned/sub-trigger could fire alongside or shadow
     * the more specific shortcut during user input.
     */
    fun conflictsWith(other: ShortcutTrigger, allowSubsumption: Boolean = false): Boolean {
        if (pointerButton != other.pointerButton || gesture != other.gesture) return false
        if (key != other.key) return false
        if (pointerButton == ShortcutPointerButton.None && gesture == ShortcutGesture.None && key == null) {
            return false
        }

        val exactModifiersMatch = isCtrl == other.isCtrl &&
            isShift == other.isShift &&
            isAlt == other.isAlt &&
            isMeta == other.isMeta

        if (exactModifiersMatch) return true

        if (allowSubsumption) {
            val m1 = mutableSetOf<String>()
            if (isCtrl) m1.add("ctrl")
            if (isShift) m1.add("shift")
            if (isAlt) m1.add("alt")
            if (isMeta) m1.add("meta")

            val m2 = mutableSetOf<String>()
            if (other.isCtrl) m2.add("ctrl")
            if (other.isShift) m2.add("shift")
            if (other.isAlt) m2.add("alt")
            if (other.isMeta) m2.add("meta")

            return m1.containsAll(m2) || m2.containsAll(m1)
        }

        return false
    }
}

/**
 * Specifies the input medium/method required by an action.
 * Decided by the action declaration and immutable by the user.
 */
@Serializable
enum class ShortcutInputMode {
    Keyboard,
    Pointer,
    Hybrid
}

/**
 * Definition of an action that can be bound to one or more shortcut triggers.
 */
data class ShortcutAction(
    val id: String,
    val title: LocalizedString,
    val description: LocalizedString,
    val situation: ShortcutSituation,
    val defaultTriggers: List<ShortcutTrigger>,
    val inputMode: ShortcutInputMode = defaultTriggers.firstOrNull()?.let {
        when {
            it.isHybrid -> ShortcutInputMode.Hybrid
            it.isPointerOnly -> ShortcutInputMode.Pointer
            else -> ShortcutInputMode.Keyboard
        }
    } ?: ShortcutInputMode.Keyboard,
    val isConfigurable: Boolean = true,
    val relativePriority: Int = ShortcutPriorities.Relative.NORMAL
) {
    /** Primary trigger convenience accessor. */
    val defaultTrigger: ShortcutTrigger
        get() = defaultTriggers.firstOrNull() ?: ShortcutTrigger()
}

/**
 * Standard identifier constants for shortcuts across the application.
 */
object ShortcutActionId {
    const val SKIP_CONFIRMATION = "global.skip_confirmation"

    // Flow Editor Canvas & Board
    const val FLOW_PAN_CANVAS = "flow.board.pan"
    const val FLOW_ZOOM_CANVAS = "flow.board.zoom"
    const val FLOW_SELECT_NODE = "flow.board.select_node"
    const val FLOW_BOX_SELECT = "flow.board.box_select"
    const val FLOW_STRUCTURED_MODE = "flow.board.structured_mode"
    const val FLOW_PAINT_TOOL = "flow.board.paint_tool"
    const val FLOW_WASH_TOOL = "flow.board.wash_tool"
    const val FLOW_EYEDROPPER = "flow.board.eyedropper"
    const val FLOW_UNDO = "flow.board.undo"
    const val FLOW_REDO = "flow.board.redo"

    // Flow Selection
    const val FLOW_MOVE_NODE = "flow.selection.move"
    const val FLOW_DELETE_SELECTED = "flow.selection.delete"

    // Flow Connection Points
    const val FLOW_CONNECT_PORT = "flow.point.connect"
    const val FLOW_DETACH_CONNECTION = "flow.point.detach"
    const val FLOW_BRANCH_WIRE = "flow.point.branch"
    const val FLOW_MOVE_POINT = "flow.point.move"
    const val FLOW_CREATE_RAMIFICATION = "flow.point.ramification"

    // Other System Shortcuts
    const val JOB_FORCE_CANCEL = "job.force_cancel"
    const val SETTINGS_SCALE_2X_STEP = "settings.scale_2x_step"
}

/**
 * Strategy mode used to calculate effective priorities for shortcut actions.
 */
@Serializable
enum class ShortcutPriorityMode {
    /**
     * Combines dynamic Compose z-index elevation, base situation priority, and relative action priority.
     */
    ZIndexAndPriority,

    /**
     * Relies strictly on predefined situation priority and relative action priority without dynamic Compose elevation.
     */
    PriorityOnly
}

/**
 * Represents a conflict where two different actions within the same situation
 * share the exact same trigger.
 */
data class ShortcutConflict(
    val action1: ShortcutAction,
    val action2: ShortcutAction,
    val situation: ShortcutSituation,
    val trigger: ShortcutTrigger
)

/**
 * Serializable settings storing user-defined overrides for shortcuts.
 * Supports multiple triggers per action, priority resolution strategy, and action-level relative priorities.
 */
@Serializable
data class ShortcutSettings(
    val customBindings: Map<String, List<ShortcutTrigger>> = emptyMap(),
    val priorityMode: ShortcutPriorityMode = ShortcutPriorityMode.ZIndexAndPriority,
    val customRelativePriorities: Map<String, Int> = emptyMap()
)
