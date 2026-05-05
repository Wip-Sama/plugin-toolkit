package org.wip.plugintoolkit.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import org.wip.plugintoolkit.features.settings.model.LoggingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

class FileLogWriter(
    private val logsDir: Path,
    private val settingsProvider: () -> LoggingSettings
) : LogWriter() {

    private val settings get() = settingsProvider()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FileLogThread").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())

    init {
        if (!SystemFileSystem.exists(logsDir)) {
            SystemFileSystem.createDirectories(logsDir)
        }
        cleanupOldLogs()
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val now = Date()
        val timestamp = timeFormatter.format(now)
        val dateString = dateFormatter.format(now)
        val logFile = Path("${logsDir}/$dateString.log")

        val severityChar = when (severity) {
            Severity.Verbose -> "V"
            Severity.Debug -> "D"
            Severity.Info -> "I"
            Severity.Warn -> "W"
            Severity.Error -> "E"
            Severity.Assert -> "A"
        }

        val formattedLine = "[$timestamp] $severityChar/$tag: $message"

        scope.launch {
            try {
                SystemFileSystem.sink(logFile, append = true).buffered().asOutputStream().use { fos ->
                    PrintWriter(fos).use { pw ->
                        pw.println(formattedLine)
                        throwable?.let {
                            it.printStackTrace(pw)
                        }
                    }
                }
            } catch (e: Exception) {
                System.err.println("Failed to write to log file: ${e.message}")
            }
        }
    }

    private fun cleanupOldLogs() {
        scope.launch {
            try {
                val files = SystemFileSystem.list(logsDir).toList()

                // Group files by type
                val logFiles = files.filter { it.name.endsWith(".log") }.sortedByDescending { it.name }
                val gzFiles = files.filter { it.name.endsWith(".gz") }.sortedByDescending { it.name }

                val todayStr = dateFormatter.format(Date())

                // 1. Handle compression and deletion of .log files
                logFiles.forEach { file ->
                    val fileName = file.name.substringBeforeLast(".")
                    if (fileName == todayStr) return@forEach // Don't touch today's log

                    val fileDate = try {
                        dateFormatter.parse(fileName)
                    } catch (e: Exception) {
                        null
                    } ?: return@forEach
                    val daysOld = getDaysDifference(fileDate, Date())

                    if (daysOld >= settings.logsToKeep) {
                        if (settings.compressOldLogs) {
                            compressFile(file)
                            SystemFileSystem.delete(file)
                        } else {
                            SystemFileSystem.delete(file)
                        }
                    }
                }

                // 2. Handle cleanup of .gz files
                val updatedGzFiles =
                    SystemFileSystem.list(logsDir).filter { it.name.endsWith(".gz") }.sortedByDescending { it.name }
                        .toList()
                if (updatedGzFiles.size > settings.compressedLogsToKeep) {
                    updatedGzFiles.drop(settings.compressedLogsToKeep).forEach { SystemFileSystem.delete(it) }
                }

            } catch (e: Exception) {
                System.err.println("Failed to cleanup old logs: ${e.message}")
            }
        }
    }

    private fun compressFile(file: Path) {
        val gzFile = Path("${file}.gz")
        try {
            SystemFileSystem.source(file).buffered().asInputStream().use { input ->
                GZIPOutputStream(SystemFileSystem.sink(gzFile).buffered().asOutputStream()).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to compress log file ${file.name}: ${e.message}")
        }
    }

    private fun getDaysDifference(d1: Date, d2: Date): Long {
        val diff = d2.time - d1.time
        return diff / (24 * 60 * 60 * 1000)
    }
}
