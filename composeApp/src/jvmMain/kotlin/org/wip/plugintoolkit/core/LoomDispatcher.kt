package org.wip.plugintoolkit.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * JVM implementation of the Virtual Thread dispatcher.
 */
actual val loomDispatcher: CoroutineDispatcher = 
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
