package com.wip.kpm_cpm_wotoolkit.features.repository.logic

import com.wip.kpm_cpm_wotoolkit.features.repository.model.*
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _modules = MutableStateFlow<Map<String, List<ExtensionModule>>>(emptyMap()) // repoUrl -> modules
    val modules: StateFlow<Map<String, List<ExtensionModule>>> = _modules.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val settings = settingsRepository.loadSettings()
        _repositories.value = settings.extensions.repositories
        
        // Initial load from memory (if we had a cache, we'd load it here)
        // For now, we refresh on launch
        scope.launch {
            refreshAll()
        }
    }

    suspend fun addRepository(url: String): AddRepoResult {
        if (_repositories.value.any { it.url == url }) {
            return AddRepoResult.AlreadyAdded
        }

        return try {
            val response = client.get(url)
            val index: RepoIndex = response.body()
            
            val newRepo = ExtensionRepo(
                name = index.name ?: url.substringAfterLast("/").substringBeforeLast("."),
                url = url,
                signPublicKey = index.signPublicKey,
                signAlgorithm = index.signAlgorithm ?: "SHA256",
                modulesFolder = index.modulesFolder
            )
            
            val updatedRepos = _repositories.value + newRepo
            _repositories.value = updatedRepos
            
            saveReposToSettings(updatedRepos)

            _modules.value = _modules.value + (url to index.modules.map { it.copy(repoUrl = url) })
            
            AddRepoResult.Success
        } catch (e: Exception) {
            AddRepoResult.Error(e.message ?: "Unknown error")
        }
    }

    fun removeRepository(url: String) {
        val updatedRepos = _repositories.value.filter { it.url != url }
        _repositories.value = updatedRepos
        
        saveReposToSettings(updatedRepos)
        
        _modules.value = _modules.value - url
    }

    suspend fun refreshRepository(url: String) {
        try {
            val index: RepoIndex = client.get(url).body()
            _modules.value = _modules.value + (url to index.modules.map { it.copy(repoUrl = url) })
            
            // Update repo metadata if changed
            val updatedRepos = _repositories.value.map { 
                if (it.url == url) {
                    it.copy(
                        name = index.name ?: it.name,
                        signPublicKey = index.signPublicKey ?: it.signPublicKey,
                        signAlgorithm = index.signAlgorithm ?: it.signAlgorithm,
                        modulesFolder = index.modulesFolder ?: it.modulesFolder
                    )
                } else it
            }
            if (updatedRepos != _repositories.value) {
                _repositories.value = updatedRepos
                saveReposToSettings(updatedRepos)
            }
        } catch (e: Exception) {
            // Log error or notify
        }
    }

    suspend fun refreshAll() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        try {
            _repositories.value.forEach { 
                refreshRepository(it.url)
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    private fun saveReposToSettings(repos: List<ExtensionRepo>) {
        val settings = settingsRepository.loadSettings()
        settingsRepository.saveSettings(settings.copy(
            extensions = settings.extensions.copy(repositories = repos)
        ))
    }

    fun setPackageSourceOverride(pkg: String, repoUrl: String) {
        val settings = settingsRepository.loadSettings()
        settingsRepository.saveSettings(settings.copy(
            extensions = settings.extensions.copy(
                packageSourceOverrides = settings.extensions.packageSourceOverrides + (pkg to repoUrl)
            )
        ))
    }

    fun getPackageSourceOverride(pkg: String): String? {
        return settingsRepository.loadSettings().extensions.packageSourceOverrides[pkg]
    }

    fun parseChangelog(content: String): List<ModuleChangelog> {
        val lines = content.lines()
        val changelogs = mutableListOf<ModuleChangelog>()
        var currentChangelog: ModuleChangelog? = null
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
                currentChangelog = ModuleChangelog(match.groupValues[1], match.groupValues[2], mutableMapOf())
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
