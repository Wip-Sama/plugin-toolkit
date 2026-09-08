package org.wip.plugintoolkit.features.plugin.model

enum class PluginManagerChipFilter {
    All,
    Enabled,
    Disabled,
    UpdateAvailable,
    RequiresSetup
}

enum class PluginManagerSortMode {
    NameAsc,
    NameDesc,
    StatusEnabledFirst,
    LatestVersion
}
