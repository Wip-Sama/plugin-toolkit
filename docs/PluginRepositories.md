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
  "pluginsFolder": "plugins",
  "plugins": [
    {
      "name": "Awesome Plugin",
      "pkg": "com.example.plugin",
      "version": "1.0.0",
      "fileName": "plugin-1.0.0.jar",
      "description": "An awesome plugin that does things.",
      "minAppVersion": "1.0.0"
    }
  ],
  "signPublicKey": "...",
  "signAlgorithm": "SHA256"
}
```

### Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `name` | String | Human-readable name of the repository. |
| `url` | String | Absolute URL to this `index.json`. |
| `pluginsFolder` | String | (Optional) Relative path to the folder containing plugins. Defaults to `plugins`. |
| `plugins` | Array | List of plugin metadata objects. |
| `signPublicKey` | String | (Optional) Public key used for verifying plugin signatures. |
| `signAlgorithm` | String | (Optional) Algorithm used for signing (e.g., `SHA256`). |

### Plugin Object Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `name` | String | Name of the plugin. |
| `pkg` | String | Unique identifier (package ID) of the plugin. |
| `version` | String | Semantic version of the plugin. |
| `fileName` | String | Name of the file to download (located in `pluginsFolder/{pkg}/`). |
| `description` | String | Short description of the plugin. |
| `minAppVersion`| String | (Optional) Minimum version of the host app required. |

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

## How Installation Works

1. **Discovery**: The user adds the `index.json` URL to the application.
2. **Indexing**: The app fetches `index.json` and parses the `plugins` list, preloading `manifest.json` if available.
3. **Resolution**: When a user clicks "Install", the app constructs the download URL:
   `{repo_base_url}/{pluginsFolder}/{pkg}/{fileName}`
4. **Extraction**: The `.jar` is placed directly in the plugin's folder.
5. **Registration**: The plugin is added to the local registry and becomes available for loading.

## Package Source Overrides

If multiple repositories provide the same plugin (same package ID), the application allows users to "lock" a plugin to a specific repository source. This prevents accidental "upgrades" from an untrusted or incompatible repository.

## Security (Signing)

Repositories can optionally provide a `signPublicKey`. While the current implementation primarily uses this for metadata, future versions will enforce signature verification for all downloaded JARs to ensure they haven't been tampered with.

## Hosting a Repository

Since the repository is purely static, you can host it on:
- **GitHub Pages**: Ideal for community-driven repositories.
- **S3 / Cloud Storage**: For high-availability distribution.
- **Local Web Server**: For development and testing.
