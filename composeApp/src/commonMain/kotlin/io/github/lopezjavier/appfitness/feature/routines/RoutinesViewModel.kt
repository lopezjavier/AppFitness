package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.designsystem.NameSheetDefaults
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
private const val ID_SEPARATOR = ","

/**
 * "Rutinas" (US-10, US-35): lists the active routines grouped by folder in their manual order and runs edit,
 * duplicate, delete, folders, move, next workout, reordering and starting a session. Collapsed folders and the
 * open folder sheet live in [savedState] (ADR-40); the typed folder name belongs to the Name sheet. Writes need
 * the network; reordering, "next" and delete apply at once and are reverted (with [RoutinesEffect.ActionFailed])
 * when the server rejects them.
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

            is RoutinesEvent.OpenDetails -> effectChannel.trySend(RoutinesEffect.OpenDetails(event.id))

            is RoutinesEvent.OpenMenu -> updateContent { content -> content.copy(menuFor = content.find(event.id)) }

            RoutinesEvent.DismissMenu -> updateContent { it.copy(menuFor = null) }

            is RoutinesEvent.Duplicate -> duplicate(event.id)

            is RoutinesEvent.Delete -> deleteRoutine(event.id)

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
                    if (online) openSheet(FolderSheetState(FolderSheetMode.Rename, it.id))
                }

            is RoutinesEvent.SaveFolder -> saveFolder(event.name)

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
                (current as? RoutinesUiState.Content)?.copy(routines = list, folders = folderList)
                    ?: RoutinesUiState.Content(
                        routines = list,
                        isOnline = online,
                        folders = folderList,
                        collapsedFolderIds = restoredCollapsed(),
                        folderSheet = restoredSheet(),
                    )
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
            effectChannel.trySend(
                when (create().toStartOutcome()) {
                    SessionStartOutcome.Started -> RoutinesEffect.OpenLiveSession
                    SessionStartOutcome.AlreadyInProgress -> RoutinesEffect.SessionInProgress
                    SessionStartOutcome.Failed -> RoutinesEffect.StartFailed
                },
            )
            updateContent { it.copy(starting = false) }
        }
    }

    /** Optimistic: the routine leaves the list at once; a server failure puts the list back. */
    private fun deleteRoutine(id: String) {
        val before = (_uiState.value as? RoutinesUiState.Content)?.routines ?: return
        updateContent { it.copy(menuFor = null) }
        if (!online) {
            effectChannel.trySend(RoutinesEffect.OfflineRefused)
            return
        }
        updateContent { content -> content.copy(routines = content.routines.filterNot { it.id == id }) }
        viewModelScope.launch {
            repository.delete(id).onFailure { revert(before) }
        }
    }

    // ---- Folders ----

    private fun saveFolder(rawName: String) {
        val content = _uiState.value as? RoutinesUiState.Content
        val sheet = content?.folderSheet ?: return
        val name = NameSheetDefaults.normalize(rawName)
        val ownName = content.folders.firstOrNull { it.id == sheet.folderId }?.name
        val taken = NameSheetDefaults.isDuplicate(name, content.folders.map { it.name }, ignore = ownName)
        if (!online || content.folderSaving || !NameSheetDefaults.canSave(name, taken)) return
        updateContent { it.copy(folderSaving = true) }
        viewModelScope.launch {
            val result = when (sheet.mode) {
                FolderSheetMode.Create -> repository.createFolder(name).map {}
                FolderSheetMode.Rename -> repository.renameFolder(sheet.folderId.orEmpty(), name)
            }
            updateContent { it.copy(folderSaving = false) }
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
    }

    private fun restoredSheet(): FolderSheetState? {
        val mode = savedState.get<String>(KEY_SHEET_MODE)?.let { FolderSheetMode.valueOf(it) } ?: return null
        return FolderSheetState(mode = mode, folderId = savedState.get<String>(KEY_SHEET_FOLDER))
    }

    private fun persistCollapsed() {
        val collapsed = (_uiState.value as? RoutinesUiState.Content)?.collapsedFolderIds ?: return
        savedState[KEY_COLLAPSED] = collapsed.joinToString(ID_SEPARATOR)
    }

    private fun restoredCollapsed(): Set<String> =
        savedState.get<String>(KEY_COLLAPSED)?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    private fun RoutinesUiState.Content.find(id: String): RoutineSummary? = routines.firstOrNull { it.id == id }

    private fun updateContent(transform: (RoutinesUiState.Content) -> RoutinesUiState.Content) {
        _uiState.update { if (it is RoutinesUiState.Content) transform(it) else it }
    }
}
