package org.wip.complete

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.wip.plugintoolkit.api.PluginStorage

@Serializable
enum class ChapterSyncStatus {
    PUBLISHED,
    LOCAL_DRAFT,
    OUT_OF_SYNC,
    REMOTE_ONLY
}

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
    val requiresLock: Boolean = false
)

object CompleteExampleCatalogStore {
    const val STORAGE_LOCAL_CHAPTERS = "chapter_publisher_local_catalog"
    const val STORAGE_REMOTE_CHAPTERS = "chapter_publisher_remote_catalog"
    const val STORAGE_ENVIRONMENT = "chapter_publisher_environment"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
            releaseNotes = "Final lettering & SFX cleanup approved."
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
            releaseNotes = "Fixed speech bubble overlap on page 12."
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
            releaseNotes = "Awaiting compositor final export."
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
            releaseNotes = "Final lettering & SFX cleanup approved."
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
            releaseNotes = "Initial server upload."
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
            requiresLock = true
        )
    )

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
