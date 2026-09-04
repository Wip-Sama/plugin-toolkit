package org.wip.plugintoolkit.core.utils

expect object MemoryUtils {
    fun getCurrentMemoryUsageBytes(): Long
    fun getMaxMemoryBytes(): Long
    fun formatMemoryBytes(bytes: Long): String
}
