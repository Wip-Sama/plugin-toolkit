package org.wip.plugintoolkit.core.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

actual suspend fun <T> runInterruptibleIO(block: () -> T): T {
    return runInterruptible(Dispatchers.IO) {
        block()
    }
}
