package com.wip.bettwemanhwa

import com.wip.plugin.api.ExecutionContext
import com.wip.plugin.api.PluginFileSystem
import com.wip.plugin.api.PluginLogger
import com.wip.plugin.api.PluginSignal
import com.wip.plugin.api.ProgressReporter
import com.wip.plugin.api.annotations.Capability
import com.wip.plugin.api.annotations.CapabilityParam
import com.wip.plugin.api.annotations.PluginInfo
import com.wip.plugin.api.annotations.ResumeState
import com.wip.plugin.api.annotations.PluginSetup
import com.wip.plugin.api.annotations.PluginValidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class OutputFormat {
    PNG, WEBP
}

enum class Widths {
    x1, x2, x4, x8
}

@PluginInfo(
    id = "com.wip.bettwemanhwa",
    name = "BetterManhwa",
    version = "1.0.0",
    description = "A plugin that processes manhwa images using the BetterManhwa CLI tool."
)
class BetterManhwa {
    @Capability(
        name = "Manhwa Processor",
        description = "Process a folder of manhwa images using BetterManhwa CLI"
    )
    fun manhwaProcessor(
        @CapabilityParam(description = "Folder to process") folder: String,
        @CapabilityParam(description = "Output width in pixels", defaultValue = "940") width: Int,
        @CapabilityParam(description = "Grain level for processing", defaultValue = "5") grain: Int,
        @CapabilityParam(description = "Output image format", defaultValue = "WEBP") outputFormat: OutputFormat,
        @CapabilityParam(description = "Output folder (leave null to auto-create)", defaultValue = "null") outFolder: String? = null,
        @ResumeState resumeState: kotlinx.serialization.json.JsonElement? = null,
        context: ExecutionContext
    ): String {
        // Validate input folder
        val inputDir = File(folder)
        if (!inputDir.exists() || !inputDir.isDirectory) {
            throw IllegalArgumentException("Input folder does not exist or is not a directory: $folder")
        }

        val logger = context.logger
        val fileSystem = context.fileSystem
        val progressReporter = context.progress

        // Determine output folder
        val outputDir = if (!outFolder.isNullOrBlank() && outFolder != "null") {
            File(outFolder)
        } else {
            File(inputDir.parent, "${inputDir.name}_output")
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        logger.info("Processing folder: $folder")
        logger.info("Output folder: ${outputDir.absolutePath}")
        logger.info("Width: $width, Grain: $grain, Format: $outputFormat")

        // Resolve the Python environment and core script from the plugin's managed files
        val basePath = fileSystem.getBasePath()
        val pythonExe = File(basePath, "vapoursynth-portable/python.exe")
        val coreScript = File(basePath, "upscaler_core.py")

        if (!pythonExe.exists()) {
            throw IllegalStateException(
                "Python environment not found at ${pythonExe.absolutePath}. " + "Please run plugin setup first."
            )
        }

        if (!coreScript.exists()) {
            throw IllegalStateException(
                "upscaler_core.py not found at ${coreScript.absolutePath}. " + "Please run plugin setup first."
            )
        }

        // Build the command
        val command = mutableListOf(
            pythonExe.absolutePath,
            coreScript.absolutePath,
            inputDir.absolutePath,
            "--output", outputDir.absolutePath,
            "--width", width.toString(),
            "--grain", grain.toString(),
            "--format", outputFormat.name.lowercase()
        )

        logger.info("Executing: ${command.joinToString(" ")}")

        var process = ProcessBuilder(command)
            .directory(File(basePath))
            .redirectErrorStream(true)
            .start()

        fun executeProcess(process: Process) {
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    if (line.toFloatOrNull() != null) {
                        progressReporter.report(line.toFloat().coerceIn(0f, 1f))
                    } else {
                        logger.debug(line)
                        output.appendLine(line)
                    }
                }
            }
            process.errorStream.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    logger.error(line)
                    output.appendLine(line)
                }
            }

            try {
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val errorMsg = "Upscaler core exited with code $exitCode. Output:\n$output"
                    logger.error(errorMsg)
                    throw RuntimeException(errorMsg)
                }
            } catch (
                e: Exception
            ) {
                val errorMsg = "Error while waiting for process to finish: ${e.message}"
                logger.error(errorMsg, e)
                throw RuntimeException(e)
            } finally {
                process.destroyForcibly()
            }
        }

        context.onSignal { signal ->
            when(signal) {
                PluginSignal.PAUSE -> {
                    logger.info("Received pause signal. Killing process...")
                    process.destroyForcibly()
                }
                PluginSignal.CANCEL -> {
                    logger.info("Received cancel signal. Killing process...")
                    process.destroyForcibly()
                }
            }
        }
        executeProcess(process)

        logger.info("Processing completed successfully")
        return outputDir.absolutePath.replace("\\\\", "\\")
    }

    @PluginSetup
    suspend fun setup(fileSystem: PluginFileSystem, logger: PluginLogger): Result<Unit> {
        return try {
            logger.info("Starting BetterManhwa setup...")

            // Extract bundled resources from the JAR to the plugin's managed file area
            val resourcesToExtract = listOf(
                "scripts/app_ui.py" to "app_ui.py",
                "scripts/install.bat" to "install.bat",
                "scripts/Install-Portable-VapourSynth-R73.ps1" to "Install-Portable-VapourSynth-R73.ps1",
                "scripts/launchCLI.bat" to "launchCLI.bat",
                "scripts/launchCLInoUpdate.bat" to "launchCLInoUpdate.bat",
                "scripts/launchUI.bat" to "launchUI.bat",
                "scripts/requirements.txt" to "requirements.txt",
                "scripts/UPDATE.bat" to "UPDATE.bat",
                "scripts/upscaler_core.py" to "upscaler_core.py",
                "scripts/version.json" to "version.json",
                "scripts/vsmlrt.py" to "vsmlrt.py",
            )

            for ((jarResource, targetPath) in resourcesToExtract) {
                logger.info("Extracting resource: $jarResource -> $targetPath")
                val result = fileSystem.extractResource(jarResource, targetPath)
                if (result.isFailure) {
                    logger.warn("Failed to extract $jarResource: ${result.exceptionOrNull()?.message}")
                }
            }

            // Run install.bat to set up dependencies
            val basePath = fileSystem.getBasePath()
            val installScript = File(basePath, "install.bat")

            if (installScript.exists()) {
                logger.info("Running install script: ${installScript.absolutePath}")
                val process = withContext(Dispatchers.IO) {
                    ProcessBuilder("cmd", "/c", installScript.absolutePath).directory(File(basePath))
                        .redirectErrorStream(true).start()
                }

                process.inputStream.bufferedReader().use { reader ->
                    reader.lines().forEach { line ->
                        logger.debug(line)
                    }
                }

                val exitCode = withContext(Dispatchers.IO) {
                    process.waitFor()
                }
                if (exitCode != 0) {
                    logger.error("Install script exited with code $exitCode")
                    return Result.failure(RuntimeException("Install script failed with exit code $exitCode"))
                }
                logger.info("Install script completed successfully")
            } else {
                logger.warn("Install script not found at ${installScript.absolutePath}, skipping installation step")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @PluginValidate
    suspend fun validate(fileSystem: PluginFileSystem, logger: PluginLogger): Result<Unit> {
        return try {
            val basePath = fileSystem.getBasePath()
            val pythonExe = File(basePath, "vapoursynth-portable/python.exe")
            val coreScript = File(basePath, "upscaler_core.py")

            if (!pythonExe.exists()) {
                logger.error("Python environment not found at ${pythonExe.absolutePath}")
                return Result.failure(Exception("Python environment not found. Please run setup."))
            }

            if (!coreScript.exists()) {
                logger.error("upscaler_core.py not found at ${coreScript.absolutePath}")
                return Result.failure(Exception("Core script not found. Please run setup."))
            }

            logger.info("BetterManhwa validation passed - environment and core script found")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}