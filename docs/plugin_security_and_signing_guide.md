# Plugin Security & Signing Guide

This guide outlines the security boundaries enforced by the Plugin Toolkit and provides step-by-step instructions on how to properly sign a plugin JAR so it passes runtime verification.

## 🛡️ Sandbox & Security Boundaries

To ensure the safety of the host application, the plugin engine enforces strict filesystem and execution boundaries. Plugins operating outside of these constraints will trigger a `SecurityException`.

### 1. Relative Path Sandboxing
Plugins are restricted to their assigned installation directories (`files/` and `cache/`). 
* **Allowed:** Accessing paths relatively (e.g., `data/settings.json`, `cache/temp.txt`).
* **Blocked:** Path traversal attacks using `../` to access host system files (e.g., `../../etc/passwd`).

### 2. Strict Host Capability Paths
If the host application grants a plugin access to a specific directory (e.g., `/Users/name/Documents`), the plugin can only access that exact folder and its descendants. 
* **Blocked:** Attempting to access prefix-matching folders (e.g., `/Users/name/Documents_private`).

### 3. Asynchronous Initialization
Heavy blocking tasks inside `initialize()` or `performLoad()` will stall the plugin lifecycle. All heavy lifting should be delegated to coroutines scoped to the `PluginContext`.

> **Migration Plan:** Plugin initialization methods (`initialize()` and `performLoad()`) are now strictly sequential. If your plugin spawned a heavy blocking task directly inside `initialize()`, it will block subsequent reload attempts. Offload heavy work to Coroutines using the provided `PluginContext` lifecycle scope rather than hanging the initialization thread.

---

## 🔐 How to Sign a Plugin JAR

To distribute a plugin securely, its JAR file must be cryptographically signed. The host application strictly verifies the signatures of all files inside the JAR, including the `META-INF/MANIFEST.MF` file.

> **Migration Plan (Signed Manifests Requirement):** Older or custom-built test plugins that were signed without signing the manifest file itself will now fail to load at runtime. Ensure that any build tool (e.g., jarsigner) signs the entire archive, particularly ensuring `MANIFEST.MF` holds its digest under the `.SF` entries.

If a plugin is tampered with after signing, or if the manifest is left unsigned, the runtime will reject the plugin and it will not load.

### Step 1: Generate a Keystore
If you do not already have a Java Keystore, you can generate one using the `keytool` command. This creates a public/private key pair.

```bash
keytool -genkeypair -alias plugin-key -keyalg RSA -keysize 2048 -validity 3650 -keystore plugin-keystore.jks
```
*(You will be prompted to enter a password and some organizational details.)*

### Step 2: Sign the JAR File
Once your plugin is compiled into a JAR (e.g., `my-plugin.jar`), sign it using the `jarsigner` tool. It is critical to use strong cryptographic algorithms like `SHA256withRSA`.

```bash
jarsigner -keystore plugin-keystore.jks -sigalg SHA256withRSA -digestalg SHA-256 my-plugin.jar plugin-key
```

### Step 3: Verify the Signature (Optional but Recommended)
Before distributing your plugin, you can verify that the signature was applied correctly.

```bash
jarsigner -verify -verbose -certs my-plugin.jar
```
*If successful, you should see "jar verified."*

### Step 4: Extract the Public Key for the Host
The host application needs your public key to verify your plugin. You can export the X.509 certificate and convert it to Base64 to be added to the host's repository configuration.

```bash
# Export the certificate
keytool -exportcert -alias plugin-key -keystore plugin-keystore.jks -rfc -file plugin.pem
```
The contents of `plugin.pem` (between `-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----`) is the Base64-encoded string you provide to the host application repository settings.
