package org.wip.plugintoolkit.features.controls.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.core.utils.FileUtils

/**
 * Discrete pointer buttons supported by the input scheme.
 */
@Serializable
enum class PointerButton(val displayName: String) {
    Primary("Left"),
    Secondary("Right"),
    Tertiary("Middle"),
    Back("Back"),
    Forward("Forward");

    companion object {
        fun fromButtons(
            isPrimary: Boolean,
            isSecondary: Boolean,
            isTertiary: Boolean,
            isBack: Boolean = false,
            isForward: Boolean = false
        ): PointerButton? = when {
            isPrimary && !isSecondary && !isTertiary -> Primary
            isSecondary -> Secondary
            isTertiary -> Tertiary
            isBack -> Back
            isForward -> Forward
            else -> null
        }
    }
}

/**
 * Specific button and modifier combination required to activate a continuous spatial gesture.
 */
@Serializable
data class PointerBinding(
    val button: PointerButton,
    val requireCtrl: Boolean = false,
    val requireShift: Boolean = false,
    val requireAlt: Boolean = false,
    val requireMeta: Boolean = false
) {
    companion object {
        /**
         * Resolves a pointer binding using the platform's primary command modifier (Cmd on macOS, Ctrl elsewhere).
         */
        fun primary(
            button: PointerButton,
            shift: Boolean = false,
            alt: Boolean = false,
            isMac: Boolean = FileUtils.isMac
        ): PointerBinding = PointerBinding(
            button = button,
            requireCtrl = !isMac,
            requireMeta = isMac,
            requireShift = shift,
            requireAlt = alt
        )
    }

    fun matches(
        testButton: PointerButton,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean
    ): Boolean = this.button == testButton &&
        this.requireCtrl == ctrl &&
        this.requireShift == shift &&
        this.requireAlt == alt &&
        this.requireMeta == meta

    fun format(): String {
        val parts = mutableListOf<String>()
        if (requireCtrl) parts.add("Ctrl")
        if (requireAlt) parts.add("Alt")
        if (requireShift) parts.add("Shift")
        if (requireMeta) parts.add("Cmd")
        parts.add(button.displayName)
        return if (parts.isEmpty()) "[ None ]" else "[ ${parts.joinToString(" + ")} ]"
    }
}

/**
 * Configuration profile for continuous spatial canvas interactions.
 */
@Serializable
data class CanvasControlScheme(
    val name: String = "Default",
    val panBindings: List<PointerBinding> = listOf(
        PointerBinding(PointerButton.Secondary),
        PointerBinding(PointerButton.Tertiary)
    ),
    val boxSelectBinding: PointerBinding = PointerBinding(PointerButton.Primary),
    val toggleSelectBinding: PointerBinding = PointerBinding(
        button = PointerButton.Primary,
        requireCtrl = !FileUtils.isMac,
        requireMeta = FileUtils.isMac
    ),
    val zoomWithWheel: Boolean = true,
    val zoomRequiresCtrl: Boolean = false,
    val invertZoomDirection: Boolean = false,
    val zoomSensitivity: Float = 1.0f
) {
    fun isPanTriggered(button: PointerButton, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Boolean =
        panBindings.any { it.matches(button, ctrl, shift, alt, meta) }

    fun isBoxSelectTriggered(button: PointerButton, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Boolean =
        boxSelectBinding.matches(button, ctrl, shift, alt, meta)

    fun isToggleSelectTriggered(button: PointerButton, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Boolean =
        toggleSelectBinding.matches(button, ctrl, shift, alt, meta)

    fun shouldZoom(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Boolean {
        if (!zoomWithWheel) return false
        if (zoomRequiresCtrl) {
            val hasPrimary = if (FileUtils.isMac) meta else ctrl
            return hasPrimary
        }
        return true
    }
}
