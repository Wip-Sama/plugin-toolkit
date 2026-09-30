package org.wip.plugintoolkit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressModelsTest {

    @Test
    fun testPercentageProgressData() {
        val data = ProgressData.percentage(0.75f, "Downloading models")
        assertEquals(0.75f, data.fraction)
        assertEquals("Downloading models", data.message)
        assertEquals(ProgressDisplayMode.PERCENTAGE, data.displayMode)
        assertNull(data.current)
        assertNull(data.total)
    }

    @Test
    fun testRatioProgressData() {
        val data = ProgressData.ratio(12.3, 14.5, "MB", "Transferring data")
        assertEquals((12.3 / 14.5).toFloat(), data.fraction)
        assertEquals(12.3, data.current)
        assertEquals(14.5, data.total)
        assertEquals("MB", data.unit)
        assertEquals("Transferring data", data.message)
        assertEquals(ProgressDisplayMode.RATIO, data.displayMode)
    }

    @Test
    fun testIndeterminateProgressData() {
        val data = ProgressData.indeterminate("Waiting for rate limit reset")
        assertNull(data.fraction)
        assertEquals("Waiting for rate limit reset", data.message)
        assertEquals(ProgressDisplayMode.INDETERMINATE, data.displayMode)
    }

    @Test
    fun testProgressReporterDefaultImplementations() {
        var reportedFraction = 0f
        var reportedMessage: String? = null
        var secondaryFraction = 0f
        var secondaryMessage: String? = null
        var subProgressName: String? = null
        var subProgressData: ProgressData? = null

        val reporter = object : ProgressReporter {
            override fun report(progress: Float) {
                reportedFraction = progress
            }

            override fun report(progress: Float, message: String?) {
                reportedFraction = progress
                reportedMessage = message
            }

            override fun reportSecondary(progress: Float) {
                secondaryFraction = progress
            }

            override fun reportSecondary(progress: Float, message: String?) {
                secondaryFraction = progress
                secondaryMessage = message
            }

            override fun reportSubProgress(name: String, data: ProgressData) {
                subProgressName = name
                subProgressData = data
            }
        }

        reporter.report(0.5f, "Halfway done")
        assertEquals(0.5f, reportedFraction)
        assertEquals("Halfway done", reportedMessage)

        reporter.report(5.0, 10.0, "MB", "5 out of 10 MB")
        assertEquals(0.5f, reportedFraction)
        assertEquals("5 out of 10 MB", reportedMessage)

        reporter.reportSecondary(0.8f, "Backoff retry")
        assertEquals(0.8f, secondaryFraction)
        assertEquals("Backoff retry", secondaryMessage)

        reporter.reportSubProgress("chunkDownload", 0.3f, "Chunk 3/10")
        assertEquals("chunkDownload", subProgressName)
        assertEquals(0.3f, subProgressData?.fraction)
        assertEquals("Chunk 3/10", subProgressData?.message)
    }
}
