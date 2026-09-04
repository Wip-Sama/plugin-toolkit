package org.wip.plugintoolkit.core.utils

import kotlin.math.roundToLong

actual object MemoryUtils {
    actual fun getCurrentMemoryUsageBytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    }

    actual fun getMaxMemoryBytes(): Long {
        return Runtime.getRuntime().maxMemory()
    }

    actual fun formatMemoryBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0

        return when {
            bytes >= gb -> {
                val value = (bytes / gb * 10.0).roundToLong() / 10.0
                "${value} GB"
            }
            bytes >= mb -> {
                val value = (bytes / mb * 10.0).roundToLong() / 10.0
                "${value} MB"
            }
            bytes >= kb -> {
                val value = (bytes / kb * 10.0).roundToLong() / 10.0
                "${value} KB"
            }
            else -> "$bytes B"
        }
    }
}
