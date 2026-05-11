package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides shared, per-plugin mutexes to prevent deadlocks from nested locks
 * across different components (e.g., Coordinator and Manager).
 */
class PluginLockProvider {
    private val pluginMutexes = ConcurrentHashMap<String, Mutex>()

    /**
     * Gets or creates a Mutex for the given plugin package.
     */
    fun getMutex(pkg: String): Mutex {
        return pluginMutexes.getOrPut(pkg) { Mutex() }
    }
}
