package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.CapabilityExecutionMetric
import org.wip.plugintoolkit.features.job.model.JobExecutionMetrics
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.ui.CardButtonState
import org.wip.plugintoolkit.features.plugin.ui.PluginStatusAction

class JobResultCardTest {

    @Test
    fun testFormatDurationSubSecond() {
        assertEquals("0ms", formatDuration(0L))
        assertEquals("450ms", formatDuration(450L))
        assertEquals("999ms", formatDuration(999L))
    }

    @Test
    fun testFormatDurationSeconds() {
        assertEquals("1.0s", formatDuration(1000L))
        assertEquals("2.5s", formatDuration(2500L))
        assertEquals("59.9s", formatDuration(59900L))
    }

    @Test
    fun testFormatDurationMinutes() {
        assertEquals("1m 0s", formatDuration(60000L))
        assertEquals("1m 5s", formatDuration(65000L))
        assertEquals("2m 30s", formatDuration(150000L))
    }

    @Test
    fun testBuildJobExportReportWithMetricsAndLogs() {
        val testStart = Instant.fromEpochMilliseconds(1700000000000L)
        val testEnd = Instant.fromEpochMilliseconds(1700000005000L)

        val job = BackgroundJob(
            id = "test-job-12345",
            name = "Test Job",
            type = JobType.Capability,
            status = JobStatus.Completed,
            enqueuedAt = testStart,
            startedAt = testStart,
            completedAt = testEnd,
            pluginId = "org.wip.testplugin",
            capabilityName = "runCalculation",
            result = "{\"result\": 42}",
            executionMetrics = JobExecutionMetrics(
                startedAt = testStart,
                completedAt = testEnd,
                totalDurationMs = 5000L,
                memoryUsageBytes = 1024L * 1024L * 16L,
                capabilityMetrics = listOf(
                    CapabilityExecutionMetric(
                        capabilityName = "runCalculation",
                        durationMs = 5000L,
                        memoryUsageBytes = 1024L * 1024L * 16L
                    )
                )
            )
        )

        val logs = listOf("Starting job...", "Calculated 42", "Finished job.")

        val report = buildJobExportReport(
            job = job,
            logs = logs,
            startedAtLabel = "Started",
            completedAtLabel = "Completed",
            durationLabel = "Duration",
            memoryLabel = "Peak Memory",
            capabilityBreakdownLabel = "Capabilities",
            totalMemoryLabel = "Total Memory"
        )

        assertTrue(report.contains("JOB EXECUTION REPORT"))
        assertTrue(report.contains("Job ID:         test-job-12345"))
        assertTrue(report.contains("Status:         Completed"))
        assertTrue(report.contains("Duration: 5000 ms (5.0s)"))
        assertTrue(report.contains("Starting job..."))
        assertTrue(report.contains("Calculated 42"))
        assertTrue(report.contains("CONSOLE LOGS (3 lines)"))
        assertTrue(report.contains("RESULT"))
        assertTrue(report.contains("{\"result\": 42}"))
    }

    @Test
    fun testCardButtonState() {
        val state1 = CardButtonState(
            reqAction = null,
            hasUpdate = false,
            hasAltUpdate = false,
            enabled = true
        )
        val state2 = CardButtonState(
            reqAction = null,
            hasUpdate = false,
            hasAltUpdate = false,
            enabled = true
        )
        val state3 = CardButtonState(
            reqAction = "CONFIGURE_SETTINGS",
            hasUpdate = true,
            hasAltUpdate = false,
            enabled = true
        )

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }

    @Test
    fun testPluginStatusActionEquality() {
        val action1 = PluginStatusAction.Custom("clean_cache")
        val action2 = PluginStatusAction.Custom("clean_cache")
        val action3 = PluginStatusAction.Custom("refresh")

        assertEquals(action1, action2)
        assertTrue(action1 != action3)
        assertEquals(PluginStatusAction.Uninstall, PluginStatusAction.Uninstall)
        assertEquals(PluginStatusAction.Update, PluginStatusAction.Update)
    }

    @Test
    fun testCalculateResizedLogHeightPx() {
        // Drag downwards increases height
        val expanded = calculateResizedLogHeightPx(
            currentHeightPx = 150f,
            deltaY = 75f,
            minHeightPx = 100f,
            maxHeightPx = 800f
        )
        assertEquals(225f, expanded)

        // Drag upwards decreases height
        val shrunk = calculateResizedLogHeightPx(
            currentHeightPx = 225f,
            deltaY = -50f,
            minHeightPx = 100f,
            maxHeightPx = 800f
        )
        assertEquals(175f, shrunk)

        // Coerce at minimum height
        val clampedMin = calculateResizedLogHeightPx(
            currentHeightPx = 120f,
            deltaY = -50f,
            minHeightPx = 100f,
            maxHeightPx = 800f
        )
        assertEquals(100f, clampedMin)

        // Coerce at maximum height
        val clampedMax = calculateResizedLogHeightPx(
            currentHeightPx = 750f,
            deltaY = 100f,
            minHeightPx = 100f,
            maxHeightPx = 800f
        )
        assertEquals(800f, clampedMax)
    }

    @Test
    fun testCalculateToggledLogHeight() {
        val defaultHeight = 150.dp
        val expandedHeight = 450.dp

        // When at default height, toggle expands to expandedHeight
        assertEquals(expandedHeight, calculateToggledLogHeight(defaultHeight, defaultHeight, expandedHeight))

        // When at expanded height, toggle collapses to defaultHeight
        assertEquals(defaultHeight, calculateToggledLogHeight(expandedHeight, defaultHeight, expandedHeight))

        // When resized above default height, toggle collapses to defaultHeight
        assertEquals(defaultHeight, calculateToggledLogHeight(300.dp, defaultHeight, expandedHeight))

        // When resized below default height, toggle expands to expandedHeight
        assertEquals(expandedHeight, calculateToggledLogHeight(100.dp, defaultHeight, expandedHeight))
    }
}
