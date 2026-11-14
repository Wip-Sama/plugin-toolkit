package org.wip.complete

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.PluginNotificationType
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.CapabilityJobSnapshot
import org.wip.plugintoolkit.api.sdui.CapabilityJobStatus
import org.wip.plugintoolkit.api.sdui.ChipItem
import org.wip.plugintoolkit.api.sdui.ControlSize
import org.wip.plugintoolkit.api.sdui.ImageScale
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TabItem
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiContainerBuilder
import org.wip.plugintoolkit.api.sdui.UiDropTarget
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.UiShortcut
import org.wip.plugintoolkit.api.sdui.uiTree

private val publisherJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val STORAGE_LINKED_REPO_PATH = "chapter_publisher_repo_path"
private const val SECRET_PUBLISHER_API_KEY = "publisher_api_secret"
private const val DEFAULT_PAGES_PER_CHAPTER = 6

private enum class PublisherNavLevel {
    SERIES,
    CHAPTERS,
    IMAGES
}

private data class ChapterPublisherState(
    val activeTab: String = "chapters",
    val navLevel: PublisherNavLevel = PublisherNavLevel.SERIES,
    val environment: String = "STAGING",
    val seriesList: List<SeriesRecord> = emptyList(),
    val selectedSeriesId: String = CompleteExampleCatalogStore.SERIES_OBSIDIAN_ID,
    val seriesSearchQuery: String = "",
    val seriesSortMode: String = "CUSTOM",
    val searchQuery: String = "",
    val statusFilter: String = "ALL",
    val chapters: List<ChapterRecord> = emptyList(),
    val selectedChapterIds: Set<String> = emptySet(),
    val inspectedChapterId: String? = null,
    val selectedPageId: String? = null,
    val splitFraction: Float = 0.54f,
    val pageOrderRevision: Int = 0,
    val draftTitle: String = "",
    val draftNotes: String = "",
    val draftRequiresLock: Boolean = false,
    val draftRevision: Int = 0,
    val draftSecretToken: String = "",
    val draftSecretRevision: Int = 0,
    val secretConfigured: Boolean = false,
    val linkedRepoPath: String = "",
    val webhookStatus: String? = null,
    val isLoading: Boolean = true,
    val showConfirmDialog: Boolean = false,
    val pendingPushIds: List<String> = emptyList(),
    val bannerMessage: String? = "Browse a series to manage chapters and reorder pages.",
    val bannerSeverity: SemanticColor = SemanticColor.INFO
)

