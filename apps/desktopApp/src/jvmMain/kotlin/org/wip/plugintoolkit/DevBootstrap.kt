package org.wip.plugintoolkit

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ManifestLoader
import org.wip.plugintoolkit.core.utils.FileUtils
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.repository.logic.AddRepoResult
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import java.io.File
import java.security.MessageDigest

/**
 * Resolves developer/testing startup options (portable overrides, dev plugins, and local repository)
 * and synchronizes them into the active sandbox on startup.
 */
object DevBootstrap {
    const val PROP_PORTABLE = "plugintoolkit.portable"
    const val PROP_PORTABLE_DIR = "plugintoolkit.portable.dir"
    const val PROP_DEV_PLUGINS = "plugintoolkit.dev.plugins"
    const val PROP_DEV_REPO = "plugintoolkit.dev.repo"

    const val FLAG_DEV_PLUGIN = "--dev-plugin"
    const val FLAG_DEV_REPO = "--dev-repo"

    private val DEV_REQUIRED_SETTINGS_DEFAULTS: Map<String, Map<String, JsonElement>> = mapOf(
        "org.wip.complete" to mapOf(
            "secretToken" to JsonPrimitive("secret-12345"),
            "userId" to JsonPrimitive("user_demo")
        )
    )

    fun resolveCliOptionValues(args: Array<String>, flag: String): List<String> {
        val values = mutableListOf<String>()
        val prefix = "$flag="
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg.startsWith(prefix) -> {
                    val value = arg.substringAfter(prefix).trim()
                    if (value.isNotEmpty()) values.add(value)
                }
                arg == flag && i + 1 < args.size -> {
                    val next = args[i + 1].trim()
                    if (next.isNotEmpty() && !next.startsWith("--")) {
                        values.add(next)
                        i++
                    }
                }
            }
            i++
        }
        return values
    }

    fun resolveDevPluginJars(args: Array<String>): List<File> {
        val cliPaths = resolveCliOptionValues(args, FLAG_DEV_PLUGIN)
        val propPaths = System.getProperty(PROP_DEV_PLUGINS)
            ?.split(File.pathSeparatorChar)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val seenCanonicalPaths = linkedSetOf<String>()
        val result = mutableListOf<File>()
        (cliPaths + propPaths).forEach { rawPath ->
            val file = File(rawPath)
            if (file.exists() && file.isFile && file.name.endsWith(".jar", ignoreCase = true)) {
                val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
                if (seenCanonicalPaths.add(canonical)) {
                    result.add(file)
                }
            } else {
                Logger.w { "DevBootstrap: Ignoring missing or non-JAR dev plugin path: $rawPath" }
            }
        }
        return result
    }

    fun resolveDevRepoPath(args: Array<String>): String? {
        val rawPath = resolveCliOptionValues(args, FLAG_DEV_REPO).lastOrNull()
            ?: System.getProperty(PROP_DEV_REPO)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val file = File(rawPath)
        if (!file.exists() || !file.isFile) {
            Logger.w { "DevBootstrap: Dev repository index does not exist: $rawPath" }
            return null
        }
        return runCatching { file.canonicalPath }.getOrElse { file.absolutePath }.replace('\\', '/')
    }

    suspend fun syncDevPlugins(
        jarFiles: List<File>,
        pluginRegistry: PluginRegistry,
        pluginManager: PluginManager
    ): Set<String> {
        if (jarFiles.isEmpty()) return emptySet()

        val handledPkgs = linkedSetOf<String>()
        val targetFolder = pluginRegistry.getDefaultPluginFolder()

        for (jarFile in jarFiles) {
            val manifestContent = FileUtils.readFileFromZip(jarFile.absolutePath, "META-INF/manifest.json")
                ?: FileUtils.readFileFromZip(jarFile.absolutePath, "manifest.json")
            if (manifestContent == null) {
                Logger.w { "DevBootstrap: Skipping dev JAR without manifest.json: ${jarFile.absolutePath}" }
                continue
            }

            val manifest = runCatching { ManifestLoader.loadFromString(manifestContent) }
                .onFailure { Logger.e(it) { "DevBootstrap: Failed to parse manifest from ${jarFile.absolutePath}" } }
                .getOrNull() ?: continue

            val pkg = manifest.plugin.id
            val existing = pluginRegistry.getPlugin(pkg)
            val installedJar = existing?.let {
                val jarName = it.jarFileName ?: "${pkg.substringAfterLast('.')}.jar"
                File(it.installPath, jarName)
            }

            val needsInstall = existing == null ||
                installedJar == null ||
                !installedJar.exists() ||
                !filesMatchSha256(jarFile, installedJar)

            if (!needsInstall) {
                Logger.d { "DevBootstrap: Dev plugin $pkg is already up to date in $targetFolder" }
                continue
            }

            Logger.i { "DevBootstrap: Installing/updating dev plugin $pkg from ${jarFile.absolutePath}" }
            val installResult = pluginManager.installLocal(jarFile.absolutePath, targetFolder)
            if (installResult.isFailure) {
                Logger.e { "DevBootstrap: Failed to install dev plugin $pkg: ${installResult.exceptionOrNull()?.message}" }
                continue
            }

            handledPkgs.add(pkg)
            val updatedPlugin = pluginRegistry.getPlugin(pkg)
            if (updatedPlugin?.requiredAction == "CONFIGURE_SETTINGS") {
                val defaults = DEV_REQUIRED_SETTINGS_DEFAULTS[pkg]
                if (!defaults.isNullOrEmpty()) {
                    Logger.i { "DevBootstrap: Seeding required dev settings for $pkg and resuming setup" }
                    pluginManager.updatePluginSettings(pkg, defaults)
                    pluginManager.checkAndResumeSetup(pkg)
                }
            }
        }

        return handledPkgs
    }

    suspend fun registerDevRepo(
        repoPath: String,
        settingsRepository: SettingsRepository,
        repoManager: RepoManager
    ) {
        settingsRepository.isLoaded.first { it }
        val normalizedTarget = normalizePath(repoPath)
        val alreadyPresent = repoManager.repositories.value.any {
            normalizePath(it.url) == normalizedTarget
        }
        if (alreadyPresent) {
            Logger.d { "DevBootstrap: Dev repository already registered: $normalizedTarget" }
            return
        }

        Logger.i { "DevBootstrap: Registering local dev repository: $normalizedTarget" }
        when (val result = repoManager.addRepository(normalizedTarget)) {
            is AddRepoResult.Success -> Logger.i { "DevBootstrap: Local dev repository registered successfully" }
            is AddRepoResult.AlreadyAdded -> Logger.d { "DevBootstrap: Local dev repository was already registered" }
            is AddRepoResult.Error -> Logger.w { "DevBootstrap: Failed to register dev repository: ${result.message}" }
        }
    }

    private fun normalizePath(path: String): String {
        val file = File(path)
        val resolved = if (file.exists()) {
            runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        } else {
            path
        }
        return resolved.replace('\\', '/').removeSuffix("/")
    }

    private fun filesMatchSha256(first: File, second: File): Boolean {
        if (first.length() != second.length()) return false
        return sha256(first) == sha256(second)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
