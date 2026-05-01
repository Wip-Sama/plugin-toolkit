package com.wip.bettwemanhwa

import com.wip.plugin.api.annotations.Capability
import com.wip.plugin.api.annotations.CapabilityParam
import com.wip.plugin.api.annotations.PluginInfo
import com.wip.plugin.api.annotations.PluginSetup
import com.wip.plugin.api.PluginFileSystem.*

enum class OutputFormat {
    PNG, WEBP
}

@PluginInfo(
    id = "com.wip.bettwemanhwa",
    name = "BetterManhwa",
    version = "0.1.0",
    description = ""
)
class BetterManhwa {
    @Capability(
        name = "Manhwa Processor",
        description = "Process a folder"
    )
    fun manhwaProcessor(
        @CapabilityParam(description = "Folder to process") folder: String,
        @CapabilityParam(description = "Width") width: Int,
        @CapabilityParam(description = "Grain") grain: Int,
        @CapabilityParam(description = "Output Format") outputFormat: OutputFormat,
        @CapabilityParam(description = "Output Folder", defaultValue = "null") outFolder: String,
    ): String {
        // it should ensure outputFolder is not null and it exist else throw error
        // launch the launchCLInoUpdate.bat / run python manually
        // return outputFolder if everything worked else the error
        return outFolder
    }

    @PluginSetup
    fun setup(): Result<Unit> {
        // Setup should extract all the resources/files from the jar using PluginFileSystem
        // Then it should run install.bat to install the resources
        return Result.success(Unit)
    }
}