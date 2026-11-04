package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseNames
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.usecase.ChecklistRow
import io.github.lopezjavier.appfitness.domain.usecase.DaysRow
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewResult
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow
import io.github.lopezjavier.appfitness.domain.usecase.StrengthOrderRow
import io.github.lopezjavier.appfitness.domain.usecase.StrengthRepsRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_FOLDER = "folderId"

/**
 * "Revisar tus rutinas" (US-95): the score and checklist of a folder, computed on the device by [review] from data
 * already there (no model, no network, no write). The folder id comes from the route's [savedState]; the result is
 * recomputed whenever the model is created, so a recreated screen (or a folder edited meanwhile) is always current.
 * A missing exercise name fails the load instead of inventing one (R-54).
 */
class FolderReviewResultViewModel(
    private val review: suspend (folderId: String) -> Result<FolderReviewResult>,
    private val names: ExerciseNameResolver,
    private val routines: RoutineRepository,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val folderId: String = savedState.get<String>(KEY_FOLDER).orEmpty()

    private val _uiState = MutableStateFlow<FolderReviewResultUiState>(FolderReviewResultUiState.Loading)
    val uiState: StateFlow<FolderReviewResultUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: FolderReviewResultEvent) {
        when (event) {
            FolderReviewResultEvent.Retry -> if (_uiState.value == FolderReviewResultUiState.Error) load()
        }
    }

    private fun load() {
        _uiState.value = FolderReviewResultUiState.Loading
        viewModelScope.launch { _uiState.value = compute() }
    }

    @Suppress("ReturnCount") // Early exits on a failed read keep the happy path flat.
    private suspend fun compute(): FolderReviewResultUiState {
        val result = review(folderId).getOrNull() ?: return FolderReviewResultUiState.Error
        if (result.isEmpty) return FolderReviewResultUiState.Empty
        val resolved = names.resolve().getOrNull() ?: return FolderReviewResultUiState.Error
        val items = result.rows.map { it.toItem(resolved) }
        if (items.any { it == null }) return FolderReviewResultUiState.Error
        val folderName = routines.listFolders().getOrNull()?.firstOrNull { it.id == folderId }?.name
        return FolderReviewResultUiState.Content(
            folderName = folderName,
            score = result.score,
            strength = result.strength,
            complete = result.complete,
            liftingRoutines = result.liftingRoutines,
            items = items.filterNotNull(),
        )
    }

    /** Null when a name the row needs is unknown. */
    private fun ChecklistRow.toItem(names: ExerciseNames): ReviewItem? = when (this) {
        is GroupRow -> toReviewItem()

        is DaysRow -> ReviewItem.Days(liftingRoutines, met)

        is StrengthRepsRow -> offenders.map { o ->
            names.byId[o.exerciseId]?.let { RepLine(o.routineName, it, o.repMin, o.repMax) }
        }.takeIf { lines -> lines.none { it == null } }?.let { ReviewItem.Reps(it.filterNotNull()) }

        is StrengthOrderRow -> offenders.map { o ->
            val mainLift = names.byId[o.mainLiftId]
            val earlier = names.byId[o.earlierExerciseId]
            val mainLifts = o.mainLiftIds.map { names.byId[it] }
            if (mainLift == null || earlier == null || mainLifts.any { it == null }) {
                null
            } else {
                OrderLine(o.routineName, mainLift, earlier, mainLifts.filterNotNull())
            }
        }.takeIf { lines -> lines.none { it == null } }?.let { ReviewItem.Order(it.filterNotNull()) }
    }
}
