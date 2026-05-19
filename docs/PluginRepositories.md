# Extension and Plugin Repositories

This document describes how extension repositories are structured and how the host application interacts with them. An extension repository is a static file server hosting an `index.json` file, plugin packages, and reusable flows.

## Repository Structure

A standard repository should follow this directory structure:

```text
repository-root/
├── index.json                 # Main repository manifest
├── plugins/                   # Folder containing plugins
│   ├── com.example.plugin/    # Plugin ID as folder name
│   │   ├── plugin-1.0.0.jar   # The plugin JAR file
│   │   ├── changelog.md      # Optional: Detailed changelog
│   │   ├── icon.png           # Optional: Plugin icon
│   │   └── manifest.json      # Optional: Preloads plugin metadata
│   └── org.another.plugin/
│       └── ...
└── flows/                     # Folder containing versioned flows
    ├── my_cool_flow.json      # JSON Flow definition file
    ├── complex_flow.zip       # Or zip packaged flow
    └── ...
```

## The `index.json` File

The `index.json` is the entry point of the repository. It contains metadata about the repository itself, a list of available plugins, and a list of versioned flows.

### Format

```json
{
  "name": "My Extension Repository",
  "url": "https://example.com/repo/index.json",
  "schemaVersion": 1,
  "pluginsFolder": "plugins",
  "flowsFolder": "flows",
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
  "flows": [
    {
      "name": "Data Processing Flow",
      "fileName": "data_process.json",
      "version": "1.2.0",
      "description": "A workflow that processes incoming data streams.",
      "hash": "...",
      "signature": "..."
    }
  ],
  "signPublicKey": "...",
  "signAlgorithm": "SHA256"
}
```

### Repository Fields

| Field           | Type   | Description                                                                       |
|:----------------|:-------|:----------------------------------------------------------------------------------|
| `name`          | String | Human-readable name of the repository.                                            |
| `url`           | String | Absolute URL to this `index.json`.                                                |
| `schemaVersion` | Int    | Version of the repository manifest schema. Defaults to `1`.                       |
| `pluginsFolder` | String | (Optional) Relative path to the folder containing plugins. Defaults to `plugins`. |
| `flowsFolder`   | String | (Optional) Relative path to the folder containing flows. Defaults to `flows`.     |
| `plugins`       | Array  | List of plugin metadata objects.                                                  |
| `flows`         | Array  | List of flow metadata objects.                                                    |
| `signPublicKey` | String | (Optional) Public key used for verifying plugin & flow signatures.                 |
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

### Flow Object Fields

| Field         | Type   | Description                                                      |
|:--------------|:-------|:-----------------------------------------------------------------|
| `name`        | String | Name of the flow.                                                |
| `fileName`    | String | Name of the flow file to download (located in `flowsFolder/`).   |
| `version`     | String | Semantic version of the flow.                                    |
| `description` | String | Description of what the flow does.                               |
| `hash`        | String | (Recommended) SHA-256 hash of the flow file.                     |
| `signature`   | String | (Recommended) Detached RSA signature of the `hash` string.       |

## Transitive Dependency Resolution (Subflows)

When importing or installing a Flow from a repository:
1. The application parses the `.json` flow file to inspect the configured nodes.
2. If any node is of type `SubFlowNode` (referencing another flow by name), the application checks if that subflow is already installed locally.
3. If the subflow is not installed, the application recursively searches the repository for a flow matching that subflow's name.
4. If found, the dependency flow is downloaded and installed recursively before the parent flow installation completes.

## Security (Signing)

To protect users from malicious or tampered extensions, the application enforces digital signature verification for all remote repositories that provide a `signPublicKey`.

### Mandatory Verification
If a repository defines a `signPublicKey`, **all plugins and flows** downloaded from that repository must be signed with the corresponding private key. The application performs the following verifications:
1.  **Plugins JAR Verification**: The downloaded JAR file is checked for a valid internal signature (Standard Java JAR signing).
2.  **Metadata/Flow Verification**: If the `index.json` provides a `hash` and `signature` for a plugin or a flow, the application verifies that the `signature` is a valid detached signature of the `hash` string using the repository's `signPublicKey`, and that the `hash` matches the actual downloaded file content.

Extensions that fail verification will be blocked from loading or importing.

### Key Requirements
- **Algorithm**: RSA with SHA-256.
- **Key Format**: Base64-encoded X.509.

## Hosting a Repository

Since the repository is purely static, you can host it on:
- **GitHub Pages**: Ideal for community-driven repositories.
- **S3 / Cloud Storage**: For high-availability distribution.
- **Local Web Server**: For development and testing.
