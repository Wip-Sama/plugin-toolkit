package com.wip.kpm_cpm_wotoolkit.features.repository.logic

import co.touchlab.kermit.Logger
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationEvent
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionPlugin
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionRepo
import com.wip.kpm_cpm_wotoolkit.features.repository.model.PluginChangelog
import com.wip.kpm_cpm_wotoolkit.features.repository.model.RepoIndex
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
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
import kotlinx.serialization.json.Json

class RepoManager(
    private val settingsRepository: SettingsRepository
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val _repositories = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repositories: StateFlow<List<ExtensionRepo>> = _repositories.asStateFlow()

    private val _plugins = MutableStateFlow<Map<String, List<ExtensionPlugin>>>(emptyMap()) // repoUrl -> plugins
    val plugins: StateFlow<Map<String, List<ExtensionPlugin>>> = _plugins.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val settings = settingsRepository.loadSettings()
        _repositories.value = settings.extensions.repositories

        // Initial load from memory (if we had a cache, we'd load it here)
        // For now, we refresh on launch
        Logger.i { "Initializing RepoManager with ${settings.extensions.repositories.size} repositories" }
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

            _plugins.value = _plugins.value + (url to index.plugins.map { it.copy(repoUrl = url) })

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

        _plugins.value = _plugins.value - url
    }

    suspend fun refreshRepository(url: String) {
        Logger.d { "Refreshing repository: $url" }
        try {
            val index: RepoIndex = client.get(url).body()
            _plugins.value = _plugins.value + (url to index.plugins.map { it.copy(repoUrl = url) })

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
        val settings = settingsRepository.loadSettings()
        settingsRepository.saveSettings(
            settings.copy(
                extensions = settings.extensions.copy(repositories = repos)
            )
        )
    }

    suspend fun fetchText(url: String): String? {
        return try {
            client.get(url).body<String>()
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.e(e) { "Failed to fetch text from: $url" }
            null
        }
    }

    fun setPackageSourceOverride(pkg: String, repoUrl: String) {
        val settings = settingsRepository.loadSettings()
        settingsRepository.saveSettings(
            settings.copy(
                extensions = settings.extensions.copy(
                    packageSourceOverrides = settings.extensions.packageSourceOverrides + (pkg to repoUrl)
                )
            )
        )
    }

    fun getPackageSourceOverride(pkg: String): String? {
        return settingsRepository.loadSettings().extensions.packageSourceOverrides[pkg]
    }

    fun parseChangelog(content: String): List<PluginChangelog> {
        val lines = content.lines()
        val changelogs = mutableListOf<PluginChangelog>()
        var currentChangelog: PluginChangelog? = null
        var currentCategory: String? = null

        val headerRegex = Regex("""\[(\d{2}/\d{2}/\d{4})\] Version: (.*)""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val match = headerRegex.matchEntire(trimmed)
            if (match != null) {
                if (currentChangelog != null) {
                    changelogs.add(currentChangelog)
                }
                currentChangelog = PluginChangelog(match.groupValues[1], match.groupValues[2], mutableMapOf())
                currentCategory = null
            } else if (trimmed.endsWith(":")) {
                currentCategory = trimmed.removeSuffix(":")
            } else if (trimmed.startsWith("-") && currentCategory != null && currentChangelog != null) {
                val voice = trimmed.removePrefix("-").trim()
                val categories = currentChangelog.categories as MutableMap<String, MutableList<String>>
                val voices = categories.getOrPut(currentCategory) { mutableListOf() }
                voices.add(voice)
            }
        }
        if (currentChangelog != null) {
            changelogs.add(currentChangelog)
        }
        return changelogs
    }
}

sealed class AddRepoResult {
    object Success : AddRepoResult()
    object AlreadyAdded : AddRepoResult()
    data class Error(val message: String) : AddRepoResult()
}
