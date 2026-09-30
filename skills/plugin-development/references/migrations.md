# Plugin Migrations Reference

This document covers version-to-version migration strategies for plugins in the Plugin Toolkit ecosystem, including Flow Node migrations (`migrations.json`) and internal state migrations (`performUpdate`).

---

## 1. Flow Node Migrations (`migrations.json`)

When releasing new versions that rename capabilities, change ports, alter complex objects, or modify settings, place a `migrations.json` file in the root of your plugin project module. The KSP processor detects and packages it into the plugin JAR.

When the host application opens saved user flows created with older plugin versions, it traces the shortest migration path in `migrations.json` from the node's saved version to the currently installed version.

### `migrations.json` Schema

```json
[
  {
    "fromVersion": "1.0.0",
    "toVersion": "1.1.0",
    "capabilityMigrations": [
      {
        "oldName": "calculate_hash",
        "newName": "hash_data",
        "isDropInReplacement": true,
        "portMigrations": [
          {
            "oldName": "text_input",
            "newName": "input"
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
        "newName": "secretToken"
      }
    ],
    "objectMigrations": [
      {
        "oldClassName": "OldUserData",
        "newClassName": "UserProfile",
        "propertyMigrations": [
          {
            "oldName": "displayName",
            "newName": "username"
          }
        ]
      }
    ]
  }
]
```

### Migration Types Explained

#### 1. Capability Migrations (`capabilityMigrations`):
- `oldName`: Previous capability identifier.
- `newName`: Replacement capability identifier (or `null` for deprecations).
- `isDropInReplacement`: Set to `true` if the migration can be applied silently without breaking connections or altering behavior.
- `portMigrations`: Array mapping old input/output port names to new names.

#### 2. Setting Migrations (`settingMigrations`):
- Renames persistent settings without forcing the user to re-enter values.

#### 3. Complex Object Migrations (`objectMigrations`):
- Updates serialized data class names and property names inside persisted flow node states.

---

## 2. Drop-In Replacements vs. User Consent

| Mode | Configuration | Host Behavior |
| :--- | :--- | :--- |
| **Drop-In Replacement** | `"isDropInReplacement": true` | The host application automatically and silently updates the flow node without prompting the user. |
| **User Consent Required** | `"isDropInReplacement": false` (or omitted) | The host displays a Migration Dialog warning the user of potential behavior differences and requests explicit approval before updating. |

---

## 3. Unhandled Migrations & Breaking Changes

To intentionally break a capability across major version boundaries (e.g. 1.x to 2.0.0):
- Explicitly configure `"newName": null`, or omit a migration entry entirely.
- The host marks the flow node as an **Unhandled / Broken Node**.
- Broken nodes remain visible on the canvas with their existing connections and parameters preserved, but display a distinct error state and refuse execution until the user replaces or updates them manually.

---

## 4. Internal State Migrations (`performUpdate`)

While `migrations.json` updates flow nodes, internal plugin state (database tables, downloaded models, cached files) must be migrated in code via `@PluginUpdate` or overriding `performUpdate(context: PluginContext)`.

### Version-Agnostic Requirement

> **CRITICAL**: Users frequently skip intermediate versions during upgrades (e.g. upgrading directly from `1.0.0` to `1.5.0`). The `performUpdate` implementation must **never** assume the immediate preceding version was installed.

Always perform **defensive state checks** rather than assuming a specific upgrade sequence:

```kotlin
@PluginUpdate
suspend fun onUpdate(context: PluginContext): Result<Unit> {
    val fs = context.fileSystem
    val logger = context.logger

    logger.info("Checking for required data migrations...")

    // Check 1: Migrate legacy plain text configs to JSON format
    if (fs.exists("config.txt") && !fs.exists("config.json")) {
        val oldContent = fs.readTextFile("config.txt")
        val migratedJson = convertConfigToJson(oldContent)
        fs.writeTextFile("config.json", migratedJson)
        fs.deleteFile("config.txt")
        logger.info("Migrated config.txt to config.json")
    }

    // Check 2: Schema migration on internal SQLite or key-value storage
    val currentSchemaVersion = context.storage.get("schema_version")?.jsonPrimitive?.intOrNull ?: 1
    if (currentSchemaVersion < 2) {
        migrateDatabaseToV2()
        context.storage.put("schema_version", JsonPrimitive(2))
        logger.info("Upgraded storage schema to version 2")
    }

    return Result.success(Unit)
}
```

### `@PluginUpdate` vs `@PluginSetup`

- **`@PluginUpdate`**: Invoked when an existing installation is updated. Retains all existing files in the plugin data directory.
- **`@PluginSetup`**: Invoked during first-time installation. If used as an update fallback (when `@PluginUpdate` is missing), the host **wipes the entire `files/` directory** before running setup. Always provide `@PluginUpdate` if persistent user data must be preserved.
