package org.wip.plugintoolkit.features.shortcuts.engine

import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutScope

/**
 * Dispatches keyboard events hierarchically across focus scopes.
 */
interface ShortcutDispatcher {
    /**
     * Resolves matching command ID for a key event within [activeScope] or ancestor scopes.
     * Returns the command ID if matched, or null if no command matches.
     */
    fun resolveMatchingCommand(
        key: ShortcutKey,
        isCtrl: Boolean,
        isShift: Boolean,
        isAlt: Boolean,
        isMeta: Boolean,
        activeScope: ShortcutScope
    ): String?
}
