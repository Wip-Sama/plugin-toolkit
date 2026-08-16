package org.wip.plugintoolkit.features.repository.logic

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.FileUtils
import org.wip.plugintoolkit.core.utils.RealFileSystem
import org.wip.plugintoolkit.features.plugin.logic.PluginSecurity
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.RepoIndex
import org.wip.plugintoolkit.features.repository.model.RepoValidationResult
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

class RepoManager(
    private val settingsRepository: SettingsRepository,
    private val client: HttpClient,
    private val jsonConfig: Json,
    scope: CoroutineScope,
    private val fileSystem: FileSystem = RealFileSystem()
) {

    private val _repositories = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repositories: StateFlow<List<ExtensionRepo>> = _repositories.asStateFlow()

    private val _plugins = MutableStateFlow<Map<String, List<ExtensionPlugin>>>(emptyMap()) // repoUrl -> plugins
    val plugins: StateFlow<Map<String, List<ExtensionPlugin>>> = _plugins.asStateFlow()

    private val _flows = MutableStateFlow<Map<String, List<ExtensionFlow>>>(emptyMap()) // repoUrl -> flows
    val flows: StateFlow<Map<String, List<ExtensionFlow>>> = _flows.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        scope.launch {
            var startupHandled = false
            // Synchronize repositories with settings and trigger initial load
            settingsRepository.settings.collect { settings ->
                _repositories.value = settings.extensions.repositories

                if (!startupHandled) {
                    startupHandled = true
                    refreshAll()
                }
            }
        }
    }

    suspend fun addRepository(url: String): AddRepoResult {
        // Aggressively clean the URL to remove any hidden characters or whitespace
        val trimmedUrl = url.trim().replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")

        if (_repositories.value.any { it.url == trimmedUrl }) {
            Logger.w { "Repository already added: $trimmedUrl" }
            return AddRepoResult.AlreadyAdded
        }

        Logger.i { "Adding repository: $trimmedUrl" }
        return try {
            val index = loadRepoIndex(trimmedUrl)
            Logger.v { "Index: $index" }

            val defaultName = if (isLocalUrl(trimmedUrl)) {
                trimmedUrl.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
            } else {
                trimmedUrl.substringAfterLast("/").substringBeforeLast(".")
            }

            val newRepo = ExtensionRepo(
                name = index.name ?: defaultName,
                url = trimmedUrl,
                schemaVersion = index.schemaVersion,
                signPublicKey = index.signPublicKey,
                signAlgorithm = index.signAlgorithm ?: "SHA256",
                pluginsFolder = index.pluginsFolder,
                flowsFolder = index.flowsFolder
            )

            val updatedRepos = _repositories.value + newRepo
            _repositories.value = updatedRepos
            saveReposToSettings(updatedRepos)

            coroutineScope {
                val updatedPlugins = index.plugins.map { plugin ->
                    async {
                        val baseLocation = getBaseLocation(trimmedUrl)
                        val pluginsFolder = index.pluginsFolder ?: "plugins"
                        val manifestPath = "$baseLocation/$pluginsFolder/${plugin.pkg}/manifest.json"
                        val manifestContent = fetchText(manifestPath)
                        val manifest = manifestContent?.let {
                            try {
                                jsonConfig.decodeFromString<PluginManifest>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        val pubKey = index.signPublicKey
                        val pSig = plugin.signature
                        val pHash = plugin.hash
                        val isSignatureValid =
                            if (pubKey != null && pSig != null && pHash != null) {
                                PluginSecurity.verifyDetached(
                                    pHash,
                                    pSig,
                                    pubKey
                                )
                            } else null
                        plugin.copy(repoUrl = trimmedUrl, manifest = manifest, isSignatureValid = isSignatureValid)
                    }
                }.awaitAll()
                _plugins.value += (trimmedUrl to updatedPlugins)

                val updatedFlows = index.flows.map { flow ->
                    val pubKey = index.signPublicKey
                    val fSig = flow.signature
                    val fHash = flow.hash
                    val isSignatureValid =
                        if (pubKey != null && fSig != null && fHash != null) {
                            PluginSecurity.verifyDetached(
                                fHash,
                                fSig,
                                pubKey
                            )
                        } else null
                    flow.copy(repoUrl = trimmedUrl, isSignatureValid = isSignatureValid)
                }
                _flows.value += (trimmedUrl to updatedFlows)

            }

            Logger.i { "Successfully added repository: ${newRepo.name} ($trimmedUrl)" }
            AddRepoResult.Success
        } catch (e: Exception) {
            Logger.e(e) { "Failed to add repository: $url" }
            AddRepoResult.Error(e.message ?: "Unknown error")
        }
    }

    fun removeRepository(url: String) {
        Logger.i { "Removing repository: $url" }
        val updatedRepos = _repositories.value.filter { it.url != url }
        _repositories.value = updatedRepos

        saveReposToSettings(updatedRepos)

        _plugins.value -= url
        _flows.value -= url
    }

    suspend fun refreshRepository(url: String) {
        val trimmedUrl = url.trim().replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        Logger.d { "Refreshing repository: $trimmedUrl" }
        try {
            val index = loadRepoIndex(trimmedUrl)

            coroutineScope {
                val updatedPlugins = index.plugins.map { plugin ->
                    async {
                        val baseLocation = getBaseLocation(trimmedUrl)
                        val pluginsFolder = index.pluginsFolder ?: "plugins"
                        val manifestPath = "$baseLocation/$pluginsFolder/${plugin.pkg}/manifest.json"
                        val manifestContent = fetchText(manifestPath)
                        val manifest = manifestContent?.let {
                            try {
                                jsonConfig.decodeFromString<PluginManifest>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        val pubKey = index.signPublicKey
                        val pSig = plugin.signature
                        val pHash = plugin.hash
                        val isSignatureValid =
                            if (pubKey != null && pSig != null && pHash != null) {
                                PluginSecurity.verifyDetached(
                                    pHash,
                                    pSig,
                                    pubKey
                                )
                            } else null
                        plugin.copy(repoUrl = trimmedUrl, manifest = manifest, isSignatureValid = isSignatureValid)
                    }
                }.awaitAll()
                _plugins.value += (trimmedUrl to updatedPlugins)

                val updatedFlows = index.flows.map { flow ->
                    val pubKey = index.signPublicKey
                    val fSig = flow.signature
                    val fHash = flow.hash
                    val isSignatureValid =
                        if (pubKey != null && fSig != null && fHash != null) {
                            PluginSecurity.verifyDetached(
                                fHash,
                                fSig,
                                pubKey
                            )
                        } else null
                    flow.copy(repoUrl = trimmedUrl, isSignatureValid = isSignatureValid)
                }
                _flows.value += (trimmedUrl to updatedFlows)

            }

            // Update repo metadata if changed
            val updatedRepos = _repositories.value.map {
                if (it.url == trimmedUrl) {
                    it.copy(
                        name = index.name ?: it.name,
                        schemaVersion = index.schemaVersion,
                        signPublicKey = index.signPublicKey,
                        signAlgorithm = index.signAlgorithm ?: it.signAlgorithm,
                        pluginsFolder = index.pluginsFolder ?: it.pluginsFolder,
                        flowsFolder = index.flowsFolder ?: it.flowsFolder
                    )
                } else it
            }
            if (updatedRepos != _repositories.value) {
                _repositories.value = updatedRepos
                saveReposToSettings(updatedRepos)
            }
            Logger.d { "Successfully refreshed repository: $trimmedUrl" }
            NotificationEvent.Toast(
                "Repository refreshed: ${
                    index.name ?: if (isLocalUrl(trimmedUrl)) {
                        trimmedUrl.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
                    } else {
                        trimmedUrl.substringAfterLast("/").substringBeforeLast(".")
                    }
                }".localized
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to refresh repository: $trimmedUrl" }
            NotificationEvent.Toast("Failed to refresh repository: $trimmedUrl".localized)
        }
    }

    suspend fun refreshAll() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        Logger.i { "Refreshing all repositories..." }
        try {
            _repositories.value.forEach {
                refreshRepository(it.url)
            }
        } finally {
            _isRefreshing.value = false
            Logger.i { "All repositories refreshed" }
        }
    }

    suspend fun validateRepository(target: String): RepoValidationResult {
        val trimmedTarget = target.trim().replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        if (trimmedTarget.isEmpty()) {
            return RepoValidationResult.Invalid("Please specify a repository address or file path.")
        }
        val isLocal = isLocalUrl(trimmedTarget)
        return try {
            val index = loadRepoIndex(trimmedTarget)
            val defaultName = if (isLocal) {
                trimmedTarget.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
            } else {
                trimmedTarget.substringAfterLast("/").substringBeforeLast(".")
            }
            RepoValidationResult.Valid(
                name = index.name ?: defaultName,
                pluginCount = index.plugins.size,
                flowCount = index.flows.size,
                index = index,
                isLocal = isLocal
            )
        } catch (e: Exception) {
            Logger.e(e) { "Validation error for repository target: $target" }
            RepoValidationResult.Invalid(e.message ?: "Failed to validate repository manifest")
        }
    }

    private suspend fun loadRepoIndex(urlOrPath: String): RepoIndex {
        return if (isLocalUrl(urlOrPath)) {
            val normalizedPath = urlOrPath.removePrefix("file://").removePrefix("file:/")
            if (!fileSystem.exists(normalizedPath)) {
                throw Exception("File does not exist: $normalizedPath")
            }
            val content = fileSystem.readFile(normalizedPath)
                ?: throw Exception("Could not read file content: $normalizedPath")
            jsonConfig.decodeFromString<RepoIndex>(content)
        } else {
            val response = client.get(urlOrPath)
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("Server returned ${response.status}: $errorBody")
            }
            val responseBody = response.bodyAsText()
            jsonConfig.decodeFromString<RepoIndex>(responseBody)
        }
    }

    private fun isLocalUrl(url: String): Boolean {
        return !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)
    }

    private fun getBaseLocation(urlOrPath: String): String {
        val normalized = urlOrPath.replace('\\', '/')
        return if (normalized.contains('/')) normalized.substringBeforeLast("/") else normalized
    }

    private fun saveReposToSettings(repos: List<ExtensionRepo>) {
        settingsRepository.updateSettings {
            it.copy(extensions = it.extensions.copy(repositories = repos))
        }
    }

    suspend fun fetchText(url: String): String? {
        return if (isLocalUrl(url)) {
            val normalizedPath = url.removePrefix("file://").removePrefix("file:/")
            fileSystem.readFile(normalizedPath)
        } else {
            try {
                client.get(url).body<String>()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to fetch text from: $url" }
                null
            }
        }
    }

    suspend fun fetchBytes(url: String): ByteArray? {
        return if (isLocalUrl(url)) {
            val normalizedPath = url.removePrefix("file://").removePrefix("file:/")
            FileUtils.readBytes(normalizedPath)
        } else {
            try {
                client.get(url).readBytes()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to fetch bytes from: $url" }
                null
            }
        }
    }

    fun setPackageSourceOverride(pkg: String, repoUrl: String) {
        settingsRepository.updateSettings {
            it.copy(
                extensions = it.extensions.copy(
                    packageSourceOverrides = it.extensions.packageSourceOverrides + (pkg to repoUrl)
                )
            )
        }
    }

    fun getPackageSourceOverride(pkg: String): String? {
        return settingsRepository.loadSettings().extensions.packageSourceOverrides[pkg]
    }

    suspend fun fetchRemoteChangelog(pkg: String): String? {
        val remote = _plugins.value.values.flatten().find { it.pkg == pkg } ?: return null
        val repoUrl = remote.repoUrl ?: return null
        val baseLocation = getBaseLocation(repoUrl)
        val repo = _repositories.value.find { it.url == repoUrl }
        val pluginsFolder = repo?.pluginsFolder ?: "plugins"
        val changelogPath = "$baseLocation/$pluginsFolder/${remote.pkg}/changelog.md"
        return fetchText(changelogPath)
    }
}

sealed class AddRepoResult {
    object Success : AddRepoResult()
    object AlreadyAdded : AddRepoResult()
    data class Error(val message: String) : AddRepoResult()
}
