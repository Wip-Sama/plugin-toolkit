package org.wip.complete

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.sdui.AssetReference

@Serializable
enum class ChapterSyncStatus {
    PUBLISHED,
    LOCAL_DRAFT,
    OUT_OF_SYNC,
    REMOTE_ONLY
}

@Serializable
data class SeriesRecord(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val sortOrder: Int = 0,
    val coverAsset: AssetReference? = null
)

@Serializable
data class ChapterPageRecord(
    val id: String,
    val label: String,
    val asset: AssetReference
)

@Serializable
data class ChapterRecord(
    val id: String,
    val number: Int,
    val title: String,
    val pageCount: Int,
    val sizeMb: Double,
    val localRevision: Int,
    val remoteRevision: Int,
    val status: ChapterSyncStatus,
    val releaseNotes: String = "",
    val requiresLock: Boolean = false,
    val coverAsset: AssetReference? = null,
    val pages: List<ChapterPageRecord> = emptyList(),
    val seriesId: String = CompleteExampleCatalogStore.SERIES_OBSIDIAN_ID
)

object CompleteExampleCatalogStore {
    const val SERIES_OBSIDIAN_ID = "series_obsidian"
    const val SERIES_CRIMSON_ID = "series_crimson"
    const val SERIES_ASTRAL_ID = "series_astral"

    const val STORAGE_SERIES_CATALOG = "chapter_publisher_series_catalog"
    const val STORAGE_LOCAL_CHAPTERS = "chapter_publisher_local_catalog"
    const val STORAGE_REMOTE_CHAPTERS = "chapter_publisher_remote_catalog"
    const val STORAGE_ENVIRONMENT = "chapter_publisher_environment"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun defaultSeries(): List<SeriesRecord> = listOf(
        SeriesRecord(
            id = SERIES_OBSIDIAN_ID,
            title = "Obsidian Tower Chronicles",
            author = "K. Arisugawa",
            description = "Flagship weekly portal-fantasy serialization synced across staging & production.",
            sortOrder = 0
        ),
        SeriesRecord(
            id = SERIES_CRIMSON_ID,
            title = "Crimson Gate Protocol",
            author = "M. Takashi",
            description = "Sci-fi tactical mini-series with dual-language lettering passes.",
            sortOrder = 1
        ),
        SeriesRecord(
            id = SERIES_ASTRAL_ID,
            title = "Astral Courier Zero",
            author = "R. Hayashi",
            description = "New bi-weekly anthology staged for upcoming Stage 1 & Stage 2 launch.",
            sortOrder = 2
        )
    )

    fun defaultLocalChapters(): List<ChapterRecord> = listOf(
        ChapterRecord(
            id = "ch_040",
            number = 40,
            title = "Echoes of the Obsidian Tower",
            pageCount = 28,
            sizeMb = 11.4,
            localRevision = 4,
            remoteRevision = 4,
            status = ChapterSyncStatus.PUBLISHED,
            releaseNotes = "Final lettering & SFX cleanup approved.",
            seriesId = SERIES_OBSIDIAN_ID
        ),
        ChapterRecord(
            id = "ch_041",
            number = 41,
            title = "Crimson Gate Activation",
            pageCount = 32,
            sizeMb = 14.8,
            localRevision = 3,
            remoteRevision = 2,
            status = ChapterSyncStatus.OUT_OF_SYNC,
            releaseNotes = "Fixed speech bubble overlap on page 12.",
            seriesId = SERIES_OBSIDIAN_ID
        ),
        ChapterRecord(
            id = "ch_042",
            number = 42,
            title = "Class-S Spatial Anomaly",
            pageCount = 30,
            sizeMb = 13.2,
            localRevision = 1,
            remoteRevision = 0,
            status = ChapterSyncStatus.LOCAL_DRAFT,
            releaseNotes = "Awaiting compositor final export.",
            seriesId = SERIES_OBSIDIAN_ID
        ),
        ChapterRecord(
            id = "cg_010",
            number = 10,
            title = "Relay Node Nine",
            pageCount = 24,
            sizeMb = 9.6,
            localRevision = 2,
            remoteRevision = 2,
            status = ChapterSyncStatus.PUBLISHED,
            releaseNotes = "Verified double-page spread alignment.",
            seriesId = SERIES_CRIMSON_ID
        ),
        ChapterRecord(
            id = "cg_011",
            number = 11,
            title = "Orbital Breacher",
            pageCount = 26,
            sizeMb = 10.9,
            localRevision = 2,
            remoteRevision = 1,
            status = ChapterSyncStatus.OUT_OF_SYNC,
            releaseNotes = "Updated tone screen density on pages 03-06.",
            seriesId = SERIES_CRIMSON_ID
        ),
        ChapterRecord(
            id = "ac_001",
            number = 1,
            title = "Launch Trajectory",
            pageCount = 22,
            sizeMb = 8.4,
            localRevision = 1,
            remoteRevision = 0,
            status = ChapterSyncStatus.LOCAL_DRAFT,
            releaseNotes = "Debut chapter ready for page order review.",
            seriesId = SERIES_ASTRAL_ID
        )
    )

