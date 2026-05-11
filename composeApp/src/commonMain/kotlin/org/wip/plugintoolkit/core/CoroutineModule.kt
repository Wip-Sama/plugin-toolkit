package org.wip.plugintoolkit.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val coroutineModule = module {
    /**
     * AppScope is tied to Dispatchers.Default.
     * Use this for CPU-intensive background tasks (e.g., parsing, logic, calculations).
     */
    single(named("AppScope")) { 
        CoroutineScope(SupervisorJob() + Dispatchers.Default) 
    } onClose { 
        it?.cancel() 
    }

    /**
     * LoomDispatcher for injection. All blocking/I/O work should use this.
     */
    single(named("LoomDispatcher")) { loomDispatcher }

    /**
     * LoomScope is tied to Virtual Threads (Project Loom).
     * Use this for massive concurrency and blocking I/O in plugins.
     */
    single(named("LoomScope")) {
        CoroutineScope(SupervisorJob() + loomDispatcher)
    } onClose {
        it?.cancel()
    }
}

/**
 * Platform-specific dispatcher for Virtual Threads.
 */
expect val loomDispatcher: CoroutineDispatcher
