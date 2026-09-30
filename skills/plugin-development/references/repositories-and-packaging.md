# Standalone Runner, Packaging, & Repositories Reference

This document covers standalone desktop execution, dual build mode, digital signing, and repository indexing for plugins.

---

## 1. Standalone Execution & Dual Build Mode

Plugins are developed as pure JVM modules against `:plugin-api` without UI framework dependencies. To test or distribute a plugin as an independent desktop application, the repository provides a **Dual Build Mode**:

| Mode | Target Artifact | Primary Use Case |
| :--- | :--- | :--- |
| **Pure Plugin Mode** (Default) | `.jar` | Distribution via repositories, flow canvas nodes, multi-plugin desktop app. |
| **Standalone Mode** | Runnable desktop window | Quick local testing, isolated GUI debugging, single-purpose desktop utility. |

### Running in Standalone Mode:

1. **Run target plugin module directly**:
   ```bash
   ./gradlew :apps:standaloneRunner:run -PtargetPlugin=:plugins:<plugin-module-name>
   ```

2. **Run against an arbitrary compiled JAR**:
   ```bash
   ./gradlew :apps:standaloneRunner:run --args="--plugin-jar /path/to/plugin.jar"
   ```

3. **Standalone UI Features**:
   - Displays plugin metadata badge, memory requirements, and version.
   - Provides **Run Setup** button (if `@PluginSetup` is present).
   - Provides **Settings Dialog** populated with all `@PluginSetting` controls and real-time validation.
   - Left-hand capability selector with search filtering and real-time parameter input forms.

---

## 2. Digital Signing & Security

For plugins distributed via remote repositories, digital signature verification is **mandatory**.

### Verification Mechanism:
- Algorithm: **RSA** with **SHA-256**.
- The repository manifest provides a `signPublicKey` in **Base64-encoded X.509** format.
- Downloaded JAR files are verified against the public key and manifest SHA-256 hash before loading.

### Key Pair Generation & Signing Workflow:

1. **Generate RSA Key Pair**:
   ```bash
   keytool -genkeypair -alias plugin-key -keyalg RSA -keysize 2048 -keystore release-key.jks -validity 10000
   ```

2. **Sign the Compiled JAR**:
   ```bash
   jarsigner -keystore release-key.jks -sigalg SHA256withRSA -digestalg SHA-256 my-plugin.jar plugin-key
   ```

3. **Export Public Certificate**:
   ```bash
   keytool -exportcert -alias plugin-key -keystore release-key.jks -file plugin-cert.crt
   ```

4. **Convert Certificate to Base64 (PowerShell)**:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("plugin-cert.crt"))
   ```
   Paste the resulting Base64 string as the `signPublicKey` in your repository manifest (`index.json`).

---

## 3. Extension Repositories (`index.json`)

Repositories host plugins and reusable flows as static web servers or local directories.

### Standard Directory Layout:
```text
repository-root/
├── index.json                 # Repository entry manifest
├── plugins/                   # Folder containing plugin JARs
│   └── com.example.plugin/
│       ├── plugin-1.0.0.jar
│       ├── changelog.md       # Optional
│       └── icon.png           # Optional
└── flows/                     # Folder containing saved flows
    └── my_flow.json
```

### `index.json` Schema:
```json
{
  "name": "Community Plugins",
  "url": "https://example.com/repo/index.json",
  "schemaVersion": 1,
  "pluginsFolder": "plugins",
  "flowsFolder": "flows",
  "signPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...",
  "signAlgorithm": "SHA256",
  "plugins": [
    {
      "name": "Image Filter Plugin",
      "pkg": "com.example.imagefilter",
      "version": "1.2.0",
      "fileName": "plugin-1.2.0.jar",
      "description": "Applies image transformations.",
      "targetAppVersion": "1.0.0",
      "hash": "<sha256-hex>",
      "signature": "<rsa-signature-base64>"
    }
  ],
  "flows": [
    {
      "name": "Batch Upscale Flow",
      "fileName": "batch_upscale.json",
      "version": "1.0.0",
      "description": "Upscales an entire directory of images.",
      "hash": "<sha256-hex>",
      "signature": "<rsa-signature-base64>"
    }
  ]
}
```

### Transitive Dependency Resolution (Subflows):
When installing a flow from a repository:
1. The host scans the flow's nodes for any `SubFlowNode` referencing other flows by name.
2. If the referenced subflow is not installed, the host recursively searches the repository, downloads, and installs all subflow dependencies before finalizing the installation.

### Local Repositories:
You can register an offline or development repository by pointing directly to a local filesystem manifest (e.g. `D:\MyRepo\index.json`). The host handles all installations with zero-network file operations while still validating the manifest schema.
