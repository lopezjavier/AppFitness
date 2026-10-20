package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.usecase.CopyEntries
import io.github.lopezjavier.appfitness.domain.usecase.RepeatYesterdayMeal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private const val KEY_SELECTED = "diary_selected"
private const val ID_SEPARATOR = ","

/**
 * The Diary's selection mode, copy and "repeat yesterday" events. The selected ids live in [savedState] (ADR-40). The
 * Diary supplies what is on screen through [visibleEntries], [mealEntries], [yesterdayEntries] and [visibleDate].
 */
@Suppress("LongParameterList") // Lookups into the Diary's state.
internal class DiaryCopyActions(
    savedState: SavedStateHandle,
    private val writes: DiaryEntryWrites,
    private val send: (DiaryEffect) -> Unit,
    private val scope: CoroutineScope,
    private val visibleEntries: () -> List<FoodLogEntry>,
    private val mealEntries: (String) -> List<FoodLogEntry>,
    private val yesterdayEntries: () -> List<FoodLogEntry>?,
    private val visibleDate: () -> LocalDate,
) {
    private val state = savedState
    private val copyEntries = CopyEntries()
    private val repeatYesterdayMeal = RepeatYesterdayMeal(copyEntries)

    val selected = MutableStateFlow(
        state.get<String>(KEY_SELECTED)?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
    )

    fun select(ids: Set<String>) {
        selected.value = ids
        state[KEY_SELECTED] = ids.joinToString(ID_SEPARATOR)
    }

    /** Handles the selection and copy events; ignores every other event. */
    fun onEvent(event: DiaryEvent) {
        when (event) {
            is DiaryEvent.EnterSelection -> select(selected.value + event.entryId)

            is DiaryEvent.SelectMeal -> select(selected.value + mealEntries(event.slotId).map { it.id })

            is DiaryEvent.ToggleSelected -> select(
                if (event.entryId in selected.value) selected.value - event.entryId else selected.value + event.entryId,
            )

            DiaryEvent.ClearSelection -> select(emptySet())

            DiaryEvent.CopySelected -> openCopy(visibleEntries().filter { it.id in selected.value })

            is DiaryEvent.CopyMeal -> openCopy(mealEntries(event.slotId))

            is DiaryEvent.ConfirmCopy -> confirmCopy(event)

            is DiaryEvent.RepeatYesterday -> {
                val day = visibleDate()
                val copies = repeatYesterdayMeal(yesterdayEntries().orEmpty(), event.slotId, day)
                scope.launch { writes.copy(copies, UndoKind.Repeat, CopyTarget(event.slotId, day)) }
            }

            DiaryEvent.UndoCopy -> scope.launch { writes.undoCopy() }

            else -> Unit
        }
    }

    private fun confirmCopy(event: DiaryEvent.ConfirmCopy) {
        val entries = visibleEntries().filter { it.id in event.entryIds }
        val copies = copyEntries(entries, event.slotId, event.date)
        scope.launch {
            if (writes.copy(copies, UndoKind.Copy, CopyTarget(event.slotId, event.date))) select(emptySet())
        }
    }

    private fun openCopy(entries: List<FoodLogEntry>) {
        if (entries.isNotEmpty()) send(DiaryEffect.OpenCopy(entries.map { it.id }, visibleDate()))
    }
}
