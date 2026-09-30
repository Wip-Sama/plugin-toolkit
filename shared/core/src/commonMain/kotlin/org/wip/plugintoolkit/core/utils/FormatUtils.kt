package org.wip.plugintoolkit.core.utils

import kotlin.math.roundToLong

/**
 * Utility functions for formatting values and data sizes.
 */
object FormatUtils {

    /**
     * Formats a byte count into a human-readable string representation (e.g., "14.2 MB", "512.0 KB").
     *
     * @param bytes The number of bytes to format.
     * @return A formatted string with appropriate units.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024.0 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }
        val rounded = (size * 10.0).roundToLong() / 10.0
        return "$rounded ${units[unitIndex]}"
    }

    /**
     * Formats a network throughput rate (bytes per second) into a human-readable string (e.g., "1.5 MB/s").
     *
     * @param bytesPerSec The throughput rate in bytes per second.
     * @return Formatted throughput string.
     */
    fun formatThroughput(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 B/s"
        return "${formatFileSize(bytesPerSec)}/s"
    }

    /**
     * Formats a numeric progress value cleanly without trailing zeros when integer (e.g., "12.3", "5").
     */
    fun formatProgressValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            val rounded = (value * 10.0).roundToLong() / 10.0
            rounded.toString()
        }
    }
}
