# Plugin Migrations

When developing plugins for the host application, you will eventually release new versions that modify your existing APIs. This can include renaming capabilities, changing port inputs/outputs, updating custom objects, or altering user settings.

To ensure that flows previously constructed by users with older versions of your plugin continue to work, the host application features a robust migration system.

## Flow Node Migrations (`migrations.json`)

To automatically update user flows, you can place a `migrations.json` file in the root of your plugin project (alongside your source code). The KSP processor will detect this file and package it with your plugin. 

When the host application loads a flow, it identifies `CapabilityNode`s that belong to older versions of your plugin. It will then fetch the `migrations.json` file and resolve a step-by-step path from the old version to the current version.

### `migrations.json` Format

The `migrations.json` file consists of an array of `PluginMigration` objects:

```json
[
  {
    "fromVersion": "1.0.0",
    "toVersion": "1.1.0",
    "capabilityMigrations": [
      {
        "oldName": "old_sum",
        "newName": "sum",
        "isDropInReplacement": true,
        "portMigrations": [
          {
            "oldName": "value",
            "newName": "values"
          }
        ]
      },
      {
        "oldName": "deprecated_feature",
        "newName": null
      }
    ],
    "settingMigrations": [
      {
        "oldName": "api_key",
        "newName": "googleApiKey"
      }
    ],
    "objectMigrations": [
      {
        "oldClassName": "OldComplexObject",
        "newClassName": "ComplexObject",
        "propertyMigrations": [
          {
            "oldName": "data",
            "newName": "properties"
          }
        ]
      }
    ]
  }
]
```

#### Drop-In Replacements vs User Consent
If a capability or setting migration is seamless and entirely handled by the mapping rules, you should set `"isDropInReplacement": true` (where applicable). The host will silently apply these changes.

If there are fundamental changes (such as missing required ports that the user now must manually connect), the host will display a Migration Popup warning the user and asking for consent to apply the available migration steps.

#### Unhandled Migrations & Breaking Changes
If you omit a migration path for a capability, or explicitly set `"newName": null`, the capability is marked as **unhandled**. The host application handles this gracefully by turning the node into a "Broken Node". The node remains on the canvas with its existing connections, but displays an error and refuses to execute until the user manually fixes or replaces it.

This is incredibly useful for major version bumps (e.g. `1.x.x` to `2.0.0`). If you do not wish to maintain backward compatibility across major versions, simply do not provide a migration path from 1.x to 2.0. Old nodes will gracefully break, forcing users to adopt your new paradigm cleanly.

## Internal State Migrations (`performUpdate()`)

While `migrations.json` handles the configuration of Nodes in the host's Flows, your plugin might manage its own internal state, such as local database schemas, downloaded files, or cached data.

To migrate this internal state, your `PluginEntry` can override `suspend fun performUpdate(context: PluginContext)`.

> [!CAUTION]
> **Version-Agnostic Requirement**
> The `performUpdate` function must be implemented in a **version-agnostic** way. Users may skip multiple versions during an upgrade (e.g., jumping from `1.0.0` directly to `1.5.0`). 
> 
> You cannot assume the user is upgrading from `1.4.0`. Instead, query your current internal state defensively. For example, check if a database table exists or if a file is in the old format, and then upgrade it to the new format.

```kotlin
override suspend fun performUpdate(context: PluginContext): Result<Unit> {
    val fs = context.fileSystem
    // Defensive check: Do we still have the v1 settings file?
    if (fs.exists("settings_v1.json")) {
        // Upgrade to v2 format
        val oldData = fs.readTextFile("settings_v1.json")
        // ... transform ...
        fs.writeTextFile("settings_v2.json", newData)
        fs.deleteFile("settings_v1.json")
    }
    return Result.success(Unit)
}
```
