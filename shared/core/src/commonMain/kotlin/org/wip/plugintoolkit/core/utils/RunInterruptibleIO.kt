package org.wip.plugintoolkit.core.utils

/**
 * Executes the given blocking block on [kotlinx.coroutines.Dispatchers.IO] with platform-specific thread interruption support.
 * On JVM targets, this delegates to [kotlinx.coroutines.runInterruptible] which invokes Thread.interrupt() when cancelled.
 */
expect suspend fun <T> runInterruptibleIO(block: () -> T): T
