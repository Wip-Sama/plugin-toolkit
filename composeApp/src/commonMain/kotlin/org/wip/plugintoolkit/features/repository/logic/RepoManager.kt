package org.wip.plugintoolkit.features.repository.logic

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.RepoIndex
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.features.plugin.logic.PluginSecurity

class RepoManager(
    private val settingsRepository: SettingsRepository,
    private val client: HttpClient,
    private val jsonConfig: Json,
    private val scope: CoroutineScope
) {

    private val _repositories = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repositories: StateFlow<List<ExtensionRepo>> = _repositories.asStateFlow()

    private val _plugins = MutableStateFlow<Map<String, List<ExtensionPlugin>>>(emptyMap()) // repoUrl -> plugins
    val plugins: StateFlow<Map<String, List<ExtensionPlugin>>> = _plugins.asStateFlow()

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
        val trimmedUrl = url.replace(Regex("\\s+"), "").replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        
        if (_repositories.value.any { it.url == trimmedUrl }) {

            Logger.w { "Repository already added: $trimmedUrl" }
            return AddRepoResult.AlreadyAdded
        }

        Logger.i { "Adding repository: $trimmedUrl" }
        return try {
            val response = client.get(trimmedUrl)
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("Server returned ${response.status}: $errorBody")
            }

            val responseBody = response.bodyAsText()
            val index = jsonConfig.decodeFromString<RepoIndex>(responseBody)

            Logger.v { "Index: $index" }


            val newRepo = ExtensionRepo(
                name = index.name ?: trimmedUrl.substringAfterLast("/").substringBeforeLast("."),
                url = trimmedUrl,
                schemaVersion = index.schemaVersion,
                signPublicKey = index.signPublicKey,
                signAlgorithm = index.signAlgorithm ?: "SHA256",
                pluginsFolder = index.pluginsFolder
            )


            val updatedRepos = _repositories.value + newRepo
            _repositories.value = updatedRepos

            saveReposToSettings(updatedRepos)

            coroutineScope {
                val updatedPlugins = index.plugins.map { plugin ->
                    async {
                        val baseUrl = trimmedUrl.substringBeforeLast("/") + "/" + (index.pluginsFolder ?: "plugins") + "/${plugin.pkg}"
                        val manifestContent = fetchText("$baseUrl/manifest.json")
                        val manifest = manifestContent?.let {
                            try {
                                jsonConfig.decodeFromString<PluginManifest>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        val isSignatureValid = if (index.signPublicKey != null && plugin.signature != null && plugin.hash != null) {
                            PluginSecurity.verifyDetached(
                                plugin.hash,
                                plugin.signature,
                                index.signPublicKey
                            )
                        } else null
                        plugin.copy(repoUrl = trimmedUrl, manifest = manifest, isSignatureValid = isSignatureValid)
                    }
                }.awaitAll()
                _plugins.value += (trimmedUrl to updatedPlugins)
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
    }

    suspend fun refreshRepository(url: String) {
        val trimmedUrl = url.replace(Regex("\\s+"), "").replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        Logger.d { "Refreshing repository: $trimmedUrl" }
        try {
            val response = client.get(trimmedUrl)

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("Server returned ${response.status}: $errorBody")
            }
            val responseBody = response.bodyAsText()
            val index = jsonConfig.decodeFromString<RepoIndex>(responseBody)
            
            coroutineScope {
                val updatedPlugins = index.plugins.map { plugin ->
                    async {
                        val baseUrl = trimmedUrl.substringBeforeLast("/") + "/" + (index.pluginsFolder ?: "plugins") + "/${plugin.pkg}"

                        val manifestContent = fetchText("$baseUrl/manifest.json")
                        val manifest = manifestContent?.let {
                            try {
                                jsonConfig.decodeFromString<PluginManifest>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        val isSignatureValid = if (index.signPublicKey != null && plugin.signature != null && plugin.hash != null) {
                            PluginSecurity.verifyDetached(
                                plugin.hash,
                                plugin.signature,
                                index.signPublicKey
                            )
                        } else null
                        plugin.copy(repoUrl = trimmedUrl, manifest = manifest, isSignatureValid = isSignatureValid)
                    }
                }.awaitAll()
                _plugins.value += (trimmedUrl to updatedPlugins)
            }

            // Update repo metadata if changed
            val updatedRepos = _repositories.value.map {
                if (it.url == trimmedUrl) {

                    it.copy(
                        name = index.name ?: it.name,
                        schemaVersion = index.schemaVersion,
                        signPublicKey = index.signPublicKey,
                        signAlgorithm = index.signAlgorithm ?: it.signAlgorithm,
                        pluginsFolder = index.pluginsFolder ?: it.pluginsFolder
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
                    index.name ?: trimmedUrl.substringAfterLast("/").substringBeforeLast(".")
                }"
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to refresh repository: $trimmedUrl" }
            NotificationEvent.Toast("Failed to refresh repository: $trimmedUrl")
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

    private fun saveReposToSettings(repos: List<ExtensionRepo>) {
        settingsRepository.updateSettings { 
            it.copy(extensions = it.extensions.copy(repositories = repos))
        }
    }

    suspend fun fetchText(url: String): String? {
        return try {
            client.get(url).body<String>()
        } catch (e: Exception) {
            Logger.e(e) { "Failed to fetch text from: $url" }
            null
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
        val pluginsFolder = repoUrl.substringBeforeLast("/") + "/plugins"
        val baseUrl = "$pluginsFolder/${remote.pkg}"
        return fetchText("$baseUrl/changelog.md")
    }
}

sealed class AddRepoResult {
    object Success : AddRepoResult()
    object AlreadyAdded : AddRepoResult()
    data class Error(val message: String) : AddRepoResult()
}
