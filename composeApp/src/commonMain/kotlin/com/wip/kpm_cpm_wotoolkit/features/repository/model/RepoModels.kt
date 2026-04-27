package com.wip.kpm_cpm_wotoolkit.features.repository.model

import kotlinx.serialization.Serializable

@Serializable
data class ExtensionRepo(
    val name: String,
    val url: String, // index.json url
    val signPublicKey: String? = null,
    val signAlgorithm: String = "SHA256",
    val modulesFolder: String? = null
)

@Serializable
data class RepoIndex(
    val name: String? = null,
    val url: String? = null,
    val signPublicKey: String? = null,
    val signAlgorithm: String? = null,
    val modulesFolder: String? = null,
    val modules: List<ExtensionModule> = emptyList()
)

@Serializable
data class ExtensionModule(
    val name: String,
    val fileName: String,
    val pkg: String,
    val version: String,
    val minAppVersion: String? = null,
    val repoUrl: String? = null // Filled during parsing to track source
)

data class ModuleChangelog(
    val date: String,
    val version: String,
    val categories: Map<String, List<String>>
)
