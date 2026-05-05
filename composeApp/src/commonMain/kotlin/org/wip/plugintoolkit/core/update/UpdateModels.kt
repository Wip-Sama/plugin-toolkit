package org.wip.plugintoolkit.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val assets: List<GithubAsset>
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long
)

data class UpdateInfo(
    val version: String,
    val changelog: String?,
    val downloadUrl: String,
    val fileName: String,
    val size: Long
)
