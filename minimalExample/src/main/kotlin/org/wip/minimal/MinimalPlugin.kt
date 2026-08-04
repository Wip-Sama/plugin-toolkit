package org.wip.minimal

import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.annotations.Capability
import org.wip.plugintoolkit.api.annotations.CapabilityParam
import org.wip.plugintoolkit.api.annotations.PluginInfo

@PluginInfo(
    id = "org.wip.minimal",
    name = "Minimal Example Plugin",
    version = "1.0.0",
    description = "A minimal plugin showcase demonstrating basic plugin structure.",
    supportedOs = [OS.WINDOWS, OS.LINUX, OS.MACOS]
)
class MinimalPlugin {

    @Capability(name = "greet", description = "Returns a simple greeting message")
    fun greet(
        @CapabilityParam(description = "Name of the person to greet", defaultValue = "World") name: String
    ): String {
        return "Hello, $name!"
    }
}
