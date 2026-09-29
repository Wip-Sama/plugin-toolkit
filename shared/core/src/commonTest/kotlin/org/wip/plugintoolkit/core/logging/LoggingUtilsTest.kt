package org.wip.plugintoolkit.core.logging

import co.touchlab.kermit.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.LogLevel
import org.wip.plugintoolkit.features.settings.model.LoggingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingUtilsTest {

    @Test
    fun testLogLevelToSeverityMapping() {
        assertEquals(Severity.Verbose, LogLevel.Verbose.toSeverity())
        assertEquals(Severity.Debug, LogLevel.Debug.toSeverity())
        assertEquals(Severity.Info, LogLevel.Info.toSeverity())
        assertEquals(Severity.Warn, LogLevel.Warn.toSeverity())
        assertEquals(Severity.Error, LogLevel.Error.toSeverity())
        assertEquals(Severity.Assert, LogLevel.Assert.toSeverity())
    }

    @Test
    fun testSyncLoggerSeverityUpdatesDynamicallyOnSettingsChange() = runTest {
        val initialSettings = AppSettings(
            logging = LoggingSettings(level = LogLevel.Info)
        )
        val settingsFlow = MutableStateFlow(initialSettings)
        val observedSeverities = mutableListOf<Severity>()

        val job = syncLoggerSeverity(
            settings = settingsFlow,
            scope = backgroundScope,
            onSeverityChanged = { severity ->
                observedSeverities.add(severity)
            }
        )

        runCurrent()
        assertEquals(listOf(Severity.Info), observedSeverities)

        // Change level to Debug without restarting
        settingsFlow.value = settingsFlow.value.copy(
            logging = settingsFlow.value.logging.copy(level = LogLevel.Debug)
        )
        runCurrent()
        assertEquals(listOf(Severity.Info, Severity.Debug), observedSeverities)

        // Change level to Verbose
        settingsFlow.value = settingsFlow.value.copy(
            logging = settingsFlow.value.logging.copy(level = LogLevel.Verbose)
        )
        runCurrent()
        assertEquals(listOf(Severity.Info, Severity.Debug, Severity.Verbose), observedSeverities)

        // Updating unrelated setting does not emit duplicate severity change
        settingsFlow.value = settingsFlow.value.copy(
            logging = settingsFlow.value.logging.copy(logsToKeep = 10)
        )
        runCurrent()
        assertEquals(listOf(Severity.Info, Severity.Debug, Severity.Verbose), observedSeverities)

        // Change level to Error
        settingsFlow.value = settingsFlow.value.copy(
            logging = settingsFlow.value.logging.copy(level = LogLevel.Error)
        )
        runCurrent()
        assertEquals(listOf(Severity.Info, Severity.Debug, Severity.Verbose, Severity.Error), observedSeverities)

        job.cancel()
    }
}