internal fun runChapterPublisherSession(
    context: UiSessionContext,
    events: Flow<UiEvent>
): Flow<UiNodeTree> = channelFlow {
    val stateFlow = MutableStateFlow(ChapterPublisherState())
    val handledCompletedJobIds = mutableSetOf<String>()

    suspend fun enrichSeries(series: List<SeriesRecord>): List<SeriesRecord> =
        series.mapIndexed { idx, entry ->
            val cover = entry.coverAsset ?: CompleteExampleSampleAssets.ensureSeriesCoverAsset(
                context = context,
                seriesId = entry.id,
                title = entry.title,
                accentIndex = idx
            )
            entry.copy(coverAsset = cover)
        }

    suspend fun enrichChapter(chapter: ChapterRecord): ChapterRecord {
        val cover = chapter.coverAsset ?: CompleteExampleSampleAssets.ensureChapterCoverAsset(
            context = context,
            chapterId = chapter.id,
            chapterNumber = chapter.number
        )
        val pages = chapter.pages.ifEmpty {
            (1..DEFAULT_PAGES_PER_CHAPTER).map { idx ->
                ChapterPageRecord(
                    id = "${chapter.id}_p$idx",
                    label = "Page ${idx.toString().padStart(2, '0')}",
                    asset = CompleteExampleSampleAssets.ensurePageThumbnailAsset(
                        context = context,
                        chapterId = chapter.id,
                        pageIndex = idx
                    )
                )
            }
        }
        return chapter.copy(coverAsset = cover, pages = pages)
    }

    suspend fun refreshCatalog(environment: String, statusNote: String? = null) {
        val loadedSeries = enrichSeries(CompleteExampleCatalogStore.loadSeriesCatalog(context.storage))
        val execResult = context.executeCapability(
            capabilityName = "queryChapterCatalog",
            parameters = mapOf("environment" to JsonPrimitive(environment))
        )
        val encodedJson = (execResult as? ExecutionResult.Success)
            ?.response
            ?.metadata
            ?.get("chaptersJson")
        val rawChapters = if (!encodedJson.isNullOrBlank()) {
            runCatching { publisherJson.decodeFromString<List<ChapterRecord>>(encodedJson) }.getOrNull()
        } else {
            null
        } ?: run {
            val local = CompleteExampleCatalogStore.loadLocalCatalog(context.storage)
            val remote = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
            CompleteExampleCatalogStore.reconcile(local, remote)
        }
        val chapters = rawChapters.map { enrichChapter(it) }

        stateFlow.update { current ->
            val validSeriesIds = loadedSeries.map { it.id }.toSet()
            val requestedChapterId = context.initialParameters["chapterId"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { current.isLoading && it in chapters.map { c -> c.id } }
            val requestedSeriesId = context.initialParameters["seriesId"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { current.isLoading && it in validSeriesIds }
            val requestedNavRaw = context.initialParameters["navLevel"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { current.isLoading }

            val targetSeriesId = requestedSeriesId
                ?: requestedChapterId?.let { chId -> chapters.find { it.id == chId }?.seriesId }
                ?: current.selectedSeriesId.takeIf { it in validSeriesIds }
                ?: loadedSeries.firstOrNull()?.id
                ?: CompleteExampleCatalogStore.SERIES_OBSIDIAN_ID

            val seriesChapters = chapters.filter { it.seriesId == targetSeriesId }
            val validChapterIds = chapters.map { it.id }.toSet()
            val nextInspected = requestedChapterId
                ?: current.inspectedChapterId?.takeIf { chId -> seriesChapters.any { it.id == chId } }
                ?: seriesChapters.firstOrNull()?.id
                ?: chapters.firstOrNull()?.id

            val nextNav = when (requestedNavRaw) {
                "IMAGES" -> if (nextInspected != null) PublisherNavLevel.IMAGES else PublisherNavLevel.CHAPTERS
                "CHAPTERS" -> PublisherNavLevel.CHAPTERS
                "SERIES" -> PublisherNavLevel.SERIES
                else -> if (requestedChapterId != null) PublisherNavLevel.CHAPTERS else current.navLevel
            }

            val inspectedChapter = chapters.find { it.id == nextInspected }
            val changedInspector = nextInspected != current.inspectedChapterId || current.isLoading
            val validPageIds = inspectedChapter?.pages?.map { it.id }?.toSet().orEmpty()
            val nextSelectedPage = current.selectedPageId?.takeIf { it in validPageIds }
                ?: inspectedChapter?.pages?.firstOrNull()?.id
            current.copy(
                seriesList = loadedSeries,
                selectedSeriesId = targetSeriesId,
                navLevel = nextNav,
                chapters = chapters,
                selectedChapterIds = current.selectedChapterIds.intersect(validChapterIds),
                inspectedChapterId = nextInspected,
                selectedPageId = nextSelectedPage,
                draftTitle = if (changedInspector) inspectedChapter?.title.orEmpty() else current.draftTitle,
                draftNotes = if (changedInspector) inspectedChapter?.releaseNotes.orEmpty() else current.draftNotes,
                draftRequiresLock = if (changedInspector) (inspectedChapter?.requiresLock ?: false) else current.draftRequiresLock,
                draftRevision = if (changedInspector) current.draftRevision + 1 else current.draftRevision,
                isLoading = false,
                bannerMessage = statusNote ?: current.bannerMessage,
                bannerSeverity = if (statusNote != null) SemanticColor.SUCCESS else current.bannerSeverity
            )
        }
    }

    suspend fun dispatchPush(chapterIds: List<String>, environment: String) {
        if (chapterIds.isEmpty()) {
            stateFlow.update {
                it.copy(
                    bannerMessage = "Select at least one chapter to push.",
                    bannerSeverity = SemanticColor.WARNING
                )
            }
            return
        }
        val jobId = context.launchCapability(
            capabilityName = "publishChapterBatch",
            parameters = mapOf(
                "chapterIdsJson" to JsonPrimitive(publisherJson.encodeToString(chapterIds)),
                "environment" to JsonPrimitive(environment),
                "simulatedStepDelayMs" to JsonPrimitive(45L)
            ),
            runInBackgroundQueue = false
        )
        stateFlow.update {
            it.copy(
                bannerMessage = "Started push job ($jobId) for ${chapterIds.size} chapter(s) to $environment.",
                bannerSeverity = SemanticColor.INFO
            )
        }
    }

    suspend fun dispatchPull(chapterIds: List<String>, environment: String) {
        if (chapterIds.isEmpty()) {
            stateFlow.update {
                it.copy(
                    bannerMessage = "Select at least one chapter to pull.",
                    bannerSeverity = SemanticColor.WARNING
                )
            }
            return
        }
        val jobId = context.launchCapability(
            capabilityName = "pullRemoteChapters",
            parameters = mapOf(
                "chapterIdsJson" to JsonPrimitive(publisherJson.encodeToString(chapterIds)),
                "environment" to JsonPrimitive(environment),
                "simulatedStepDelayMs" to JsonPrimitive(45L)
            ),
            runInBackgroundQueue = false
        )
        stateFlow.update {
            it.copy(
                bannerMessage = "Started pull job ($jobId) for ${chapterIds.size} chapter(s) from $environment.",
                bannerSeverity = SemanticColor.INFO
            )
        }
    }

    suspend fun openTargetInBoard(chapter: ChapterRecord, page: ChapterPageRecord?) {
        val activePage = page ?: chapter.pages.firstOrNull()
        val handoffAsset = activePage?.let {
            CompleteExampleSampleAssets.prepareBoardHandoffAsset(
                context = context,
                chapterId = chapter.id,
                pageId = it.id,
                asset = it.asset
            )
        }
        val params = buildMap<String, JsonElement> {
            put("seriesId", JsonPrimitive(chapter.seriesId))
            put("chapterId", JsonPrimitive(chapter.id))
            put("chapterTitle", JsonPrimitive("Ch. ${chapter.number}: ${chapter.title}"))
            put("releaseNotes", JsonPrimitive(chapter.releaseNotes))
            if (activePage != null) {
                put("pageId", JsonPrimitive(activePage.id))
                put("pageLabel", JsonPrimitive(activePage.label))
            }
            if (handoffAsset != null) {
                put(
                    "backgroundAssetJson",
                    JsonPrimitive(publisherJson.encodeToString(AssetReference.serializer(), handoffAsset))
                )
            }
        }
        context.openInteractiveTool(
            capabilityName = "interactiveBoardCompositor",
            initialParameters = params
        )
    }

    suspend fun appendPagesFromPaths(paths: List<String>, sourceLabel: String) {
        if (paths.isEmpty()) return
        val current = stateFlow.value
        val targetId = current.inspectedChapterId ?: return
        val target = current.chapters.find { it.id == targetId } ?: return
        val startIdx = target.pages.size + 1
        val newPages = paths.mapIndexed { offset, path ->
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            ChapterPageRecord(
                id = "${target.id}_${sourceLabel}_${startIdx + offset}",
                label = fileName.ifBlank { "Page ${startIdx + offset}" },
                asset = AssetReference.Uri(path)
            )
        }
        val updatedChapter = target.copy(
            pages = target.pages + newPages,
            pageCount = target.pages.size + newPages.size
        )
        val updatedChapters = current.chapters.map { if (it.id == target.id) updatedChapter else it }
        CompleteExampleCatalogStore.saveLocalCatalog(
            context.storage,
            updatedChapters.filter { it.status != ChapterSyncStatus.REMOTE_ONLY }
        )
        stateFlow.update {
            it.copy(
                chapters = updatedChapters,
                selectedPageId = newPages.first().id,
                pageOrderRevision = it.pageOrderRevision + 1,
                bannerMessage = "Added ${newPages.size} $sourceLabel page image(s) to Chapter ${target.number}.",
                bannerSeverity = SemanticColor.SUCCESS
            )
        }
    }

    suspend fun applyPageAction(action: String) {
        val current = stateFlow.value
        val targetId = current.inspectedChapterId ?: return
        val target = current.chapters.find { it.id == targetId } ?: return
        if (target.pages.isEmpty()) return
        val nextPages = when (action) {
            "reverse_page_order" -> target.pages.reversed()
            "move_page_prev" -> movePageBy(target.pages, current.selectedPageId, -1)
            "move_page_next" -> movePageBy(target.pages, current.selectedPageId, +1)
            "remove_selected_page" -> target.pages.filterNot { it.id == current.selectedPageId }
            else -> return
        }
        val updatedChapter = target.copy(pages = nextPages, pageCount = nextPages.size)
        val updatedChapters = current.chapters.map { if (it.id == target.id) updatedChapter else it }
        CompleteExampleCatalogStore.saveLocalCatalog(
            context.storage,
            updatedChapters.filter { it.status != ChapterSyncStatus.REMOTE_ONLY }
        )
        val nextSel = current.selectedPageId?.takeIf { id -> nextPages.any { it.id == id } }
            ?: nextPages.firstOrNull()?.id
        stateFlow.update {
            it.copy(
                chapters = updatedChapters,
                selectedPageId = nextSel,
                pageOrderRevision = it.pageOrderRevision + 1,
                bannerMessage = "Updated page order for Ch. ${target.number}."
            )
        }
    }

    suspend fun saveDraftChanges() {
        val current = stateFlow.value
        val targetId = current.inspectedChapterId ?: return
        val existing = CompleteExampleCatalogStore.loadLocalCatalog(context.storage).toMutableList()
        val remote = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
        val idx = existing.indexOfFirst { it.id == targetId }
        val edited = current.chapters.find { it.id == targetId }?.copy(
            title = current.draftTitle.ifBlank { "Untitled Chapter" },
            releaseNotes = current.draftNotes,
            requiresLock = current.draftRequiresLock,
            localRevision = (current.chapters.find { it.id == targetId }?.localRevision ?: 0) + 1,
            status = ChapterSyncStatus.OUT_OF_SYNC
        ) ?: return
        if (idx >= 0) existing[idx] = edited else existing.add(edited)
        CompleteExampleCatalogStore.saveLocalCatalog(context.storage, existing)
        val reconciled = CompleteExampleCatalogStore.reconcile(existing, remote).map { enrichChapter(it) }
        stateFlow.update {
            it.copy(
                chapters = reconciled,
                bannerMessage = "Saved local draft revision ${edited.localRevision} for Chapter ${edited.number}.",
                bannerSeverity = SemanticColor.SUCCESS
            )
        }
    }

    suspend fun linkRepoDirectory(folder: String) {
        if (folder.isBlank()) return
        context.storage.put(STORAGE_LINKED_REPO_PATH, JsonPrimitive(folder))
        stateFlow.update {
            it.copy(
                linkedRepoPath = folder,
                bannerMessage = "Linked local repository folder: $folder",
                bannerSeverity = SemanticColor.SUCCESS
            )
        }
    }

    fun handleEscapeAction() {
        stateFlow.update { current ->
            when {
                current.showConfirmDialog -> current.copy(showConfirmDialog = false, pendingPushIds = emptyList())
                current.navLevel == PublisherNavLevel.IMAGES -> current.copy(navLevel = PublisherNavLevel.CHAPTERS)
                current.navLevel == PublisherNavLevel.CHAPTERS -> current.copy(navLevel = PublisherNavLevel.SERIES)
                else -> current.copy(selectedChapterIds = emptySet())
            }
        }
    }

    launch {
        val savedEnv = context.storage.get(CompleteExampleCatalogStore.STORAGE_ENVIRONMENT)
            ?.jsonPrimitive?.contentOrNull
            ?.takeIf { it == "STAGING" || it == "PRODUCTION" }
            ?: "STAGING"
        val savedRepo = context.storage.get(STORAGE_LINKED_REPO_PATH)?.jsonPrimitive?.contentOrNull.orEmpty()
        val hasSecret = !runCatching { context.secrets.getSecret(SECRET_PUBLISHER_API_KEY) }.getOrNull().isNullOrBlank()
        stateFlow.update {
            it.copy(
                environment = savedEnv,
                linkedRepoPath = savedRepo,
                secretConfigured = hasSecret
            )
        }
        refreshCatalog(savedEnv, "Loaded ${savedEnv.lowercase()} chapter catalog.")
    }

    launch {
        context.capabilityJobs.collect { jobs ->
            val newlyFinished = jobs.filter {
                it.isTerminal && it.jobId !in handledCompletedJobIds
            }
            if (newlyFinished.isNotEmpty()) {
                newlyFinished.forEach { handledCompletedJobIds.add(it.jobId) }
                val syncJobs = newlyFinished.filter {
                    it.capabilityName == "publishChapterBatch" || it.capabilityName == "pullRemoteChapters"
                }
                if (syncJobs.isNotEmpty()) {
                    val latest = syncJobs.last()
                    val note = when (latest.status) {
                        CapabilityJobStatus.COMPLETED -> "${latest.capabilityName} completed (${latest.jobId}). Catalog synced."
                        CapabilityJobStatus.FAILED -> "${latest.capabilityName} failed: ${latest.errorMessage ?: "Unknown error"}"
                        CapabilityJobStatus.CANCELLED -> "${latest.capabilityName} was cancelled."
                        else -> null
                    }
                    refreshCatalog(stateFlow.value.environment, note)
                    context.showNotification(
                        title = if (latest.status == CapabilityJobStatus.COMPLETED) "Publisher Sync Complete" else "Publisher Sync Notice",
                        message = note ?: "Sync finished for ${latest.capabilityName}.",
                        type = when (latest.status) {
                            CapabilityJobStatus.COMPLETED -> PluginNotificationType.SUCCESS
                            CapabilityJobStatus.FAILED -> PluginNotificationType.ERROR
                            else -> PluginNotificationType.WARNING
                        }
                    )
                }
            }
        }
    }

    launch {
        events.collect { event ->
            when (event) {
                is UiEvent.TabSelected -> {
                    if (event.nodeId == "pub_tabs") {
                        stateFlow.update { it.copy(activeTab = event.tabKey) }
                    }
                }
                is UiEvent.ChipSelected -> {
                    if (event.nodeId == "pub_filter_chips" && event.isSelected) {
                        stateFlow.update { it.copy(statusFilter = event.chipKey) }
                    }
                }
                is UiEvent.TextChanged -> {
                    when (event.nodeId) {
                        "pub_series_search_input" -> stateFlow.update { it.copy(seriesSearchQuery = event.text) }
                        "pub_search_input" -> stateFlow.update { it.copy(searchQuery = event.text) }
                        "insp_title_input" -> stateFlow.update { it.copy(draftTitle = event.text) }
                        "insp_notes_input" -> stateFlow.update { it.copy(draftNotes = event.text) }
                        "cfg_secret_token_input" -> stateFlow.update { it.copy(draftSecretToken = event.text) }
                        "cfg_api_key_input" -> {
                            context.updateSetting("apiKey", event.text)
                            stateFlow.update {
                                it.copy(
                                    bannerMessage = "Updated plugin apiKey setting.",
                                    bannerSeverity = SemanticColor.INFO
                                )
                            }
                        }
                    }
                }
                is UiEvent.ValueChanged -> {
                    if (event.nodeId == "pub_chapters_split" ||
                        event.nodeId == "pub_series_split" ||
                        event.nodeId == "pub_images_split"
                    ) {
                        val clamped = event.value.toFloat().coerceIn(0.25f, 0.75f)
                        stateFlow.update { it.copy(splitFraction = clamped) }
                    }
                }
                is UiEvent.ItemsReordered -> {
                    if (event.nodeId == "insp_pages_grid") {
                        val current = stateFlow.value
                        val targetId = current.inspectedChapterId ?: return@collect
                        val target = current.chapters.find { it.id == targetId } ?: return@collect
                        val pageByNodeId = target.pages.associateBy { "insp_page_card_${it.id}" }
                        val reorderedPages = event.itemIds.mapNotNull { pageByNodeId[it] }
                        if (reorderedPages.size == target.pages.size) {
                            val updatedChapter = target.copy(pages = reorderedPages)
                            val updatedChapters = current.chapters.map { if (it.id == targetId) updatedChapter else it }
                            CompleteExampleCatalogStore.saveLocalCatalog(
                                context.storage,
                                updatedChapters.filter { it.status != ChapterSyncStatus.REMOTE_ONLY }
                            )
                            stateFlow.update {
                                it.copy(
                                    chapters = updatedChapters,
                                    pageOrderRevision = it.pageOrderRevision + 1,
                                    bannerMessage = "Moved page #${event.fromIndex + 1} to #${event.toIndex + 1} in Ch. ${target.number}.",
                                    bannerSeverity = SemanticColor.INFO
                                )
                            }
                        }
                    }
                }
                is UiEvent.OptionSelected -> {
                    when (event.nodeId) {
                        "cfg_env_dropdown" -> {
                            val nextEnv = event.selectedKey
                            context.storage.put(CompleteExampleCatalogStore.STORAGE_ENVIRONMENT, JsonPrimitive(nextEnv))
                            stateFlow.update { it.copy(environment = nextEnv) }
                            refreshCatalog(nextEnv, "Switched target environment to $nextEnv.")
                        }
                        "series_sort_dropdown" -> {
                            stateFlow.update { it.copy(seriesSortMode = event.selectedKey) }
                        }
                    }
                }
                is UiEvent.ToggleChanged -> {
                    when {
                        event.nodeId == "pub_select_all_chk" -> {
                            stateFlow.update { current ->
                                val visibleIds = filteredChapters(current).map { it.id }.toSet()
                                val nextSelection = if (event.checked) {
                                    current.selectedChapterIds + visibleIds
                                } else {
                                    current.selectedChapterIds - visibleIds
                                }
                                current.copy(selectedChapterIds = nextSelection)
                            }
                        }
                        event.nodeId.startsWith("pub_chk_") -> {
                            val chId = event.nodeId.removePrefix("pub_chk_")
                            stateFlow.update { current ->
                                val nextSelection = if (event.checked) {
                                    current.selectedChapterIds + chId
                                } else {
                                    current.selectedChapterIds - chId
                                }
                                current.copy(selectedChapterIds = nextSelection)
                            }
                        }
                        event.nodeId == "insp_lock_toggle" -> {
                            stateFlow.update { it.copy(draftRequiresLock = event.checked) }
                        }
                    }
                }
                is UiEvent.DialogAction -> {
                    if (event.nodeId == "pub_confirm_dialog") {
                        val pendingIds = stateFlow.value.pendingPushIds
                        val env = stateFlow.value.environment
                        stateFlow.update { it.copy(showConfirmDialog = false, pendingPushIds = emptyList()) }
                        if (event.isConfirmed) {
                            dispatchPush(pendingIds, env)
                        } else {
                            stateFlow.update {
                                it.copy(
                                    bannerMessage = "Production push cancelled.",
                                    bannerSeverity = SemanticColor.WARNING
                                )
                            }
                        }
                    }
                }
                is UiEvent.Click -> {
                    val action = event.action ?: event.nodeId
                    when {
                        action == "nav_series" -> {
                            stateFlow.update { it.copy(navLevel = PublisherNavLevel.SERIES) }
                        }
                        action == "nav_chapters" || event.nodeId == "img_empty_chapter" -> {
                            stateFlow.update { it.copy(navLevel = PublisherNavLevel.CHAPTERS) }
                        }
                        action == "nav_back" -> {
                            stateFlow.update { current ->
                                when (current.navLevel) {
                                    PublisherNavLevel.IMAGES -> current.copy(navLevel = PublisherNavLevel.CHAPTERS)
                                    PublisherNavLevel.CHAPTERS -> current.copy(navLevel = PublisherNavLevel.SERIES)
                                    PublisherNavLevel.SERIES -> current
                                }
                            }
                        }
                        action.startsWith("select_series:") -> {
                            val sId = action.removePrefix("select_series:")
                            selectSeriesInState(stateFlow, sId, openChapters = false)
                        }
                        action.startsWith("open_series:") -> {
                            val sId = action.removePrefix("open_series:")
                            selectSeriesInState(stateFlow, sId, openChapters = true)
                        }
                        action.startsWith("move_series_up:") || action.startsWith("move_series_down:") -> {
                            val isUp = action.startsWith("move_series_up:")
                            val sId = action.substringAfter(":")
                            val sorted = stateFlow.value.seriesList.sortedBy { it.sortOrder }.toMutableList()
                            val idx = sorted.indexOfFirst { it.id == sId }
                            val swapIdx = if (isUp) idx - 1 else idx + 1
                            if (idx >= 0 && swapIdx in sorted.indices) {
                                val moved = sorted.removeAt(idx)
                                sorted.add(swapIdx, moved)
                                val renumbered = sorted.mapIndexed { order, rec -> rec.copy(sortOrder = order) }
                                CompleteExampleCatalogStore.saveSeriesCatalog(context.storage, renumbered)
                                stateFlow.update {
                                    it.copy(
                                        seriesList = renumbered,
                                        seriesSortMode = "CUSTOM",
                                        bannerMessage = "Updated custom series order."
                                    )
                                }
                            }
                        }
                        action == "create_new_series" -> {
                            val current = stateFlow.value
                            val nextIndex = current.seriesList.size + 1
                            val newId = "series_custom_$nextIndex"
                            val title = "New Manga Series #$nextIndex"
                            val cover = CompleteExampleSampleAssets.ensureSeriesCoverAsset(
                                context = context,
                                seriesId = newId,
                                title = title,
                                accentIndex = nextIndex
                            )
                            val newSeries = SeriesRecord(
                                id = newId,
                                title = title,
                                author = "Studio Draft",
                                description = "Newly staged series ready for chapters & page uploads.",
                                sortOrder = current.seriesList.size,
                                coverAsset = cover
                            )
                            val updatedSeries = current.seriesList + newSeries
                            CompleteExampleCatalogStore.saveSeriesCatalog(context.storage, updatedSeries)
                            val debutCh = enrichChapter(
                                ChapterRecord(
                                    id = "${newId}_ch001",
                                    number = 1,
                                    title = "Chapter 1 Debut",
                                    pageCount = DEFAULT_PAGES_PER_CHAPTER,
                                    sizeMb = 6.2,
                                    localRevision = 1,
                                    remoteRevision = 0,
                                    status = ChapterSyncStatus.LOCAL_DRAFT,
                                    releaseNotes = "Created alongside new series.",
                                    seriesId = newId
                                )
                            )
                            val localCatalog = CompleteExampleCatalogStore.loadLocalCatalog(context.storage) + debutCh
                            CompleteExampleCatalogStore.saveLocalCatalog(context.storage, localCatalog)
                            val remoteCatalog = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
                            val reconciled = CompleteExampleCatalogStore.reconcile(localCatalog, remoteCatalog).map { enrichChapter(it) }
                            stateFlow.update {
                                it.copy(
                                    seriesList = updatedSeries,
                                    selectedSeriesId = newId,
                                    chapters = reconciled,
                                    inspectedChapterId = debutCh.id,
                                    selectedPageId = debutCh.pages.firstOrNull()?.id,
                                    draftTitle = debutCh.title,
                                    draftNotes = debutCh.releaseNotes,
                                    draftRequiresLock = debutCh.requiresLock,
                                    draftRevision = it.draftRevision + 1,
                                    navLevel = PublisherNavLevel.CHAPTERS,
                                    bannerMessage = "Created '$title' with debut Chapter 1.",
                                    bannerSeverity = SemanticColor.SUCCESS
                                )
                            }
                        }
                        action.startsWith("open_images:") -> {
                            val chId = action.removePrefix("open_images:")
                            stateFlow.update { current ->
                                val target = current.chapters.find { it.id == chId }
                                if (target != null) {
                                    current.copy(
                                        selectedSeriesId = target.seriesId,
                                        inspectedChapterId = chId,
                                        selectedPageId = target.pages.firstOrNull()?.id,
                                        draftTitle = target.title,
                                        draftNotes = target.releaseNotes,
                                        draftRequiresLock = target.requiresLock,
                                        draftRevision = current.draftRevision + 1,
                                        navLevel = PublisherNavLevel.IMAGES
                                    )
                                } else current
                            }
                        }
                        action == "reverse_page_order" || action == "move_page_prev" || action == "move_page_next" || action == "remove_selected_page" -> {
                            applyPageAction(action)
                        }
                        action == "refresh_catalog" || event.nodeId == "pub_refresh_btn" -> {
                            refreshCatalog(stateFlow.value.environment, "Re-queried remote & local chapter catalogs.")
                        }
                        action == "dismiss_banner" || event.nodeId == "pub_banner" -> {
                            stateFlow.update { it.copy(bannerMessage = null) }
                        }
                        action == "reset_filters" || event.nodeId == "pub_empty_state" -> {
                            stateFlow.update { it.copy(searchQuery = "", statusFilter = "ALL") }
                        }
                        action == "push_selected" || event.nodeId == "pub_push_selected_btn" -> {
                            val state = stateFlow.value
                            val ids = state.selectedChapterIds.toList()
                            if (state.environment == "PRODUCTION" && ids.isNotEmpty()) {
                                stateFlow.update { it.copy(showConfirmDialog = true, pendingPushIds = ids) }
                            } else {
                                dispatchPush(ids, state.environment)
                            }
                        }
                        action == "pull_selected" || event.nodeId == "pub_pull_selected_btn" -> {
                            val state = stateFlow.value
                            dispatchPull(state.selectedChapterIds.toList(), state.environment)
                        }
                        action.startsWith("inspect:") -> {
                            val chId = action.removePrefix("inspect:")
                            stateFlow.update { current ->
                                val target = current.chapters.find { it.id == chId }
                                if (target != null) {
                                    current.copy(
                                        inspectedChapterId = chId,
                                        selectedPageId = target.pages.firstOrNull()?.id,
                                        draftTitle = target.title,
                                        draftNotes = target.releaseNotes,
                                        draftRequiresLock = target.requiresLock,
                                        draftRevision = current.draftRevision + 1
                                    )
                                } else current
                            }
                        }
                        action.startsWith("select_page:") -> {
                            val pageId = action.removePrefix("select_page:")
                            stateFlow.update { it.copy(selectedPageId = pageId) }
                        }
                        action == "pick_page_files" -> {
                            val picked = context.pickFiles(
                                title = "Select Chapter Page Images",
                                allowedExtensions = listOf("png", "jpg", "jpeg", "webp")
                            )
                            appendPagesFromPaths(picked, "picked")
                        }
                        action == "pick_repo_folder" -> {
                            val folder = context.pickDirectory("Select Local Repository Directory")
                            if (!folder.isNullOrBlank()) {
                                linkRepoDirectory(folder)
                            }
                        }
                        action == "export_chapter_json" -> {
                            val target = stateFlow.value.chapters.find { it.id == stateFlow.value.inspectedChapterId }
                            if (target != null) {
                                val payload = publisherJson.encodeToString(target).encodeToByteArray()
                                val savedPath = context.saveFile("${target.id}_manifest.json", "json", payload)
                                if (!savedPath.isNullOrBlank()) {
                                    stateFlow.update {
                                        it.copy(
                                            bannerMessage = "Exported chapter manifest to $savedPath",
                                            bannerSeverity = SemanticColor.SUCCESS
                                        )
                                    }
                                }
                            }
                        }
                        action == "open_page_in_board" -> {
                            val current = stateFlow.value
                            val target = current.chapters.find { it.id == current.inspectedChapterId }
                            if (target != null) {
                                val activePage = target.pages.find { it.id == current.selectedPageId } ?: target.pages.firstOrNull()
                                openTargetInBoard(target, activePage)
                            }
                        }
                        action.startsWith("open_in_board:") -> {
                            val chId = action.removePrefix("open_in_board:")
                            val current = stateFlow.value
                            val target = current.chapters.find { it.id == chId }
                            if (target != null) {
                                val activePage = target.pages.find { it.id == current.selectedPageId } ?: target.pages.firstOrNull()
                                openTargetInBoard(target, activePage)
                            } else {
                                context.openInteractiveTool(
                                    capabilityName = "interactiveBoardCompositor",
                                    initialParameters = mapOf(
                                        "chapterId" to JsonPrimitive(chId),
                                        "chapterTitle" to JsonPrimitive(chId)
                                    )
                                )
                            }
                        }
                        action == "save_secret_token" -> {
                            val secret = stateFlow.value.draftSecretToken
                            if (secret.isNotBlank()) {
                                context.secrets.putSecret(SECRET_PUBLISHER_API_KEY, secret)
                                context.showNotification(
                                    title = "Credential Vault",
                                    message = "Encrypted publisher token stored securely.",
                                    type = PluginNotificationType.INFO
                                )
                                stateFlow.update {
                                    it.copy(
                                        draftSecretToken = "",
                                        draftSecretRevision = it.draftSecretRevision + 1,
                                        secretConfigured = true,
                                        bannerMessage = "Saved encrypted publisher API token.",
                                        bannerSeverity = SemanticColor.SUCCESS
                                    )
                                }
                            }
                        }
                        action == "ping_webhook" -> {
                            val response = runCatching {
                                context.networkClient.request(
                                    HttpRequest(
                                        url = "https://httpbin.org/status/200",
                                        method = HttpMethod.GET,
                                        timeoutMs = 3000L
                                    )
                                )
                            }
                            val statusSummary = response.fold(
                                onSuccess = { "HTTP ${it.statusCode}" },
                                onFailure = { "Offline / blocked: ${it.message ?: "network error"}" }
                            )
                            stateFlow.update {
                                it.copy(
                                    webhookStatus = statusSummary,
                                    bannerMessage = "Sync endpoint probe: $statusSummary",
                                    bannerSeverity = if (response.isSuccess) SemanticColor.INFO else SemanticColor.WARNING
                                )
                            }
                        }
                        action.startsWith("push_one:") -> {
                            val chId = action.removePrefix("push_one:")
                            val state = stateFlow.value
                            if (state.environment == "PRODUCTION") {
                                stateFlow.update { it.copy(showConfirmDialog = true, pendingPushIds = listOf(chId)) }
                            } else {
                                dispatchPush(listOf(chId), state.environment)
                            }
                        }
                        action.startsWith("pull_one:") -> {
                            val chId = action.removePrefix("pull_one:")
                            dispatchPull(listOf(chId), stateFlow.value.environment)
                        }
                        action == "save_draft_changes" -> {
                            saveDraftChanges()
                        }
                        action == "reset_api_key" -> {
                            context.clearSetting("apiKey")
                            stateFlow.update {
                                it.copy(
                                    bannerMessage = "Reset plugin apiKey setting to manifest default.",
                                    bannerSeverity = SemanticColor.INFO
                                )
                            }
                        }
                        action == "create_new_draft" -> {
                            val activeSeriesId = stateFlow.value.selectedSeriesId
                            val existing = CompleteExampleCatalogStore.loadLocalCatalog(context.storage).toMutableList()
                            val remote = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
                            val seriesChapters = (existing + remote).filter { it.seriesId == activeSeriesId }
                            val nextNum = (seriesChapters.maxOfOrNull { it.number } ?: 0) + 1
                            val globalNextSuffix = existing.size + remote.size + 1
                            val newId = "ch_${nextNum.toString().padStart(3, '0')}_$globalNextSuffix"
                            val newChapter = ChapterRecord(
                                id = newId,
                                number = nextNum,
                                title = "New Episode Draft #$nextNum",
                                pageCount = DEFAULT_PAGES_PER_CHAPTER,
                                sizeMb = 9.8,
                                localRevision = 1,
                                remoteRevision = 0,
                                status = ChapterSyncStatus.LOCAL_DRAFT,
                                releaseNotes = "Prepared locally in interactive publisher.",
                                seriesId = activeSeriesId
                            )
                            existing.add(newChapter)
                            CompleteExampleCatalogStore.saveLocalCatalog(context.storage, existing)
                            val reconciled = CompleteExampleCatalogStore.reconcile(existing, remote).map { enrichChapter(it) }
                            val created = reconciled.find { it.id == newId } ?: enrichChapter(newChapter)
                            stateFlow.update {
                                it.copy(
                                    activeTab = "chapters",
                                    navLevel = PublisherNavLevel.CHAPTERS,
                                    chapters = reconciled,
                                    inspectedChapterId = newId,
                                    selectedPageId = created.pages.firstOrNull()?.id,
                                    draftTitle = created.title,
                                    draftNotes = created.releaseNotes,
                                    draftRequiresLock = created.requiresLock,
                                    draftRevision = it.draftRevision + 1,
                                    bannerMessage = "Created local draft Chapter $nextNum.",
                                    bannerSeverity = SemanticColor.SUCCESS
                                )
                            }
                        }
                        action == "toggle_feature_lock" -> {
                            val currentlyUnlocked = context.locksFlow.value["feature_unlocked"] == true
                            val result = context.launchAction(
                                actionName = "toggleFeatureLock",
                                parameters = mapOf("unlocked" to JsonPrimitive(!currentlyUnlocked))
                            )
                            stateFlow.update {
                                val nextLock = !currentlyUnlocked
                                val statusText = if (result.isSuccess) {
                                    "Feature lock set to ${if (nextLock) "UNLOCKED" else "LOCKED"}."
                                } else {
                                    "Action notice: ${result.exceptionOrNull()?.message ?: "Unable to toggle lock"}"
                                }
                                it.copy(bannerMessage = statusText, bannerSeverity = SemanticColor.INFO)
                            }
                        }
                        action.startsWith("pause_job:") -> {
                            val jobId = action.removePrefix("pause_job:")
                            context.pauseCapabilityJob(jobId)
                        }
                        action.startsWith("resume_job:") -> {
                            val jobId = action.removePrefix("resume_job:")
                            context.resumeCapabilityJob(jobId)
                        }
                        action.startsWith("cancel_job:") -> {
                            val jobId = action.removePrefix("cancel_job:")
                            context.cancelCapabilityJob(jobId)
                        }
                    }
                }
                is UiEvent.ShortcutTriggered -> {
                    when (event.action) {
                        "save_draft_changes" -> saveDraftChanges()
                        "shortcut_escape" -> handleEscapeAction()
                        "move_page_prev", "move_page_next", "remove_selected_page" -> applyPageAction(event.action)
                    }
                }
                is UiEvent.FilesDropped -> {
                    when (event.action) {
                        "drop_page_files" -> appendPagesFromPaths(event.paths, "dropped")
                        "drop_repo_folder" -> event.paths.firstOrNull()?.let { linkRepoDirectory(it) }
                    }
                }
                else -> Unit
            }
        }
    }

    combine(
        stateFlow,
        context.capabilityJobs,
        context.settingsFlow,
        context.locksFlow
    ) { state, jobs, settings, locks ->
        renderChapterPublisherTree(
            state = state,
            jobs = jobs,
            settings = settings,
            locks = locks
        )
    }.collect { tree ->
        send(tree)
    }
}

private fun selectSeriesInState(
    stateFlow: MutableStateFlow<ChapterPublisherState>,
    seriesId: String,
    openChapters: Boolean
) {
    stateFlow.update { current ->
        val seriesChapters = current.chapters.filter { it.seriesId == seriesId }
        val firstChapter = seriesChapters.firstOrNull()
        current.copy(
            selectedSeriesId = seriesId,
            navLevel = if (openChapters) PublisherNavLevel.CHAPTERS else current.navLevel,
            searchQuery = "",
            statusFilter = "ALL",
            inspectedChapterId = firstChapter?.id,
            selectedPageId = firstChapter?.pages?.firstOrNull()?.id,
            draftTitle = firstChapter?.title.orEmpty(),
            draftNotes = firstChapter?.releaseNotes.orEmpty(),
            draftRequiresLock = firstChapter?.requiresLock ?: false,
            draftRevision = current.draftRevision + 1
        )
    }
}

private fun movePageBy(
    pages: List<ChapterPageRecord>,
    selectedPageId: String?,
    delta: Int
): List<ChapterPageRecord> {
    if (pages.size < 2) return pages
    val index = pages.indexOfFirst { it.id == selectedPageId }
    if (index < 0) return pages
    val swapIndex = index + delta
    if (swapIndex !in pages.indices) return pages
    return pages.toMutableList().apply {
        val moved = removeAt(index)
        add(swapIndex, moved)
    }
}

private fun filteredSortedSeries(state: ChapterPublisherState): List<SeriesRecord> {
    val query = state.seriesSearchQuery.trim().lowercase()
    val filtered = state.seriesList.filter { s ->
        query.isEmpty() ||
            s.title.lowercase().contains(query) ||
            s.author.lowercase().contains(query) ||
            s.id.lowercase().contains(query)
    }
    return when (state.seriesSortMode) {
        "TITLE" -> filtered.sortedBy { it.title.lowercase() }
        "PENDING" -> filtered.sortedByDescending { s ->
            state.chapters.count { it.seriesId == s.id && it.status != ChapterSyncStatus.PUBLISHED }
        }
        else -> filtered.sortedBy { it.sortOrder }
    }
}

private fun filteredChapters(state: ChapterPublisherState): List<ChapterRecord> {
    val query = state.searchQuery.trim().lowercase()
    return state.chapters.filter { ch ->
        val matchesSeries = ch.seriesId == state.selectedSeriesId
        val matchesStatus = state.statusFilter == "ALL" || ch.status.name == state.statusFilter
        val matchesQuery = query.isEmpty() ||
            ch.title.lowercase().contains(query) ||
            "chapter ${ch.number}".contains(query) ||
            ch.id.lowercase().contains(query)
        matchesSeries && matchesStatus && matchesQuery
    }
}

private fun statusMeta(status: ChapterSyncStatus): Pair<String, SemanticColor> = when (status) {
    ChapterSyncStatus.PUBLISHED -> "Published" to SemanticColor.SUCCESS
    ChapterSyncStatus.LOCAL_DRAFT -> "Draft" to SemanticColor.INFO
    ChapterSyncStatus.OUT_OF_SYNC -> "Out of Sync" to SemanticColor.WARNING
    ChapterSyncStatus.REMOTE_ONLY -> "Remote Only" to SemanticColor.ACCENT
}

private fun jobStatusColor(status: CapabilityJobStatus): SemanticColor = when (status) {
    CapabilityJobStatus.QUEUED -> SemanticColor.SECONDARY
    CapabilityJobStatus.RUNNING -> SemanticColor.PRIMARY
    CapabilityJobStatus.PAUSE_REQUESTED -> SemanticColor.WARNING
    CapabilityJobStatus.PAUSED -> SemanticColor.WARNING
    CapabilityJobStatus.COMPLETED -> SemanticColor.SUCCESS
    CapabilityJobStatus.FAILED -> SemanticColor.ERROR
    CapabilityJobStatus.CANCELLED -> SemanticColor.ERROR
}

private fun renderChapterPublisherTree(
    state: ChapterPublisherState,
    jobs: List<CapabilityJobSnapshot>,
    settings: Map<String, JsonElement>,
    locks: Map<String, Boolean>
): UiNodeTree = uiTree {
    val activeJobsCount = jobs.count {
        it.status == CapabilityJobStatus.RUNNING ||
            it.status == CapabilityJobStatus.PAUSE_REQUESTED ||
            it.status == CapabilityJobStatus.PAUSED ||
            it.status == CapabilityJobStatus.QUEUED
    }
    val userId = settings["userId"]?.jsonPrimitive?.contentOrNull ?: "guest_editor"
    val apiKey = settings["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
    val featureUnlocked = locks["feature_unlocked"] == true

    scaffold(
        id = "publisher_scaffold",
        modifier = UiModifier.fillMaxSize()
            .shortcut(
                key = "S",
                action = "save_draft_changes",
                ctrl = true,
                allowWhileTyping = true,
                label = "Save Chapter Draft"
            )
            .shortcut(
                key = UiShortcut.KEY_ESCAPE,
                action = "shortcut_escape",
                label = "Navigate Back / Dismiss"
            )
    ) {
        topBar {
            row(
                id = "publisher_top_row",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxWidth().padding(SpacingSize.SMALL)
            ) {
                icon(name = "sync", id = "pub_title_icon", tint = SemanticColor.PRIMARY, size = ControlSize.MEDIUM)
                text(
                    id = "pub_title_text",
                    text = "Manhwa Chapter Publisher",
                    role = TypographyRole.TITLE_MEDIUM
                )
                badge(
                    id = "pub_env_badge",
                    text = state.environment,
                    color = if (state.environment == "PRODUCTION") SemanticColor.WARNING else SemanticColor.ACCENT
                )
                badge(
                    id = "pub_user_badge",
                    text = userId,
                    color = SemanticColor.SECONDARY
                )
                spacer(id = "pub_top_spacer", modifier = UiModifier.weight(1f))
                iconButton(
                    id = "pub_new_draft_btn",
                    iconName = "add",
                    action = "create_new_draft",
                    tooltip = "New Local Chapter Draft"
                )
                iconButton(
                    id = "pub_refresh_btn",
                    iconName = "refresh",
                    action = "refresh_catalog",
                    tooltip = "Refresh Remote & Local Catalogs"
                )
                button(
                    id = "pub_pull_selected_btn",
                    label = "Pull (${state.selectedChapterIds.size})",
                    action = "pull_selected",
                    isPrimary = false
                )
                button(
                    id = "pub_push_selected_btn",
                    label = "Push (${state.selectedChapterIds.size})",
                    action = "push_selected",
                    isPrimary = true,
                    isDestructive = state.environment == "PRODUCTION"
                )
            }
        }

        content {
            column(
                id = "publisher_body_col",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                val message = state.bannerMessage
                if (!message.isNullOrBlank()) {
                    alertBanner(
                        id = "pub_banner",
                        title = "Publisher Sync",
                        message = message,
                        severity = state.bannerSeverity,
                        iconName = "info",
                        actionLabel = "Dismiss"
                    )
                }

                tabs(
                    id = "pub_tabs",
                    selectedTabKey = state.activeTab,
                    tabs = listOf(
                        TabItem(
                            key = "chapters",
                            label = "Catalog",
                            iconName = "layers",
                            badgeText = state.chapters.size.toString()
                        ),
                        TabItem(
                            key = "queue",
                            label = "Sync Queue",
                            iconName = "sync",
                            badgeText = if (activeJobsCount > 0) activeJobsCount.toString() else jobs.size.takeIf { it > 0 }?.toString()
                        ),
                        TabItem(
                            key = "settings",
                            label = "Settings & Locks",
                            iconName = "settings",
                            badgeText = if (featureUnlocked) "Unlocked" else "Locked"
                        )
                    ),
                    modifier = UiModifier.fillMaxWidth().weight(1f)
                ) {
                    when (state.activeTab) {
                        "chapters" -> renderCatalogDrilldownTab(
                            state = state,
                            jobs = jobs,
                            featureUnlocked = featureUnlocked
                        )
                        "queue" -> renderQueueTab(jobs = jobs)
                        "settings" -> renderSettingsTab(
                            state = state,
                            userId = userId,
                            apiKey = apiKey,
                            featureUnlocked = featureUnlocked
                        )
                    }
                }

                if (state.showConfirmDialog) {
                    dialog(
                        id = "pub_confirm_dialog",
                        title = "Confirm Production Publish",
                        isOpen = true,
                        confirmLabel = "Publish Live",
                        dismissLabel = "Cancel",
                        isDestructive = true
                    ) {
                        text(
                            id = "pub_confirm_desc",
                            text = "You are about to push ${state.pendingPushIds.size} chapter(s) directly to PRODUCTION: ${state.pendingPushIds.joinToString(", ")}.",
                            role = TypographyRole.BODY_MEDIUM
                        )
                    }
                }
            }
        }
    }
}

private fun UiContainerBuilder.renderCatalogDrilldownTab(
    state: ChapterPublisherState,
    jobs: List<CapabilityJobSnapshot>,
    featureUnlocked: Boolean
) {
    val selectedSeries = state.seriesList.find { it.id == state.selectedSeriesId }
        ?: state.seriesList.firstOrNull()
    val seriesChapters = state.chapters.filter { it.seriesId == (selectedSeries?.id ?: state.selectedSeriesId) }
    val selectedChapter = seriesChapters.find { it.id == state.inspectedChapterId }
        ?: state.chapters.find { it.id == state.inspectedChapterId }

    column(
        id = "pub_drilldown_col",
        spacing = SpacingSize.SMALL,
        modifier = UiModifier.fillMaxSize()
    ) {
        row(
            id = "pub_nav_bar",
            spacing = SpacingSize.SMALL,
            modifier = UiModifier.fillMaxWidth().padding(SpacingSize.EXTRA_SMALL)
        ) {
            if (state.navLevel != PublisherNavLevel.SERIES) {
                iconButton(
                    id = "pub_nav_back_btn",
                    iconName = "arrow_back",
                    action = "nav_back",
                    tooltip = "Go up one navigation level"
                )
            }
            button(
                id = "crumb_series_btn",
                label = "Series (${state.seriesList.size})",
                action = "nav_series",
                isPrimary = state.navLevel == PublisherNavLevel.SERIES
            )
            if (selectedSeries != null && state.navLevel != PublisherNavLevel.SERIES) {
                icon(
                    name = "arrow_forward",
                    id = "crumb_sep_series",
                    size = ControlSize.SMALL,
                    tint = SemanticColor.SECONDARY
                )
                button(
                    id = "crumb_chapters_btn",
                    label = "${selectedSeries.title} (${seriesChapters.size})",
                    action = "nav_chapters",
                    isPrimary = state.navLevel == PublisherNavLevel.CHAPTERS
                )
            }
            if (selectedChapter != null && state.navLevel == PublisherNavLevel.IMAGES) {
                icon(
                    name = "arrow_forward",
                    id = "crumb_sep_chapters",
                    size = ControlSize.SMALL,
                    tint = SemanticColor.SECONDARY
                )
                badge(
                    id = "crumb_images_badge",
                    text = "Ch. ${selectedChapter.number} Images (${selectedChapter.pages.size})",
                    color = SemanticColor.PRIMARY
                )
            }
            spacer(id = "crumb_spacer", modifier = UiModifier.weight(1f))
            badge(
                id = "crumb_level_badge",
                text = when (state.navLevel) {
                    PublisherNavLevel.SERIES -> "1/3 Series"
                    PublisherNavLevel.CHAPTERS -> "2/3 Chapters"
                    PublisherNavLevel.IMAGES -> "3/3 Page Grid"
                },
                color = SemanticColor.SECONDARY
            )
        }

        when (state.navLevel) {
            PublisherNavLevel.SERIES -> renderSeriesLevel(
                state = state,
                selectedSeries = selectedSeries,
                seriesChapters = seriesChapters
            )
            PublisherNavLevel.CHAPTERS -> renderChaptersTab(
                state = state,
                selectedSeries = selectedSeries,
                seriesChapters = seriesChapters,
                jobs = jobs,
                featureUnlocked = featureUnlocked
            )
            PublisherNavLevel.IMAGES -> renderImagesLevel(
                state = state,
                selectedSeries = selectedSeries,
                selectedChapter = selectedChapter
            )
        }
    }
}

private fun UiContainerBuilder.renderSeriesLevel(
    state: ChapterPublisherState,
    selectedSeries: SeriesRecord?,
    seriesChapters: List<ChapterRecord>
) {
    val visibleSeries = filteredSortedSeries(state)

    splitPane(
        id = "pub_series_split",
        splitFraction = state.splitFraction,
        isHorizontal = true,
        resizable = true,
        modifier = UiModifier.fillMaxWidth().weight(1f),
        first = {
            column(
                id = "pub_series_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                row(id = "pub_series_controls_row", spacing = SpacingSize.SMALL, modifier = UiModifier.fillMaxWidth()) {
                    textInput(
                        id = "pub_series_search_input",
                        text = state.seriesSearchQuery,
                        label = "Filter Series",
                        placeholder = "Search title or author...",
                        modifier = UiModifier.weight(1f)
                    )
                    dropdown(
                        id = "series_sort_dropdown",
                        selectedKey = state.seriesSortMode,
                        options = mapOf(
                            "CUSTOM" to "Custom Order",
                            "TITLE" to "Title (A-Z)",
                            "PENDING" to "Pending First"
                        ),
                        label = "Sort"
                    )
                    button(
                        id = "series_new_btn",
                        label = "+ New Series",
                        action = "create_new_series",
                        isPrimary = false
                    )
                }

                if (visibleSeries.isEmpty()) {
                    emptyState(
                        id = "series_empty_state",
                        title = "No matching series",
                        description = "Create a new series or clear your search filter.",
                        iconName = "book",
                        modifier = UiModifier.fillMaxWidth().weight(1f)
                    )
                } else {
                    scrollableColumn(
                        id = "pub_series_list",
                        spacing = SpacingSize.EXTRA_SMALL,
                        modifier = UiModifier.fillMaxWidth().weight(1f)
                    ) {
                        visibleSeries.forEach { series ->
                            val chaptersInSeries = state.chapters.filter { it.seriesId == series.id }
                            val pendingCount = chaptersInSeries.count { it.status != ChapterSyncStatus.PUBLISHED }
                            listItem(
                                id = "series_row_${series.id}",
                                title = series.title,
                                subtitle = "${series.author} • ${chaptersInSeries.size} chapter(s) • ${chaptersInSeries.sumOf { it.pageCount }} pages",
                                leadingIcon = "book",
                                leadingAsset = series.coverAsset,
                                badgeText = if (pendingCount > 0) "$pendingCount pending" else "Synced",
                                badgeColor = if (pendingCount > 0) SemanticColor.WARNING else SemanticColor.SUCCESS,
                                selected = series.id == state.selectedSeriesId,
                                action = "select_series:${series.id}",
                                trailingBlock = {
                                    iconButton(
                                        id = "series_up_btn_${series.id}",
                                        iconName = "arrow_upward",
                                        action = "move_series_up:${series.id}",
                                        tooltip = "Move series earlier"
                                    )
                                    iconButton(
                                        id = "series_down_btn_${series.id}",
                                        iconName = "arrow_downward",
                                        action = "move_series_down:${series.id}",
                                        tooltip = "Move series later"
                                    )
                                    button(
                                        id = "series_open_btn_${series.id}",
                                        label = "Chapters",
                                        action = "open_series:${series.id}",
                                        isPrimary = series.id == state.selectedSeriesId
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        second = {
            scrollableColumn(
                id = "series_inspector_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                if (selectedSeries == null) {
                    emptyState(
                        id = "series_insp_empty",
                        title = "Select a Series",
                        description = "Choose a series on the left to inspect its catalog summary and open chapters.",
                        iconName = "book"
                    )
                } else {
                    val publishedCount = seriesChapters.count { it.status == ChapterSyncStatus.PUBLISHED }
                    val draftCount = seriesChapters.count { it.status == ChapterSyncStatus.LOCAL_DRAFT }
                    val outOfSyncCount = seriesChapters.count { it.status == ChapterSyncStatus.OUT_OF_SYNC }
                    val remoteOnlyCount = seriesChapters.count { it.status == ChapterSyncStatus.REMOTE_ONLY }

                    card(id = "series_detail_card", title = selectedSeries.title) {
                        selectedSeries.coverAsset?.let { cover ->
                            image(
                                id = "series_detail_cover",
                                asset = cover,
                                contentDescription = selectedSeries.title,
                                contentScale = ImageScale.FIT,
                                badgeText = "Author: ${selectedSeries.author}",
                                modifier = UiModifier.fillMaxWidth().height(160)
                            )
                        }
                        text(
                            id = "series_detail_desc",
                            text = selectedSeries.description,
                            role = TypographyRole.BODY_MEDIUM
                        )
                        row(id = "series_stats_row", spacing = SpacingSize.SMALL) {
                            badge(id = "series_stat_pub", text = "$publishedCount Published", color = SemanticColor.SUCCESS)
                            badge(id = "series_stat_draft", text = "$draftCount Draft", color = SemanticColor.INFO)
                            if (outOfSyncCount > 0) {
                                badge(id = "series_stat_sync", text = "$outOfSyncCount Out of Sync", color = SemanticColor.WARNING)
                            }
                            if (remoteOnlyCount > 0) {
                                badge(id = "series_stat_remote", text = "$remoteOnlyCount Remote", color = SemanticColor.ACCENT)
                            }
                        }
                        button(
                            id = "series_drill_btn",
                            label = "Open Chapters (${seriesChapters.size}) ->",
                            action = "open_series:${selectedSeries.id}",
                            isPrimary = true
                        )
                    }
                }
            }
        }
    )
}

private fun UiContainerBuilder.renderChaptersTab(
    state: ChapterPublisherState,
    selectedSeries: SeriesRecord?,
    seriesChapters: List<ChapterRecord>,
    jobs: List<CapabilityJobSnapshot>,
    featureUnlocked: Boolean
) {
    val visible = filteredChapters(state)
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.id in state.selectedChapterIds }

    splitPane(
        id = "pub_chapters_split",
        splitFraction = state.splitFraction,
        isHorizontal = true,
        resizable = true,
        modifier = UiModifier.fillMaxWidth().weight(1f),
        first = {
            column(
                id = "pub_catalog_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                textInput(
                    id = "pub_search_input",
                    text = state.searchQuery,
                    label = "Filter ${selectedSeries?.title ?: "Chapters"}",
                    placeholder = "Search title, ID, or chapter number..."
                )
                chipGroup(
                    id = "pub_filter_chips",
                    selectedKeys = setOf(state.statusFilter),
                    multiSelect = false,
                    chips = listOf(
                        ChipItem("ALL", "All (${seriesChapters.size})"),
                        ChipItem("OUT_OF_SYNC", "Out of Sync"),
                        ChipItem("LOCAL_DRAFT", "Drafts"),
                        ChipItem("REMOTE_ONLY", "Remote Only"),
                        ChipItem("PUBLISHED", "Published")
                    )
                )
                row(id = "pub_select_all_row", spacing = SpacingSize.SMALL) {
                    checkbox(
                        id = "pub_select_all_chk",
                        checked = allVisibleSelected,
                        label = "Select all visible (${visible.size})"
                    )
                }

                if (visible.isEmpty()) {
                    emptyState(
                        id = "pub_empty_state",
                        title = "No matching chapters",
                        description = "Clear the search filter or create a new local chapter draft.",
                        iconName = "search",
                        actionLabel = "Reset Filters",
                        modifier = UiModifier.fillMaxWidth().weight(1f)
                    )
                } else {
                    scrollableColumn(
                        id = "pub_catalog_list",
                        spacing = SpacingSize.EXTRA_SMALL,
                        modifier = UiModifier.fillMaxWidth().weight(1f)
                    ) {
                        visible.forEach { ch ->
                            val (statusLabel, statusColor) = statusMeta(ch.status)
                            val subtitle = "v${ch.localRevision} local / v${ch.remoteRevision} remote • ${ch.pageCount}p (${ch.sizeMb} MB)"
                            listItem(
                                id = "pub_row_${ch.id}",
                                title = "Ch. ${ch.number}: ${ch.title}",
                                subtitle = subtitle,
                                leadingIcon = if (ch.requiresLock) "lock" else "layers",
                                leadingAsset = ch.coverAsset,
                                badgeText = statusLabel,
                                badgeColor = statusColor,
                                selected = ch.id == state.inspectedChapterId,
                                action = "inspect:${ch.id}",
                                trailingBlock = {
                                    checkbox(
                                        id = "pub_chk_${ch.id}",
                                        checked = ch.id in state.selectedChapterIds
                                    )
                                    iconButton(
                                        id = "pub_images_btn_${ch.id}",
                                        iconName = "grid_view",
                                        action = "open_images:${ch.id}",
                                        tooltip = "Open page grid & preview"
                                    )
                                    iconButton(
                                        id = "pub_inspect_btn_${ch.id}",
                                        iconName = "edit",
                                        action = "inspect:${ch.id}",
                                        tooltip = "Inspect & edit chapter"
                                    )
                                    iconButton(
                                        id = "pub_pull_btn_${ch.id}",
                                        iconName = "download",
                                        action = "pull_one:${ch.id}",
                                        tooltip = "Pull remote revision"
                                    )
                                    iconButton(
                                        id = "pub_push_btn_${ch.id}",
                                        iconName = "upload",
                                        action = "push_one:${ch.id}",
                                        tint = SemanticColor.PRIMARY,
                                        tooltip = "Push chapter"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        second = {
            scrollableColumn(
                id = "insp_scroll_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                val selected = state.chapters.find { it.id == state.inspectedChapterId }
                if (selected == null) {
                    emptyState(
                        id = "insp_empty_state",
                        title = "Select a chapter",
                        description = "Choose a chapter from the catalog list to inspect diffs, edit metadata, or manage pages.",
                        iconName = "info"
                    )
                } else {
                    val (statusLabel, statusColor) = statusMeta(selected.status)
                    card(id = "insp_header_card", title = "Chapter ${selected.number} Details") {
                        row(id = "insp_meta_row", spacing = SpacingSize.SMALL) {
                            badge(id = "insp_status_badge", text = statusLabel, color = statusColor)
                            text(
                                id = "insp_rev_text",
                                text = "Local rev ${selected.localRevision} | Remote rev ${selected.remoteRevision} | ${selected.pageCount} pages",
                                role = TypographyRole.BODY_SMALL
                            )
                        }
                        if (selected.requiresLock && !featureUnlocked) {
                            alertBanner(
                                id = "insp_lock_warn",
                                title = "Restricted Feature",
                                message = "This chapter requires feature_unlocked=true. Unlock it in Settings & Locks before publishing.",
                                severity = SemanticColor.WARNING,
                                iconName = "lock"
                            )
                        }
                        textInput(
                            id = "insp_title_input",
                            text = state.draftTitle,
                            label = "Chapter Title",
                            revision = state.draftRevision,
                            forceOverwrite = true
                        )
                        textInput(
                            id = "insp_notes_input",
                            text = state.draftNotes,
                            label = "Release Notes",
                            singleLine = false,
                            revision = state.draftRevision,
                            forceOverwrite = true
                        )
                        toggle(
                            id = "insp_lock_toggle",
                            checked = state.draftRequiresLock,
                            label = "Require Feature Lock to Publish"
                        )
                        row(id = "insp_actions_row", spacing = SpacingSize.SMALL) {
                            button(
                                id = "insp_save_btn",
                                label = "Save Draft",
                                action = "save_draft_changes",
                                isPrimary = false
                            )
                            button(
                                id = "insp_pull_btn",
                                label = "Pull Remote",
                                action = "pull_one:${selected.id}",
                                isPrimary = false
                            )
                            button(
                                id = "insp_push_btn",
                                label = "Push Chapter",
                                action = "push_one:${selected.id}",
                                isPrimary = true,
                                isDestructive = state.environment == "PRODUCTION"
                            )
                        }
                    }

                    val activePage = selected.pages.find { it.id == state.selectedPageId } ?: selected.pages.firstOrNull()
                    card(id = "insp_pages_summary_card", title = "Chapter Pages (${selected.pages.size})") {
                        if (activePage != null) {
                            image(
                                id = "insp_summary_thumb",
                                asset = activePage.asset,
                                contentDescription = activePage.label,
                                contentScale = ImageScale.FIT,
                                badgeText = activePage.label,
                                modifier = UiModifier.fillMaxWidth().height(120)
                            )
                        }
                        text(
                            id = "insp_pages_summary_hint",
                            text = "Open the dedicated Images view to preview full pages, drag tiles in 2D to reorder, or launch the Spatial Board compositor.",
                            role = TypographyRole.BODY_SMALL
                        )
                        row(id = "insp_pages_toolbar", spacing = SpacingSize.SMALL) {
                            button(
                                id = "insp_open_images_btn",
                                label = "Open Images (${selected.pages.size}) ->",
                                action = "open_images:${selected.id}",
                                isPrimary = true
                            )
                            button(
                                id = "insp_export_manifest_btn",
                                label = "Export JSON",
                                action = "export_chapter_json",
                                isPrimary = false
                            )
                            button(
                                id = "insp_open_board_btn",
                                label = "Open in Board",
                                action = "open_in_board:${selected.id}",
                                isPrimary = false
                            )
                        }
                    }

                    val latestJob = jobs.lastOrNull()
                    if (latestJob != null) {
                        card(id = "insp_job_card", title = "Latest Sync Activity") {
                            renderJobCardContent(prefix = "insp_job", job = latestJob)
                        }
                    }
                }
            }
        }
    )
}

private fun UiContainerBuilder.renderImagesLevel(
    state: ChapterPublisherState,
    selectedSeries: SeriesRecord?,
    selectedChapter: ChapterRecord?
) {
    if (selectedChapter == null) {
        emptyState(
            id = "img_empty_chapter",
            title = "Chapter not selected",
            description = "Return to Chapters and choose an episode to inspect or reorder its pages.",
            iconName = "image",
            actionLabel = "Back to Chapters",
            modifier = UiModifier.fillMaxWidth().weight(1f)
        )
        return
    }

    val activePage = selectedChapter.pages.find { it.id == state.selectedPageId }
        ?: selectedChapter.pages.firstOrNull()
    val activeIdx = activePage?.let { page -> selectedChapter.pages.indexOfFirst { it.id == page.id } } ?: -1

    splitPane(
        id = "pub_images_split",
        splitFraction = state.splitFraction,
        isHorizontal = true,
        resizable = true,
        modifier = UiModifier.fillMaxWidth().weight(1f),
        first = {
            scrollableColumn(
                id = "img_grid_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize()
                    .padding(SpacingSize.SMALL)
                    .shortcut(key = UiShortcut.KEY_DELETE, action = "remove_selected_page", label = "Remove Selected Page")
                    .shortcut(key = UiShortcut.KEY_BACKSPACE, action = "remove_selected_page", label = "Remove Selected Page")
                    .shortcut(key = UiShortcut.KEY_ARROW_LEFT, action = "move_page_prev", label = "Move Page Earlier")
                    .shortcut(key = UiShortcut.KEY_ARROW_RIGHT, action = "move_page_next", label = "Move Page Later")
                    .onFileDrop(
                        action = "drop_page_files",
                        allowedExtensions = listOf("png", "jpg", "jpeg", "webp"),
                        hint = "Drop PNG, JPG, or WEBP page images here"
                    )
            ) {
                row(id = "img_toolbar_row", spacing = SpacingSize.SMALL, modifier = UiModifier.fillMaxWidth()) {
                    button(
                        id = "insp_add_pages_btn",
                        label = "+ Add Pages...",
                        action = "pick_page_files",
                        isPrimary = false
                    )
                    button(
                        id = "img_reverse_btn",
                        label = "Reverse Order",
                        action = "reverse_page_order",
                        isPrimary = false
                    )
                    button(
                        id = "insp_export_manifest_btn",
                        label = "Export JSON",
                        action = "export_chapter_json",
                        isPrimary = false
                    )
                }
                text(
                    id = "img_drag_hint",
                    text = "${selectedSeries?.title ?: "Series"} / Ch. ${selectedChapter.number}: drag tiles to reorder, drop OS image files to append, or use ←/→/Del/Ctrl+S (${selectedChapter.pages.size} pages).",
                    role = TypographyRole.BODY_SMALL
                )
                if (selectedChapter.pages.isEmpty()) {
                    emptyState(
                        id = "img_empty_pages",
                        title = "No pages in chapter",
                        description = "Add PNG, JPG, or WEBP pages from disk to preview and reorder them.",
                        iconName = "image"
                    )
                } else {
                    grid(
                        id = "insp_pages_grid",
                        columns = null,
                        minItemWidthDp = 124f,
                        spacing = SpacingSize.SMALL,
                        reorderable = true,
                        modifier = UiModifier.fillMaxWidth()
                    ) {
                        selectedChapter.pages.forEachIndexed { idx, page ->
                            card(id = "insp_page_card_${page.id}") {
                                image(
                                    id = "insp_page_thumb_${page.id}",
                                    asset = page.asset,
                                    contentDescription = page.label,
                                    contentScale = ImageScale.CROP,
                                    clickable = true,
                                    action = "select_page:${page.id}",
                                    selected = page.id == activePage?.id,
                                    badgeText = "#${idx + 1}",
                                    modifier = UiModifier.fillMaxWidth().height(96)
                                )
                                text(
                                    id = "insp_page_caption_${page.id}",
                                    text = page.label,
                                    role = TypographyRole.LABEL
                                )
                            }
                        }
                    }
                }
            }
        },
        second = {
            scrollableColumn(
                id = "img_preview_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                if (activePage == null) {
                    emptyState(
                        id = "img_preview_empty",
                        title = "Select a page tile",
                        description = "Click any thumbnail in the grid to inspect it at larger size or open it in the Board.",
                        iconName = "image"
                    )
                } else {
                    card(
                        id = "img_preview_card",
                        title = "Preview — ${activePage.label} (${activeIdx + 1}/${selectedChapter.pages.size})"
                    ) {
                        image(
                            id = "insp_selected_page_preview",
                            asset = activePage.asset,
                            contentDescription = activePage.label,
                            contentScale = ImageScale.FIT,
                            badgeText = "#${activeIdx + 1}",
                            modifier = UiModifier.fillMaxWidth().height(230)
                        )
                        row(id = "img_preview_actions", spacing = SpacingSize.SMALL) {
                            button(
                                id = "img_move_prev_btn",
                                label = "Move Earlier",
                                action = "move_page_prev",
                                isPrimary = false
                            )
                            button(
                                id = "img_move_next_btn",
                                label = "Move Later",
                                action = "move_page_next",
                                isPrimary = false
                            )
                            button(
                                id = "img_remove_page_btn",
                                label = "Remove",
                                action = "remove_selected_page",
                                isPrimary = false,
                                isDestructive = true
                            )
                        }
                        button(
                            id = "insp_open_board_btn",
                            label = "Open Page in Board",
                            action = "open_page_in_board",
                            isPrimary = true
                        )
                    }
                }
            }
        }
    )
}

private fun UiContainerBuilder.renderQueueTab(jobs: List<CapabilityJobSnapshot>) {
    if (jobs.isEmpty()) {
        emptyState(
            id = "queue_empty_state",
            title = "No capability jobs yet",
            description = "Push or pull one or more chapters to watch live streaming capability progress and pause/resume/cancel controls.",
            iconName = "sync",
            modifier = UiModifier.fillMaxSize()
        )
        return
    }
    scrollableColumn(
        id = "queue_jobs_scroll",
        spacing = SpacingSize.SMALL,
        modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
    ) {
        jobs.reversed().forEach { job ->
            card(id = "queue_card_${job.jobId}", title = "${job.capabilityName} (${job.jobId})") {
                renderJobCardContent(prefix = "queue_${job.jobId}", job = job)
            }
        }
    }
}

private fun UiContainerBuilder.renderJobCardContent(
    prefix: String,
    job: CapabilityJobSnapshot
) {
    row(id = "${prefix}_header", spacing = SpacingSize.SMALL) {
        badge(
            id = "${prefix}_status_badge",
            text = job.status.name,
            color = jobStatusColor(job.status)
        )
        text(
            id = "${prefix}_message",
            text = job.primaryProgress?.message ?: job.errorMessage ?: "Waiting...",
            role = TypographyRole.BODY_MEDIUM
        )
    }

    val primary = job.primaryProgress
    if (primary != null) {
        progressBar(
            id = "${prefix}_primary_bar",
            progress = primary.fraction,
            label = job.capabilityName,
            statusText = primary.fraction?.let { "${(it * 100f).toInt()}%" } ?: primary.message
        )
    }

    val secondary = job.secondaryProgress
    if (secondary != null) {
        progressBar(
            id = "${prefix}_secondary_bar",
            progress = secondary.fraction,
            label = "Current Step",
            statusText = secondary.message,
            color = SemanticColor.ACCENT
        )
    }

    val isActive = job.status == CapabilityJobStatus.RUNNING ||
        job.status == CapabilityJobStatus.PAUSE_REQUESTED ||
        job.status == CapabilityJobStatus.PAUSED ||
        job.status == CapabilityJobStatus.QUEUED
    if (isActive) {
        row(id = "${prefix}_controls", spacing = SpacingSize.SMALL) {
            if (job.status == CapabilityJobStatus.PAUSED) {
                button(
                    id = "${prefix}_resume_btn",
                    label = "Resume",
                    action = "resume_job:${job.jobId}",
                    isPrimary = true
                )
            } else {
                button(
                    id = "${prefix}_pause_btn",
                    label = "Pause",
                    action = "pause_job:${job.jobId}",
                    isPrimary = false
                )
            }
            button(
                id = "${prefix}_cancel_btn",
                label = "Cancel",
                action = "cancel_job:${job.jobId}",
                isPrimary = false,
                isDestructive = true
            )
        }
    }

    if (job.recentLogs.isNotEmpty()) {
        text(
            id = "${prefix}_logs",
            text = job.recentLogs.takeLast(3).joinToString(" | "),
            role = TypographyRole.BODY_SMALL
        )
    }
}

private fun UiContainerBuilder.renderSettingsTab(
    state: ChapterPublisherState,
    userId: String,
    apiKey: String,
    featureUnlocked: Boolean
) {
    scrollableColumn(
        id = "cfg_scroll",
        spacing = SpacingSize.SMALL,
        modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
    ) {
        card(id = "cfg_env_card", title = "Publishing Target & Credentials") {
            dropdown(
                id = "cfg_env_dropdown",
                selectedKey = state.environment,
                options = mapOf(
                    "STAGING" to "Staging Sandbox (Auto-publish)",
                    "PRODUCTION" to "Production Live (Requires confirmation)"
                ),
                label = "Target Environment"
            )
            text(
                id = "cfg_user_info",
                text = "Signed-in User ID (live from settingsFlow): $userId",
                role = TypographyRole.BODY_MEDIUM
            )
            textInput(
                id = "cfg_api_key_input",
                text = apiKey,
                label = "Plugin API Key (writes directly to plugin settings)",
                placeholder = "Enter secret-key..."
            )
            button(
                id = "cfg_reset_api_key_btn",
                label = "Reset API Key to Default",
                action = "reset_api_key",
                isPrimary = false
            )
        }

        card(id = "cfg_locks_card", title = "Capability Feature Locks (locksFlow)") {
            row(id = "cfg_lock_row", spacing = SpacingSize.SMALL) {
                badge(
                    id = "cfg_lock_badge",
                    text = if (featureUnlocked) "feature_unlocked = TRUE" else "feature_unlocked = FALSE",
                    color = if (featureUnlocked) SemanticColor.SUCCESS else SemanticColor.WARNING
                )
                button(
                    id = "cfg_toggle_lock_btn",
                    label = if (featureUnlocked) "Lock Feature" else "Unlock Feature",
                    action = "toggle_feature_lock",
                    isPrimary = !featureUnlocked
                )
            }
            text(
                id = "cfg_lock_help",
                text = "Clicking the button above invokes @PluginAction toggleFeatureLock and immediately updates locksFlow across all open views.",
                role = TypographyRole.BODY_SMALL
            )
        }

        card(
            id = "cfg_integrations_card",
            title = "Encrypted Credentials & Integrations",
            modifier = UiModifier.dropTarget(
                UiDropTarget(
                    action = "drop_repo_folder",
                    acceptFiles = false,
                    acceptDirectories = true,
                    maxFiles = 1,
                    hint = "Drop local repository folder here"
                )
            )
        ) {
            row(id = "cfg_secret_status_row", spacing = SpacingSize.SMALL) {
                badge(
                    id = "cfg_secret_status_badge",
                    text = if (state.secretConfigured) "Encrypted Vault: Configured" else "Encrypted Vault: Not Set",
                    color = if (state.secretConfigured) SemanticColor.SUCCESS else SemanticColor.WARNING
                )
            }
            textInput(
                id = "cfg_secret_token_input",
                text = state.draftSecretToken,
                label = "Publisher Secret Token (AES-GCM Vault)",
                placeholder = "Enter API token or password...",
                isSecret = true,
                revision = state.draftSecretRevision,
                forceOverwrite = true
            )
            row(id = "cfg_integrations_actions", spacing = SpacingSize.SMALL) {
                button(
                    id = "cfg_save_secret_btn",
                    label = "Save Encrypted Secret",
                    action = "save_secret_token",
                    isPrimary = true
                )
                button(
                    id = "cfg_pick_repo_btn",
                    label = "Choose Repo Folder...",
                    action = "pick_repo_folder",
                    isPrimary = false
                )
                button(
                    id = "cfg_ping_webhook_btn",
                    label = "Test Sync Endpoint",
                    action = "ping_webhook",
                    isPrimary = false
                )
            }
            text(
                id = "cfg_repo_path_text",
                text = if (state.linkedRepoPath.isBlank()) {
                    "Linked Local Repository: Not selected"
                } else {
                    "Linked Local Repository: ${state.linkedRepoPath}"
                },
                role = TypographyRole.BODY_SMALL
            )
            val webhook = state.webhookStatus
            if (!webhook.isNullOrBlank()) {
                text(
                    id = "cfg_webhook_status_text",
                    text = "Last endpoint check: $webhook",
                    role = TypographyRole.BODY_SMALL
                )
            }
        }
    }
}
