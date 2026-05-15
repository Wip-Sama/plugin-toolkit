# Plugin Repositories

This document describes how plugin repositories are structured and how the host application interacts with them. A plugin repository is essentially a static file server hosting an `index.json` file and a set of plugin packages.

## Repository Structure

A standard repository should follow this directory structure:

```text
repository-root/
├── index.json                 # Main repository manifest
└── plugins/                   # Folder containing plugins
    ├── com.example.plugin/    # Plugin ID as folder name
    │   ├── plugin-1.0.0.jar   # The plugin JAR file
    │   ├── changelog.md      # Optional: Detailed changelog
    │   ├── icon.png           # Optional: Plugin icon (png, webp, svg, jpg)
    │   └── manifest.json      # Optional: Preloads plugin capabilities and settings
    └── org.another.plugin/
        └── ...
```

## The `index.json` File

The `index.json` is the entry point of the repository. It contains metadata about the repository itself and a list of available plugins.

### Format

```json
{
  "name": "My Plugin Repository",
  "url": "https://example.com/repo/index.json",
  "schemaVersion": 1,
  "pluginsFolder": "plugins",
  "plugins": [
    {
      "name": "Awesome Plugin",
      "pkg": "com.example.plugin",
      "version": "1.0.0",
      "fileName": "plugin-1.0.0.jar",
      "description": "An awesome plugin that does things.",
      "targetAppVersion": "1.0.0",
      "hash": "...",
      "signature": "..."
    }
  ],
  "signPublicKey": "...",
  "signAlgorithm": "SHA256"
}
```

### Fields

| Field           | Type   | Description                                                                       |
|:----------------|:-------|:----------------------------------------------------------------------------------|
| `name`          | String | Human-readable name of the repository.                                            |
| `url`           | String | Absolute URL to this `index.json`.                                                |
| `schemaVersion` | Int    | Version of the repository manifest schema. Defaults to `1`.                       |
| `pluginsFolder` | String | (Optional) Relative path to the folder containing plugins. Defaults to `plugins`. |
| `plugins`       | Array  | List of plugin metadata objects.                                                  |
| `signPublicKey` | String | (Optional) Public key used for verifying plugin signatures.                       |
| `signAlgorithm` | String | (Optional) Algorithm used for signing (e.g., `SHA256`).                           |

### Plugin Object Fields

| Field           | Type   | Description                                                       |
|:----------------|:-------|:------------------------------------------------------------------|
| `name`          | String | Name of the plugin.                                               |
| `pkg`           | String | Unique identifier (package ID) of the plugin.                     |
| `version`       | String | Semantic version of the plugin.                                   |
| `fileName`      | String | Name of the file to download (located in `pluginsFolder/{pkg}/`). |
| `description`   | String | Short description of the plugin.                                  |
| `targetAppVersion` | String | (Optional) The app version this plugin was targeted for.         |
| `hash`          | String | (Recommended) SHA-256 hash of the plugin JAR.                     |
| `signature`     | String | (Recommended) Detached RSA signature of the `hash` string.        |

## Plugin Assets

The host application attempts to download additional assets for each plugin to improve the UI experience. These should be placed in the same folder as the plugin file:

- **Changelog**: `changelog.md`. The format must be strictly as follows:
  ```text
  Date: 2026-05-03
  Version: 1.1.0
  Added:
    - New capability for data sync
  Fixed:
    - Memory leak in scanner
  ...
    - ...
  ---------------------------------------------------------------------------------------------------
  ```
  Note: The separator must be exactly 100 dashes.
- **Icon**: `icon.png`, `icon.webp`, `icon.svg`, or `icon.jpg`.
- **Manifest**: `manifest.json`. This allows the application to preload the plugin's capabilities and settings before it is installed.

## Installation & Updates

1. **Discovery**: The user adds the `index.json` URL to the application.
2. **Indexing**: The app fetches `index.json` and parses the `plugins` list, preloading `manifest.json` if available.
3. **Resolution**: When a user clicks "Install" or "Update", the app constructs the download URL:
   `{repo_base_url}/{pluginsFolder}/{pkg}/{fileName}`
4. **Execution**:
    - **Fresh Install**: The JAR is copied, and the `@PluginSetup` handler is called (if present).
    - **Update**: 
        - If `@PluginUpdate` is present in the new version, it is executed.
        - If not, but `@PluginSetup` is present, the plugin's `files/` directory is cleared and `@PluginSetup` is executed.
        - If neither is present, only the JAR is replaced.
5. **Registration**: The plugin state is updated in the local registry.

## Package Source Overrides

If multiple repositories provide the same plugin (same package ID), the application allows users to "lock" a plugin to a specific repository source. This prevents accidental "upgrades" from an untrusted or incompatible repository.

## Security (Signing)

To protect users from malicious or tampered plugins, the application enforces digital signature verification for all remote repositories that provide a `signPublicKey`.

### Mandatory Verification
If a repository defines a `signPublicKey`, **all plugins** downloaded from that repository must be signed with the corresponding private key. The application performs two levels of verification:
1.  **JAR Verification**: The downloaded JAR file is checked for a valid internal signature (Standard Java JAR signing).
2.  **Metadata Verification**: If the `index.json` provides a `hash` and `signature` for a plugin, the application verifies that the `signature` is a valid detached signature of the `hash` string, and that the `hash` matches the actual file content.

Plugins that fail verification will be blocked from loading.

### Key Requirements
- **Algorithm**: RSA with SHA-256.
- **Key Format**: Base64-encoded X.509.

## Hosting a Repository

Since the repository is purely static, you can host it on:
- **GitHub Pages**: Ideal for community-driven repositories.
- **S3 / Cloud Storage**: For high-availability distribution.
- **Local Web Server**: For development and testing.