    fun defaultRemoteChapters(): List<ChapterRecord> = listOf(
        ChapterRecord(
            id = "ch_040",
            number = 40,
            title = "Echoes of the Obsidian Tower",
            pageCount = 28,
            sizeMb = 11.4,
            localRevision = 4,
            remoteRevision = 4,
            status = ChapterSyncStatus.PUBLISHED,
            releaseNotes = "Final lettering & SFX cleanup approved.",
            seriesId = SERIES_OBSIDIAN_ID
        ),
        ChapterRecord(
            id = "ch_041",
            number = 41,
            title = "Crimson Gate Activation",
            pageCount = 32,
            sizeMb = 14.5,
            localRevision = 2,
            remoteRevision = 2,
            status = ChapterSyncStatus.PUBLISHED,
            releaseNotes = "Initial server upload.",
            seriesId = SERIES_OBSIDIAN_ID
        ),
        ChapterRecord(
            id = "ch_043",
            number = 43,
            title = "Translator Bonus & Epilogue",
            pageCount = 18,
            sizeMb = 6.5,
            localRevision = 0,
            remoteRevision = 1,
            status = ChapterSyncStatus.REMOTE_ONLY,
            releaseNotes = "Uploaded by co-editor on remote workstation.",
            requiresLock = true,
            seriesId = SERIES_OBSIDIAN_ID
        ),
        ChapterRecord(
            id = "cg_010",
            number = 10,
            title = "Relay Node Nine",
            pageCount = 24,
            sizeMb = 9.6,
            localRevision = 2,
            remoteRevision = 2,
            status = ChapterSyncStatus.PUBLISHED,
            releaseNotes = "Verified double-page spread alignment.",
            seriesId = SERIES_CRIMSON_ID
        ),
        ChapterRecord(
            id = "cg_011",
            number = 11,
            title = "Orbital Breacher",
            pageCount = 26,
            sizeMb = 10.4,
            localRevision = 1,
            remoteRevision = 1,
            status = ChapterSyncStatus.PUBLISHED,
            releaseNotes = "Initial release candidate.",
            seriesId = SERIES_CRIMSON_ID
        ),
        ChapterRecord(
            id = "cg_012",
            number = 12,
            title = "Signal Eclipse",
            pageCount = 20,
            sizeMb = 7.9,
            localRevision = 0,
            remoteRevision = 1,
            status = ChapterSyncStatus.REMOTE_ONLY,
            releaseNotes = "Staged on remote mirror only.",
            seriesId = SERIES_CRIMSON_ID
        )
    )

    suspend fun loadSeriesCatalog(storage: PluginStorage): List<SeriesRecord> {
        val raw = storage.get(STORAGE_SERIES_CATALOG)
        val decoded = raw?.let { element ->
            runCatching { json.decodeFromJsonElement<List<SeriesRecord>>(element) }.getOrNull()
        }
        if (!decoded.isNullOrEmpty()) return decoded.sortedBy { it.sortOrder }
        val defaults = defaultSeries()
        saveSeriesCatalog(storage, defaults)
        return defaults
    }

    suspend fun saveSeriesCatalog(storage: PluginStorage, series: List<SeriesRecord>) {
        storage.put(STORAGE_SERIES_CATALOG, json.encodeToJsonElement(series))
    }

    fun reconcile(local: List<ChapterRecord>, remote: List<ChapterRecord>): List<ChapterRecord> {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val allIds = (localById.keys + remoteById.keys).distinct()

        return allIds.mapNotNull { id ->
            val l = localById[id]
            val r = remoteById[id]
            when {
                l != null && r == null -> l.copy(
                    remoteRevision = 0,
                    status = ChapterSyncStatus.LOCAL_DRAFT
                )
                l == null && r != null -> r.copy(
                    localRevision = 0,
                    status = ChapterSyncStatus.REMOTE_ONLY
                )
                l != null && r != null -> {
                    val syncedStatus = if (l.localRevision == r.remoteRevision) {
                        ChapterSyncStatus.PUBLISHED
                    } else {
                        ChapterSyncStatus.OUT_OF_SYNC
                    }
                    l.copy(
                        remoteRevision = r.remoteRevision,
                        status = syncedStatus
                    )
                }
                else -> null
            }
        }.sortedBy { it.number }
    }

    suspend fun loadLocalCatalog(storage: PluginStorage): List<ChapterRecord> {
        val raw = storage.get(STORAGE_LOCAL_CHAPTERS)
        val decoded = raw?.let { element ->
            runCatching { json.decodeFromJsonElement<List<ChapterRecord>>(element) }.getOrNull()
        }
        if (decoded != null) return decoded
        val defaults = defaultLocalChapters()
        saveLocalCatalog(storage, defaults)
        return defaults
    }

    suspend fun loadRemoteCatalog(storage: PluginStorage): List<ChapterRecord> {
        val raw = storage.get(STORAGE_REMOTE_CHAPTERS)
        val decoded = raw?.let { element ->
            runCatching { json.decodeFromJsonElement<List<ChapterRecord>>(element) }.getOrNull()
        }
        if (decoded != null) return decoded
        val defaults = defaultRemoteChapters()
        saveRemoteCatalog(storage, defaults)
        return defaults
    }

    suspend fun saveLocalCatalog(storage: PluginStorage, chapters: List<ChapterRecord>) {
        storage.put(STORAGE_LOCAL_CHAPTERS, json.encodeToJsonElement(chapters))
    }

    suspend fun saveRemoteCatalog(storage: PluginStorage, chapters: List<ChapterRecord>) {
        storage.put(STORAGE_REMOTE_CHAPTERS, json.encodeToJsonElement(chapters))
    }
}
