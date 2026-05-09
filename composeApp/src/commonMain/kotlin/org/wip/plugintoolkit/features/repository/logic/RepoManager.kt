package org.wip.plugintoolkit.features.repository.logic

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

class RepoManager(
    private val settingsRepository: SettingsRepository,
    private val client: HttpClient,
    private val jsonConfig: Json
) {

    private val _repositories = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repositories: StateFlow<List<ExtensionRepo>> = _repositories.asStateFlow()

    private val _plugins = MutableStateFlow<Map<String, List<ExtensionPlugin>>>(emptyMap()) // repoUrl -> plugins
    val plugins: StateFlow<Map<String, List<ExtensionPlugin>>> = _plugins.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                _repositories.value = settings.extensions.repositories
            }
        }

        scope.launch {
            refreshAll()
        }
    }

    suspend fun addRepository(url: String): AddRepoResult {
        if (_repositories.value.any { it.url == url }) {
            Logger.w { "Repository already added: $url" }
            return AddRepoResult.AlreadyAdded
        }

        Logger.i { "Adding repository: $url" }
        return try {
            val response = client.get(url)
            val index: RepoIndex = response.body()

            val newRepo = ExtensionRepo(
                name = index.name ?: url.substringAfterLast("/").substringBeforeLast("."),
                url = url,
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
                        val baseUrl = url.substringBeforeLast("/") + "/" + (index.pluginsFolder ?: "plugins") + "/${plugin.pkg}"
                        val manifestContent = fetchText("$baseUrl/manifest.json")
                        val manifest = manifestContent?.let {
                            try {
                                jsonConfig.decodeFromString<PluginManifest>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        plugin.copy(repoUrl = url, manifest = manifest)
                    }
                }.awaitAll()
                _plugins.value += (url to updatedPlugins)
            }

            Logger.i { "Successfully added repository: ${newRepo.name} ($url)" }
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
        Logger.d { "Refreshing repository: $url" }
        try {
            val index: RepoIndex = client.get(url).body()
            
            coroutineScope {
                val updatedPlugins = index.plugins.map { plugin ->
                    async {
                        val baseUrl = url.substringBeforeLast("/") + "/" + (index.pluginsFolder ?: "plugins") + "/${plugin.pkg}"
                        val manifestContent = fetchText("$baseUrl/manifest.json")
                        val manifest = manifestContent?.let {
                            try {
                                jsonConfig.decodeFromString<PluginManifest>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        plugin.copy(repoUrl = url, manifest = manifest)
                    }
                }.awaitAll()
                _plugins.value += (url to updatedPlugins)
            }

            // Update repo metadata if changed
            val updatedRepos = _repositories.value.map {
                if (it.url == url) {
                    it.copy(
                        name = index.name ?: it.name,
                        signPublicKey = index.signPublicKey ?: it.signPublicKey,
                        signAlgorithm = index.signAlgorithm ?: it.signAlgorithm,
                        pluginsFolder = index.pluginsFolder ?: it.pluginsFolder
                    )
                } else it
            }
            if (updatedRepos != _repositories.value) {
                _repositories.value = updatedRepos
                saveReposToSettings(updatedRepos)
            }
            Logger.d { "Successfully refreshed repository: $url" }
            NotificationEvent.Toast(
                "Repository refreshed: ${
                    index.name ?: url.substringAfterLast("/").substringBeforeLast(".")
                }"
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to refresh repository: $url" }
            NotificationEvent.Toast("Failed to refresh repository: $url")
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
}

sealed class AddRepoResult {
    object Success : AddRepoResult()
    object AlreadyAdded : AddRepoResult()
    data class Error(val message: String) : AddRepoResult()
}
