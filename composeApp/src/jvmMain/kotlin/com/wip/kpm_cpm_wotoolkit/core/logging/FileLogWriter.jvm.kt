package com.wip.kpm_cpm_wotoolkit.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wip.kpm_cpm_wotoolkit.features.settings.model.LoggingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

class FileLogWriter(
    private val logsDir: File,
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
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        cleanupOldLogs()
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val now = Date()
        val timestamp = timeFormatter.format(now)
        val dateString = dateFormatter.format(now)
        val logFile = File(logsDir, "$dateString.log")

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
                FileOutputStream(logFile, true).use { fos ->
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
                val files = logsDir.listFiles() ?: return@launch
                val now = Calendar.getInstance()
                
                // Group files by type
                val logFiles = files.filter { it.extension == "log" }.sortedByDescending { it.name }
                val gzFiles = files.filter { it.extension == "gz" }.sortedByDescending { it.name }

                val todayStr = dateFormatter.format(Date())

                // 1. Handle compression and deletion of .log files
                logFiles.forEach { file ->
                    val fileName = file.nameWithoutExtension
                    if (fileName == todayStr) return@forEach // Don't touch today's log

                    val fileDate = try { dateFormatter.parse(fileName) } catch (e: Exception) { null } ?: return@forEach
                    val daysOld = getDaysDifference(fileDate, Date())

                    if (daysOld >= settings.logsToKeep) {
                        if (settings.compressOldLogs) {
                            compressFile(file)
                            file.delete()
                        } else {
                            file.delete()
                        }
                    }
                }

                // 2. Handle cleanup of .gz files
                val updatedGzFiles = logsDir.listFiles { _, name -> name.endsWith(".gz") }?.sortedByDescending { it.name } ?: emptyList()
                if (updatedGzFiles.size > settings.compressedLogsToKeep) {
                    updatedGzFiles.drop(settings.compressedLogsToKeep).forEach { it.delete() }
                }

            } catch (e: Exception) {
                System.err.println("Failed to cleanup old logs: ${e.message}")
            }
        }
    }

    private fun compressFile(file: File) {
        val gzFile = File(file.parent, "${file.name}.gz")
        try {
            file.inputStream().use { input ->
                GZIPOutputStream(gzFile.outputStream()).use { output ->
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
