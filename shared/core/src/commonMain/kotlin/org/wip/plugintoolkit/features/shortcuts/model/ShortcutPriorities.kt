package org.wip.plugintoolkit.features.shortcuts.model

/**
 * Centralized catalog of shortcut situation priorities ("wall of values").
 *
 * Higher values take precedence over lower values. When multiple situations
 * are active simultaneously, higher-priority situations "eat" pointer and key
 * events, deliberately shadowing triggers in lower-priority or background situations.
 */
object ShortcutPriorities {
    /**
     * Root-level / application-wide priorities.
     */
    object Global {
        const val BASE = 0
        const val MODAL = 50
    }

    /**
     * Flow Canvas board priorities.
     * Elements with higher visual z-index / elevation receive higher priority.
     */
    object Flow {
        const val BOARD = 10
        const val SELECTION = 20
        const val WIRE = 30
        const val NODE = 30
        const val CONNECTION_POINT = 40
    }

    /**
     * Dedicated tool / dialog priorities.
     */
    object Tools {
        const val JOB_TERMINAL = 50
        const val SETTINGS = 50
    }

    /**
     * Relative priorities for actions within the same situation or element.
     */
    object Relative {
        const val DEFAULT = 0
        const val LOW = -5
        const val NORMAL = 0
        const val HIGH = 5
        const val HIGHEST = 10
    }
}
