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
import org.wip.plugintoolkit.api.sdui.CapabilityJobSnapshot
import org.wip.plugintoolkit.api.sdui.CapabilityJobStatus
import org.wip.plugintoolkit.api.sdui.ChipItem
import org.wip.plugintoolkit.api.sdui.ControlSize
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TabItem
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiContainerBuilder
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.uiTree

private val publisherJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private data class ChapterPublisherState(
    val activeTab: String = "chapters",
    val environment: String = "STAGING",
    val searchQuery: String = "",
    val statusFilter: String = "ALL",
    val chapters: List<ChapterRecord> = emptyList(),
    val selectedChapterIds: Set<String> = emptySet(),
    val inspectedChapterId: String? = null,
    val draftTitle: String = "",
    val draftNotes: String = "",
    val draftRequiresLock: Boolean = false,
    val draftRevision: Int = 0,
    val isLoading: Boolean = true,
    val showConfirmDialog: Boolean = false,
    val pendingPushIds: List<String> = emptyList(),
    val bannerMessage: String? = "Loaded synced & remote chapter catalogs.",
    val bannerSeverity: SemanticColor = SemanticColor.INFO
)

internal fun runChapterPublisherSession(
    context: UiSessionContext,
    events: Flow<UiEvent>
): Flow<UiNodeTree> = channelFlow {
    val stateFlow = MutableStateFlow(ChapterPublisherState())
    val handledCompletedJobIds = mutableSetOf<String>()

    suspend fun refreshCatalog(environment: String, statusNote: String? = null) {
        val execResult = context.executeCapability(
            capabilityName = "queryChapterCatalog",
            parameters = mapOf("environment" to JsonPrimitive(environment))
        )
        val encodedJson = (execResult as? ExecutionResult.Success)
            ?.response
            ?.metadata
            ?.get("chaptersJson")
        val chapters = if (!encodedJson.isNullOrBlank()) {
            runCatching { publisherJson.decodeFromString<List<ChapterRecord>>(encodedJson) }.getOrNull()
        } else {
            null
        } ?: run {
            val local = CompleteExampleCatalogStore.loadLocalCatalog(context.storage)
            val remote = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
            CompleteExampleCatalogStore.reconcile(local, remote)
        }

        stateFlow.update { current ->
            val validIds = chapters.map { it.id }.toSet()
            val nextInspected = current.inspectedChapterId?.takeIf { it in validIds }
                ?: chapters.firstOrNull()?.id
            val inspectedChapter = chapters.find { it.id == nextInspected }
            val changedInspector = nextInspected != current.inspectedChapterId || current.isLoading
            current.copy(
                chapters = chapters,
                selectedChapterIds = current.selectedChapterIds.intersect(validIds),
                inspectedChapterId = nextInspected,
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

    launch {
        val savedEnv = context.storage.get(CompleteExampleCatalogStore.STORAGE_ENVIRONMENT)
            ?.jsonPrimitive?.contentOrNull
            ?.takeIf { it == "STAGING" || it == "PRODUCTION" }
            ?: "STAGING"
        stateFlow.update { it.copy(environment = savedEnv) }
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
                        "pub_search_input" -> stateFlow.update { it.copy(searchQuery = event.text) }
                        "insp_title_input" -> stateFlow.update { it.copy(draftTitle = event.text) }
                        "insp_notes_input" -> stateFlow.update { it.copy(draftNotes = event.text) }
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
                is UiEvent.OptionSelected -> {
                    if (event.nodeId == "cfg_env_dropdown") {
                        val nextEnv = event.selectedKey
                        context.storage.put(CompleteExampleCatalogStore.STORAGE_ENVIRONMENT, JsonPrimitive(nextEnv))
                        stateFlow.update { it.copy(environment = nextEnv) }
                        refreshCatalog(nextEnv, "Switched target environment to $nextEnv.")
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
                                        draftTitle = target.title,
                                        draftNotes = target.releaseNotes,
                                        draftRequiresLock = target.requiresLock,
                                        draftRevision = current.draftRevision + 1
                                    )
                                } else current
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
                            val current = stateFlow.value
                            val targetId = current.inspectedChapterId
                            if (targetId != null) {
                                val existing = CompleteExampleCatalogStore.loadLocalCatalog(context.storage).toMutableList()
                                val remote = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
                                val idx = existing.indexOfFirst { it.id == targetId }
                                val edited = current.chapters.find { it.id == targetId }?.copy(
                                    title = current.draftTitle.ifBlank { "Untitled Chapter" },
                                    releaseNotes = current.draftNotes,
                                    requiresLock = current.draftRequiresLock,
                                    localRevision = (current.chapters.find { it.id == targetId }?.localRevision ?: 0) + 1,
                                    status = ChapterSyncStatus.OUT_OF_SYNC
                                )
                                if (edited != null) {
                                    if (idx >= 0) existing[idx] = edited else existing.add(edited)
                                    CompleteExampleCatalogStore.saveLocalCatalog(context.storage, existing)
                                    val reconciled = CompleteExampleCatalogStore.reconcile(existing, remote)
                                    stateFlow.update {
                                        it.copy(
                                            chapters = reconciled,
                                            bannerMessage = "Saved local draft revision ${edited.localRevision} for Chapter ${edited.number}.",
                                            bannerSeverity = SemanticColor.SUCCESS
                                        )
                                    }
                                }
                            }
                        }
                        action == "create_new_draft" -> {
                            val existing = CompleteExampleCatalogStore.loadLocalCatalog(context.storage).toMutableList()
                            val remote = CompleteExampleCatalogStore.loadRemoteCatalog(context.storage)
                            val nextNum = ((existing + remote).maxOfOrNull { it.number } ?: 40) + 1
                            val newId = "ch_${nextNum.toString().padStart(3, '0')}"
                            val newChapter = ChapterRecord(
                                id = newId,
                                number = nextNum,
                                title = "New Episode Draft #$nextNum",
                                pageCount = 24,
                                sizeMb = 9.8,
                                localRevision = 1,
                                remoteRevision = 0,
                                status = ChapterSyncStatus.LOCAL_DRAFT,
                                releaseNotes = "Prepared locally in interactive publisher."
                            )
                            existing.add(newChapter)
                            CompleteExampleCatalogStore.saveLocalCatalog(context.storage, existing)
                            val reconciled = CompleteExampleCatalogStore.reconcile(existing, remote)
                            stateFlow.update {
                                it.copy(
                                    chapters = reconciled,
                                    inspectedChapterId = newId,
                                    draftTitle = newChapter.title,
                                    draftNotes = newChapter.releaseNotes,
                                    draftRequiresLock = newChapter.requiresLock,
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

private fun filteredChapters(state: ChapterPublisherState): List<ChapterRecord> {
    val query = state.searchQuery.trim().lowercase()
    return state.chapters.filter { ch ->
        val matchesStatus = state.statusFilter == "ALL" || ch.status.name == state.statusFilter
        val matchesQuery = query.isEmpty() ||
            ch.title.lowercase().contains(query) ||
            "chapter ${ch.number}".contains(query) ||
            ch.id.lowercase().contains(query)
        matchesStatus && matchesQuery
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

    scaffold(id = "publisher_scaffold", modifier = UiModifier.fillMaxSize()) {
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
                            label = "Chapters",
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
                        "chapters" -> renderChaptersTab(state = state, jobs = jobs, featureUnlocked = featureUnlocked)
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

private fun UiContainerBuilder.renderChaptersTab(
    state: ChapterPublisherState,
    jobs: List<CapabilityJobSnapshot>,
    featureUnlocked: Boolean
) {
    val visible = filteredChapters(state)
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.id in state.selectedChapterIds }

    splitPane(
        id = "pub_chapters_split",
        splitFraction = 0.56f,
        isHorizontal = true,
        modifier = UiModifier.fillMaxSize(),
        first = {
            column(
                id = "pub_catalog_pane",
                spacing = SpacingSize.SMALL,
                modifier = UiModifier.fillMaxSize().padding(SpacingSize.SMALL)
            ) {
                textInput(
                    id = "pub_search_input",
                    text = state.searchQuery,
                    label = "Filter Chapters",
                    placeholder = "Search title, ID, or chapter number..."
                )
                chipGroup(
                    id = "pub_filter_chips",
                    selectedKeys = setOf(state.statusFilter),
                    multiSelect = false,
                    chips = listOf(
                        ChipItem("ALL", "All (${state.chapters.size})"),
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
                                badgeText = statusLabel,
                                badgeColor = statusColor,
                                selected = ch.id == state.inspectedChapterId,
                                trailingBlock = {
                                    checkbox(
                                        id = "pub_chk_${ch.id}",
                                        checked = ch.id in state.selectedChapterIds
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
                        description = "Choose a chapter from the catalog list to inspect diffs, edit metadata, or push/pull.",
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
    }
}
