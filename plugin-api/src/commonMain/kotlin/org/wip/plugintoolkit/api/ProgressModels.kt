package org.wip.plugintoolkit.api

import kotlinx.serialization.Serializable

/**
 * Display mode for progress bars.
 */
@Serializable
enum class ProgressDisplayMode {
    /**
     * Standard percentage progress (e.g. 75%).
     */
    PERCENTAGE,

    /**
     * Value ratio progress (e.g. 12.3 / 14.5 MB or 4 / 10 items).
     */
    RATIO,

    /**
     * Indeterminate progress bar (e.g. spinner/continuous animation during unknown duration tasks).
     */
    INDETERMINATE
}

/**
 * Structured progress data payload containing progress fractions, current/total metrics,
 * units, dynamic status messages, and display preferences.
 */
@Serializable
data class ProgressData(
    val fraction: Float? = null,
    val current: Double? = null,
    val total: Double? = null,
    val unit: String? = null,
    val message: String? = null,
    val displayMode: ProgressDisplayMode = ProgressDisplayMode.PERCENTAGE
) {
    companion object {
        /**
         * Create standard percentage progress.
         *
         * @param fraction Progress fraction between 0.0 and 1.0.
         * @param message Optional dynamic message (e.g. "Downloading: 2.5 MB/s").
         */
        fun percentage(fraction: Float, message: String? = null): ProgressData =
            ProgressData(
                fraction = fraction.coerceIn(0f, 1f),
                message = message,
                displayMode = ProgressDisplayMode.PERCENTAGE
            )

        /**
         * Create ratio progress (e.g. 12.3 / 14.5 MB).
         *
         * @param current Current value processed.
         * @param total Total target value.
         * @param unit Unit label (e.g. "MB", "items", "files").
         * @param message Optional dynamic message.
         */
        fun ratio(
            current: Double,
            total: Double,
            unit: String? = null,
            message: String? = null
        ): ProgressData {
            val fraction = if (total > 0.0) (current / total).toFloat().coerceIn(0f, 1f) else 0f
            return ProgressData(
                fraction = fraction,
                current = current,
                total = total,
                unit = unit,
                message = message,
                displayMode = ProgressDisplayMode.RATIO
            )
        }

        /**
         * Create indeterminate progress.
         *
         * @param message Optional dynamic message (e.g. "Waiting for rate limit reset...").
         */
        fun indeterminate(message: String? = null): ProgressData =
            ProgressData(
                fraction = null,
                message = message,
                displayMode = ProgressDisplayMode.INDETERMINATE
            )
    }
}
