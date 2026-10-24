package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.moveWithinGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_COLLAPSED = "routines_collapsed_folders"
private const val KEY_SHEET_MODE = "routines_folder_sheet_mode"
private const val KEY_SHEET_FOLDER = "routines_folder_sheet_folder"
private const val KEY_SHEET_NAME = "routines_folder_sheet_name"
private const val KEY_SHEET_EDITED = "routines_folder_sheet_edited"
private const val ID_SEPARATOR = ","

/**
 * "Mis rutinas" (US-10, US-35): lists the active routines grouped by folder in their manual order and runs edit,
 * duplicate, archive, folders, move, next workout, reordering and starting a session. Collapsed folders and the
 * folder sheet draft live in [savedState] (ADR-40). Writes need the network; reordering and "next" apply at once and
 * are reverted (with [RoutinesEffect.ActionFailed]) when the server rejects them.
 */
@Suppress("TooManyFunctions") // One small handler per event group.
class RoutinesViewModel(
    private val repository: RoutineRepository,
    private val sessions: WorkoutSessionRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutinesUiState>(RoutinesUiState.Loading)
    val uiState: StateFlow<RoutinesUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<RoutinesEffect>(Channel.BUFFERED)
    val effects: Flow<RoutinesEffect> = effectChannel.receiveAsFlow()

    private var online = true

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateContent { it.copy(isOnline = isOnline) }
            }
        }
        load(showLoading = true)
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: RoutinesEvent) {
        when (event) {
            RoutinesEvent.Retry -> if (_uiState.value == RoutinesUiState.Error) load(showLoading = true)

            RoutinesEvent.Refresh -> if (_uiState.value is RoutinesUiState.Content) load(showLoading = false)

            RoutinesEvent.NewRoutine -> effectChannel.trySend(RoutinesEffect.OpenBuilder(null))

            is RoutinesEvent.Edit -> {
                updateContent { it.copy(menuFor = null) }
                effectChannel.trySend(RoutinesEffect.OpenBuilder(event.id))
            }

            is RoutinesEvent.OpenMenu -> updateContent { content -> content.copy(menuFor = content.find(event.id)) }

            RoutinesEvent.DismissMenu -> updateContent { it.copy(menuFor = null) }

            is RoutinesEvent.Duplicate -> duplicate(event.id)

            is RoutinesEvent.RequestArchive ->
                updateContent { content ->
                    if (online) content.copy(menuFor = null, archiveFor = content.find(event.id)) else content
                }

            RoutinesEvent.ConfirmArchive -> confirmArchive()

            RoutinesEvent.DismissArchive -> updateContent { it.copy(archiveFor = null) }

            is RoutinesEvent.StartRoutine -> start { sessions.startFromRoutine(event.id) }

            else -> onFolderEvent(event)
        }
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    private fun onFolderEvent(event: RoutinesEvent) {
        when (event) {
            RoutinesEvent.NewFolder -> if (online) openSheet(FolderSheetState(FolderSheetMode.Create))

            is RoutinesEvent.RenameFolder ->
                (_uiState.value as? RoutinesUiState.Content)?.folders?.firstOrNull { it.id == event.id }?.let {
                    if (online) openSheet(FolderSheetState(FolderSheetMode.Rename, it.id, it.name))
                }

            is RoutinesEvent.FolderNameChanged ->
                updateContent { content ->
                    content.copy(
                        folderSheet = content.folderSheet?.copy(
                            name = event.name.take(RoutineBounds.NAME_MAX_LENGTH),
                            edited = true,
                        ),
                    )
                }.also { persistSheet() }

            RoutinesEvent.SaveFolder -> saveFolder()

            RoutinesEvent.DismissFolderSheet -> closeSheet()

            is RoutinesEvent.RequestDeleteFolder -> requestDeleteFolder(event.id)

            RoutinesEvent.ConfirmDeleteFolder ->
                (_uiState.value as? RoutinesUiState.Content)?.deleteFolderFor?.let { deleteFolder(it.id) }

            RoutinesEvent.DismissDeleteFolder -> updateContent { it.copy(deleteFolderFor = null) }

            is RoutinesEvent.OpenMove ->
                updateContent { content ->
                    if (online) content.copy(menuFor = null, moveFor = content.find(event.routineId)) else content
                }

            RoutinesEvent.DismissMove -> updateContent { it.copy(moveFor = null) }

            is RoutinesEvent.MoveToFolder -> moveToFolder(event.routineId, event.folderId)

            is RoutinesEvent.SetNext -> setNext(event.id)

            RoutinesEvent.ClearNext -> setNext(null)

            is RoutinesEvent.Reorder -> reorder(event.folderId, event.orderedIds)

            is RoutinesEvent.MoveUp -> moveBy(event.id, -1)

            is RoutinesEvent.MoveDown -> moveBy(event.id, 1)

            is RoutinesEvent.ToggleFolder -> toggleFolder(event.id)

            else -> Unit
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) _uiState.value = RoutinesUiState.Loading
        viewModelScope.launch {
            val routines = repository.listActive()
            val folders = if (routines.isSuccess) repository.listFolders() else Result.success(emptyList())
            val failed = routines.isFailure || folders.isFailure
            if (failed) {
                if (showLoading) _uiState.value = RoutinesUiState.Error
                return@launch
            }
            val list = routines.getOrThrow()
            val folderList = folders.getOrThrow()
            _uiState.update { current ->
                (
                    (current as? RoutinesUiState.Content)?.copy(routines = list, folders = folderList)
                        ?: RoutinesUiState.Content(
                            routines = list,
                            isOnline = online,
                            folders = folderList,
                            collapsedFolderIds = restoredCollapsed(),
                            folderSheet = restoredSheet(),
                        )
                    ).withDuplicateCheck()
            }
        }
    }

    private fun duplicate(id: String) {
        if (!online) return
        updateContent { it.copy(menuFor = null) }
        viewModelScope.launch {
            repository.duplicate(id).fold(
                onSuccess = { load(showLoading = false) },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    private fun start(create: suspend () -> Result<String>) {
        val content = _uiState.value as? RoutinesUiState.Content ?: return
        if (!online || content.starting) return
        updateContent { it.copy(starting = true) }
        viewModelScope.launch {
            val result = create()
            effectChannel.trySend(
                when {
                    result.isSuccess -> RoutinesEffect.OpenLiveSession
                    result.appErrorOrNull() == AppError.SessionAlreadyInProgress -> RoutinesEffect.SessionInProgress
                    else -> RoutinesEffect.StartFailed
                },
            )
            updateContent { it.copy(starting = false) }
        }
    }

    private fun confirmArchive() {
        val target = (_uiState.value as? RoutinesUiState.Content)?.archiveFor ?: return
        updateContent { it.copy(archiveFor = null) }
        if (!online) return
        viewModelScope.launch {
            repository.archive(target.id).fold(
                onSuccess = { updateContent { content -> content.copy(routines = content.routines - target) } },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    // ---- Folders ----

    private fun saveFolder() {
        val sheet = (_uiState.value as? RoutinesUiState.Content)?.folderSheet ?: return
        if (!online || !sheet.canSave) return
        viewModelScope.launch {
            val result = when (sheet.mode) {
                FolderSheetMode.Create -> repository.createFolder(sheet.name.trim()).map {}
                FolderSheetMode.Rename -> repository.renameFolder(sheet.folderId.orEmpty(), sheet.name.trim())
            }
            result.fold(
                onSuccess = {
                    closeSheet()
                    load(showLoading = false)
                },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    private fun requestDeleteFolder(id: String) {
        val content = _uiState.value as? RoutinesUiState.Content ?: return
        val folder = content.folders.firstOrNull { it.id == id }
        if (!online || folder == null) return
        if (content.routines.none { it.folderId == id }) {
            deleteFolder(id)
        } else {
            updateContent {
                it.copy(deleteFolderFor = folder)
            }
        }
    }

    private fun deleteFolder(id: String) {
        updateContent { it.copy(deleteFolderFor = null) }
        if (!online) return
        viewModelScope.launch {
            repository.deleteFolder(id).fold(
                onSuccess = {
                    updateContent { content ->
                        content.copy(
                            folders = content.folders.filterNot { it.id == id },
                            routines = content.routines.filterNot { it.folderId == id },
                            collapsedFolderIds = content.collapsedFolderIds - id,
                        )
                    }
                    persistCollapsed()
                },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    private fun toggleFolder(id: String) {
        updateContent { content ->
            val collapsed = content.collapsedFolderIds
            content.copy(collapsedFolderIds = if (id in collapsed) collapsed - id else collapsed + id)
        }
        persistCollapsed()
    }

    private fun moveToFolder(routineId: String, folderId: String?) {
        updateContent { it.copy(menuFor = null, moveFor = null) }
        if (!online) return
        viewModelScope.launch {
            repository.moveToFolder(routineId, folderId).fold(
                onSuccess = { load(showLoading = false) },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    // ---- Next workout and order (optimistic, reverted on failure) ----

    private fun setNext(id: String?) {
        updateContent { it.copy(menuFor = null) }
        val before = (_uiState.value as? RoutinesUiState.Content)?.routines ?: return
        if (!online) return
        updateContent { content -> content.copy(routines = content.routines.map { it.copy(isNext = it.id == id) }) }
        viewModelScope.launch {
            repository.setNext(id).onFailure { revert(before) }
        }
    }

    private fun moveBy(id: String, delta: Int) {
        val content = _uiState.value as? RoutinesUiState.Content ?: return
        val folderId = content.find(id)?.folderId
        val ids = content.routines.filter { it.folderId == folderId }.map { it.id }
        updateContent { it.copy(menuFor = null) }
        reorder(folderId, moveWithinGroup(ids, id, delta))
    }

    private fun reorder(folderId: String?, orderedIds: List<String>) {
        val before = (_uiState.value as? RoutinesUiState.Content)?.routines ?: return
        val group = before.filter { it.folderId == folderId }
        val byId = group.associateBy { it.id }
        val newGroup = orderedIds.mapNotNull(byId::get) + group.filter { it.id !in orderedIds }
        if (!online || newGroup.map { it.id } == group.map { it.id }) return
        val queue = newGroup.iterator()
        val reordered = before.map { if (it.folderId == folderId) queue.next() else it }
        updateContent { it.copy(routines = reordered) }
        viewModelScope.launch {
            repository.saveOrder(folderId, newGroup.map { it.id }).onFailure { revert(before) }
        }
    }

    private fun revert(before: List<RoutineSummary>) {
        updateContent { it.copy(routines = before) }
        effectChannel.trySend(RoutinesEffect.ActionFailed)
    }

    // ---- Saved state (ADR-40) ----

    private fun openSheet(sheet: FolderSheetState) {
        updateContent { it.copy(folderSheet = sheet) }
        persistSheet()
    }

    private fun closeSheet() {
        updateContent { it.copy(folderSheet = null) }
        persistSheet()
    }

    private fun persistSheet() {
        val sheet = (_uiState.value as? RoutinesUiState.Content)?.folderSheet
        savedState[KEY_SHEET_MODE] = sheet?.mode?.name
        savedState[KEY_SHEET_FOLDER] = sheet?.folderId
        savedState[KEY_SHEET_NAME] = sheet?.name
        savedState[KEY_SHEET_EDITED] = sheet?.edited
    }

    private fun restoredSheet(): FolderSheetState? {
        val mode = savedState.get<String>(KEY_SHEET_MODE)?.let { FolderSheetMode.valueOf(it) } ?: return null
        return FolderSheetState(
            mode = mode,
            folderId = savedState.get<String>(KEY_SHEET_FOLDER),
            name = savedState.get<String>(KEY_SHEET_NAME).orEmpty(),
            edited = savedState.get<Boolean>(KEY_SHEET_EDITED) ?: false,
        )
    }

    private fun persistCollapsed() {
        val collapsed = (_uiState.value as? RoutinesUiState.Content)?.collapsedFolderIds ?: return
        savedState[KEY_COLLAPSED] = collapsed.joinToString(ID_SEPARATOR)
    }

    private fun restoredCollapsed(): Set<String> =
        savedState.get<String>(KEY_COLLAPSED)?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    private fun RoutinesUiState.Content.find(id: String): RoutineSummary? = routines.firstOrNull { it.id == id }

    private fun updateContent(transform: (RoutinesUiState.Content) -> RoutinesUiState.Content) {
        _uiState.update { if (it is RoutinesUiState.Content) transform(it).withDuplicateCheck() else it }
    }

    /** Derives [FolderSheetState.duplicateName] from the typed name and the folders (nothing extra is persisted). */
    private fun RoutinesUiState.Content.withDuplicateCheck(): RoutinesUiState.Content = copy(
        folderSheet = folderSheet?.let {
            it.copy(duplicateName = isDuplicateFolderName(it.name, folders, ignoreId = it.folderId))
        },
    )
}
