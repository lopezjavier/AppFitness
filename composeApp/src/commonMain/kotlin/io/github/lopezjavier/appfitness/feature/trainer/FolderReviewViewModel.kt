package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.FolderReviewRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val KEY_FOLDER = "folderId"
private const val KEY_PURPOSE = "purpose"
private const val KEY_PLAN = "planId"
private const val KEY_REVIEW_ID = "folder_review_id"
private const val KEY_ACCEPTED = "folder_review_accepted"
private const val KEY_PENDING = "folder_review_pending"
private const val PURPOSE_BLOCK = "block"

/**
 * Reviews a routine folder with the coach (US-95) and, for the block purpose, builds the block from it (US-93). The
 * route arguments (`folderId`, `purpose` = `review|block`, `planId`) come from [savedState]. The review is a paid
 * call: it is asked only by [FolderReviewEvent.Request] or [FolderReviewEvent.Retry], never by creation. The review
 * id, the checked ids and an in-flight flag live in [savedState]; a recreated model reads the stored review (free)
 * or, with a call in flight and nothing stored, shows [PlanFailureKind.Interrupted] and never asks by itself.
 * Nothing is written until "Aplicar en una copia" or "Crear bloque".
 */
@Suppress("TooManyFunctions", "LongParameterList") // One small handler per event.
class FolderReviewViewModel(
    private val reviews: FolderReviewRepository,
    private val names: ExerciseNameResolver,
    private val library: ExerciseLibrary,
    private val routines: RoutineRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val folderId: String = savedState.get<String>(KEY_FOLDER).orEmpty()
    private val purpose = if (savedState.get<String>(KEY_PURPOSE) == PURPOSE_BLOCK) {
        ReviewPurpose.Block
    } else {
        ReviewPurpose.Review
    }
    private val planId: String? = savedState.get<String>(KEY_PLAN)

    private val _uiState = MutableStateFlow<FolderReviewUiState>(FolderReviewUiState.Idle)
    val uiState: StateFlow<FolderReviewUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<FolderReviewEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var context: FolderContext? = null

    /** The review kept after a failure that is not the coach's, so "Reintentar" does not bill again. */
    private var keptReview: FolderReview? = null

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        val reviewId = savedState.get<String>(KEY_REVIEW_ID)
        when {
            reviewId != null -> viewModelScope.launch { resume(reviewId) }

            savedState.get<Boolean>(KEY_PENDING) == true -> {
                savedState.remove<Boolean>(KEY_PENDING)
                _uiState.value = FolderReviewUiState.Failed(PlanFailureKind.Interrupted)
            }
        }
    }

    fun onEvent(event: FolderReviewEvent) {
        when (event) {
            FolderReviewEvent.Request -> if (_uiState.value == FolderReviewUiState.Idle) request()
            is FolderReviewEvent.Toggle -> toggle(event.id)
            FolderReviewEvent.Apply -> apply()
            FolderReviewEvent.Continue -> createBlock()
            FolderReviewEvent.Retry -> retry()
        }
    }

    private fun request() {
        _uiState.value = FolderReviewUiState.Requesting
        savedState[KEY_PENDING] = true
        viewModelScope.launch {
            reviews.review(folderId, purpose, planId, today()).fold(
                onSuccess = {
                    savedState.remove<Boolean>(KEY_PENDING)
                    savedState[KEY_REVIEW_ID] = it.id
                    show(it, defaultAccepted(it))
                },
                onFailure = {
                    savedState.remove<Boolean>(KEY_PENDING)
                    _uiState.value = FolderReviewUiState.Failed(planFailureKindOf(it))
                },
            )
        }
    }

    private suspend fun resume(reviewId: String) {
        _uiState.value = FolderReviewUiState.Requesting
        val stored = reviews.stored(reviewId)
        val review = stored.getOrNull()
        when {
            review != null -> show(review, restoredAccepted(review))

            stored.isFailure ->
                _uiState.value = FolderReviewUiState.Failed(planFailureKindOf(stored.exceptionOrNull()))

            else -> forgetReview(FolderReviewUiState.Failed(PlanFailureKind.Other))
        }
    }

    private fun forgetReview(next: FolderReviewUiState) {
        keptReview = null
        savedState.remove<String>(KEY_REVIEW_ID)
        savedState.remove<String>(KEY_ACCEPTED)
        _uiState.value = next
    }

    private fun retry() {
        val state = _uiState.value as? FolderReviewUiState.Failed ?: return
        val kept = keptReview
        when {
            kept != null && !state.changed -> viewModelScope.launch { show(kept, restoredAccepted(kept)) }

            else -> {
                forgetReview(FolderReviewUiState.Idle)
                request()
            }
        }
    }

    /** Resolves every name the screen needs; a missing name fails the screen (R-54), the review stays kept. */
    private suspend fun show(review: FolderReview, accepted: Set<String>) {
        keptReview = review
        _uiState.value = FolderReviewUiState.Requesting
        val folderContext = loadContext(review)
        if (folderContext.isFailure || review.folderId == null) {
            _uiState.value = FolderReviewUiState.Failed(planFailureKindOf(folderContext.exceptionOrNull()))
            return
        }
        val resolved = folderContext.getOrThrow()
        context = resolved
        val rows = review.changes.map { change -> change.op.toLine(resolved)?.let { change to it } }
        if (rows.any { it == null }) {
            _uiState.value = FolderReviewUiState.Failed(PlanFailureKind.Other)
            return
        }
        persistAccepted(accepted)
        _uiState.value = FolderReviewUiState.Content(
            review = review,
            accepted = accepted,
            rows = rows.filterNotNull().map { (change, line) -> ChangeRow(change, line, change.id in accepted) },
            allFalta = review.falta.map(resolved::itemOf),
        )
    }

    private suspend fun loadContext(review: FolderReview): Result<FolderContext> = runCatching {
        val resolvedNames = names.resolve().getOrThrow()
        val folderRoutines = routines.listActive().getOrThrow().filter { it.folderId == review.folderId }.map {
            val routine = routines.get(it.id).getOrThrow()
            FolderRoutine(routine.name, routine.exercises.sortedBy { x -> x.position }.map { x -> x.exerciseId })
        }
        FolderContext(folderRoutines, resolvedNames.byId, library.load().mapValues { it.value.name })
    }

    private fun content(): FolderReviewUiState.Content? = _uiState.value as? FolderReviewUiState.Content

    private fun toggle(id: String) {
        val content = content() ?: return
        if (content.working) return
        val accepted = toggled(content.review, content.accepted, id)
        persistAccepted(accepted)
        _uiState.value = content.copy(
            accepted = accepted,
            rows = content.rows.map { it.copy(checked = it.id in accepted) },
            blockedFalta = emptyList(),
        )
    }

    private fun acceptedInOrder(content: FolderReviewUiState.Content): List<String> =
        content.review.changes.map { it.id }.filter { it in content.accepted }

    private fun apply() {
        val content = content() ?: return
        if (!content.canApply) return
        setWorking(true)
        viewModelScope.launch {
            reviews.applyCopy(content.review.id, acceptedInOrder(content)).fold(
                onSuccess = { copy ->
                    val name = routines.listFolders().getOrNull()?.firstOrNull { it.id == copy.folderId }?.name
                    setWorking(false)
                    effectChannel.send(FolderReviewEffect.Applied(copy.folderId, name))
                },
                onFailure = { failed(it) },
            )
        }
    }

    private fun createBlock() {
        val content = content() ?: return
        val plan = planId
        if (!content.canCreateBlock || plan == null) return
        setWorking(true)
        viewModelScope.launch {
            reviews.blockFromReview(plan, today(), content.review.id, acceptedInOrder(content)).fold(
                onSuccess = {
                    setWorking(false)
                    effectChannel.send(FolderReviewEffect.BlockReady)
                },
                onFailure = { failed(it) },
            )
        }
    }

    /** NeedsChanges shows the server's Falta list; a stale folder asks for a new review; the rest keep the content. */
    private suspend fun failed(error: Throwable) {
        val coachFailure = (error as? CoachException)?.failure
        when (coachFailure) {
            is CoachFailure.NeedsChanges -> {
                val resolved = context
                _uiState.update { state ->
                    if (state is FolderReviewUiState.Content) {
                        state.copy(
                            working = false,
                            blockedFalta = coachFailure.failures.map {
                                resolved?.itemOf(it) ?: FaltaItem(it, null, null)
                            },
                        )
                    } else {
                        state
                    }
                }
            }

            CoachFailure.Conflict, CoachFailure.InvalidReplacement ->
                forgetReview(FolderReviewUiState.Failed(PlanFailureKind.Other, changed = true))

            else -> {
                setWorking(false)
                effectChannel.send(
                    FolderReviewEffect.ActionFailed(Result.failure<Unit>(error).appErrorOrNull() == AppError.Network),
                )
            }
        }
    }

    private fun setWorking(working: Boolean) {
        _uiState.update { if (it is FolderReviewUiState.Content) it.copy(working = working) else it }
    }

    private fun persistAccepted(accepted: Set<String>) {
        savedState[KEY_ACCEPTED] = Json.encodeToString(ListSerializer(String.serializer()), accepted.toList())
    }

    private fun restoredAccepted(review: FolderReview): Set<String> {
        val ids = review.changes.map { it.id }.toSet()
        val saved = savedState.get<String>(KEY_ACCEPTED)?.let {
            runCatching { Json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        } ?: return defaultAccepted(review)
        return saved.filter { it in ids }.toSet()
    }
}
