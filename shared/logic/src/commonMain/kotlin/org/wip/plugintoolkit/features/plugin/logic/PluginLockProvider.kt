package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.sync.Mutex

/**
 * Provides shared, per-plugin mutexes to prevent deadlocks from nested locks
 * across different components (e.g., Coordinator and Manager).
 */
class PluginLockProvider {
    private val pluginMutexes = atomic(persistentMapOf<String, Mutex>())

    /**
     * Gets or creates a Mutex for the given plugin package.
     */
    fun getMutex(pkg: String): Mutex {
        pluginMutexes.value[pkg]?.let { return it }
        val newMutex = Mutex()
        pluginMutexes.update { map ->
            if (map.containsKey(pkg)) map else map.put(pkg, newMutex)
        }
        return pluginMutexes.value.getValue(pkg)
    }
}
