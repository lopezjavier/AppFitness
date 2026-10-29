package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraftBounds
import io.github.lopezjavier.appfitness.domain.usecase.ValidateWorkoutDraft
import io.github.lopezjavier.appfitness.domain.usecase.WorkoutDraftIssue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** What both form screens show once the workout is loaded. */
data class WorkoutFormView(
    val form: WorkoutForm,
    /** Exercise name by exercise id; a missing id has no name (the UI shows a generic label). */
    val names: Map<String, String>,
    /** Every problem of the form right now; the UI flags fields once the user edited the form (R-27). */
    val issues: List<WorkoutDraftIssue>,
)

/**
 * The draft operations shared by `EditWorkoutViewModel` and `PastWorkoutViewModel`: it holds the [WorkoutForm], keeps
 * it in the [SavedStateHandle] under [keyPrefix] (ADR-40), applies [WorkoutFormEvent]s and validates. [checkDate] is
 * true when the date can still be chosen (creation). [onChange] is called after every change, also the ones that
 * finish asynchronously (an exercise created after the lookup was read).
 */
@Suppress("LongParameterList", "TooManyFunctions") // Injected collaborators; one small function per operation.
internal class WorkoutDraftEditor(
    private val savedState: SavedStateHandle,
    private val keyPrefix: String,
    private val validate: ValidateWorkoutDraft,
    private val checkDate: Boolean,
    private val scope: CoroutineScope,
    private val loadLookup: suspend () -> ExerciseLookup?,
    private val onChange: () -> Unit,
) {
    private val formKey = "${keyPrefix}_form"
    private val initialKey = "${keyPrefix}_initial"

    var form: WorkoutForm? = savedState.get<String>(formKey)?.let {
        runCatching { json.decodeFromString<WorkoutForm>(it) }.getOrNull()
    }
        private set

    /** The form as it was when editing started; a restored form restores its own initial too. */
    private var initial: WorkoutForm? = savedState.get<String>(initialKey)?.let {
        runCatching { json.decodeFromString<WorkoutForm>(it) }.getOrNull()
    } ?: form

    /** True when the form differs from the one editing started with. */
    val isDirty: Boolean get() = form != initial

    var names: Map<String, String> = emptyMap()
        private set

    private var metricTypes: Map<String, ExerciseMetricType> = emptyMap()

    /** True when a form was restored from the saved state, so it must not be replaced by the loaded source. */
    val restored: Boolean get() = form != null

    fun applyLookup(lookup: ExerciseLookup?) {
        names = lookup?.names ?: names
        metricTypes = lookup?.metricTypes ?: metricTypes
    }

    fun metricTypes(): Map<String, ExerciseMetricType> = metricTypes

    /** Starts editing [initial], unless a form was restored. */
    fun start(initial: WorkoutForm) {
        if (form != null) return
        this.initial = initial
        savedState[initialKey] = json.encodeToString(initial)
        store(initial)
    }

    fun update(change: (WorkoutForm) -> WorkoutForm) {
        val current = form ?: return
        store(change(current))
    }

    private fun store(next: WorkoutForm) {
        form = next
        savedState[formKey] = json.encodeToString(next)
    }

    fun issues(): List<WorkoutDraftIssue> = form?.let { validate(it.toDraft(), checkDate) }.orEmpty()

    fun draft(): WorkoutDraft? = form?.toDraft()

    fun view(): WorkoutFormView? = form?.let { WorkoutFormView(it, names, issues()) }

    /** Applies the edit; an exercise missing from the lookup (created after the screen opened) reloads it first. */
    fun apply(event: WorkoutFormEvent) {
        if (event is WorkoutFormEvent.AddExercise && event.exerciseId !in metricTypes) {
            scope.launch {
                applyLookup(loadLookup())
                edit(event)
                onChange()
            }
        } else {
            edit(event)
        }
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    private fun edit(event: WorkoutFormEvent) {
        when (event) {
            is WorkoutFormEvent.NameChanged ->
                update { it.copy(name = event.text.take(WorkoutDraftBounds.NAME_MAX_LENGTH)) }

            is WorkoutFormEvent.DescriptionChanged ->
                update { it.copy(description = event.text.take(WorkoutDraftBounds.DESCRIPTION_MAX_LENGTH)) }

            is WorkoutFormEvent.GymChanged ->
                update { it.copy(gym = event.text.take(WorkoutDraftBounds.GYM_MAX_LENGTH)) }

            is WorkoutFormEvent.DurationChanged -> update {
                it.copy(duration = event.text.filter(Char::isDigit).take(WorkoutFormLimits.MINUTES_MAX_CHARS))
            }

            is WorkoutFormEvent.AddExercise -> update {
                it.withNewExercise(event.exerciseId, metricTypes[event.exerciseId] ?: ExerciseMetricType.WeightReps)
            }

            is WorkoutFormEvent.RemoveExercise -> update { it.withoutExercise(event.index) }

            is WorkoutFormEvent.MoveExercise -> update { it.movedExercise(event.from, event.to) }

            is WorkoutFormEvent.RestChanged -> update { it.withRest(event.index, event.seconds) }

            is WorkoutFormEvent.AddSet -> update { it.withAddedSet(event.index) }

            is WorkoutFormEvent.RemoveSet -> update { it.withoutSet(event.index, event.setIndex) }

            is WorkoutFormEvent.ValueChanged ->
                update { it.withField(event.index, event.setIndex, event.field, event.text) }

            is WorkoutFormEvent.TypeChanged -> update { it.withSetType(event.index, event.setIndex, event.type) }

            is WorkoutFormEvent.RpeChanged -> update { it.withRpe(event.index, event.setIndex, event.rpe) }

            is WorkoutFormEvent.NoteChanged -> update { it.withNote(event.index, event.setIndex, event.text) }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
