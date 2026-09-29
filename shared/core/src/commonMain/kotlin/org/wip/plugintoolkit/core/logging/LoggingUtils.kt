package org.wip.plugintoolkit.core.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.LogLevel

/**
 * Maps application [LogLevel] to Kermit [Severity].
 */
fun LogLevel.toSeverity(): Severity = when (this) {
    LogLevel.Verbose -> Severity.Verbose
    LogLevel.Debug -> Severity.Debug
    LogLevel.Info -> Severity.Info
    LogLevel.Warn -> Severity.Warn
    LogLevel.Error -> Severity.Error
    LogLevel.Assert -> Severity.Assert
}

/**
 * Synchronizes the Kermit [Logger] minimum severity with changes to [AppSettings.logging.level].
 *
 * @param settings Flow of application settings to observe.
 * @param scope CoroutineScope in which the settings collection job will run.
 * @param onSeverityChanged Callback invoked when severity changes, defaulting to updating [Logger.setMinSeverity].
 * @return The background [Job] observing setting changes.
 */
fun syncLoggerSeverity(
    settings: Flow<AppSettings>,
    scope: CoroutineScope,
    onSeverityChanged: (Severity) -> Unit = { Logger.setMinSeverity(it) }
): Job {
    return settings
        .map { it.logging.level }
        .distinctUntilChanged()
        .onEach { level ->
            onSeverityChanged(level.toSeverity())
        }
        .launchIn(scope)
}
