package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_copied_to
import io.github.lopezjavier.appfitness.composeapp.resources.diary_undo
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private const val ID_SEPARATOR = ","

/**
 * The open copy sheet's draft: the ids to copy, the destination meal and the destination day. Each part is saveable
 * (ADR-40), so the sheet survives recreation. The sheet is closed while there are no [entryIds].
 */
internal class CopyDraft(
    private val ids: MutableState<String>,
    private val slot: MutableState<String>,
    private val day: MutableState<String>,
) {
    val isOpen: Boolean get() = ids.value.isNotEmpty()
    val entryIds: List<String> get() = ids.value.split(ID_SEPARATOR).filter { it.isNotEmpty() }
    val slotId: String get() = slot.value
    val date: LocalDate get() = LocalDate.parse(day.value)

    fun open(entryIds: List<String>, slotId: String, date: LocalDate) {
        ids.value = entryIds.joinToString(ID_SEPARATOR)
        slot.value = slotId
        day.value = date.toString()
    }

    fun selectSlot(slotId: String) {
        slot.value = slotId
    }

    fun selectDate(date: LocalDate) {
        day.value = date.toString()
    }

    fun close() {
        ids.value = ""
    }
}

@Composable
internal fun rememberCopyDraft(): CopyDraft {
    val ids = rememberSaveable { mutableStateOf("") }
    val slot = rememberSaveable { mutableStateOf("") }
    val day = rememberSaveable { mutableStateOf("") }
    return remember { CopyDraft(ids, slot, day) }
}

/** The copy sheet for [draft] while it is open: "Copiar" sends [DiaryEvent.ConfirmCopy] and closes it. */
@Composable
internal fun CopyEntriesHost(draft: CopyDraft, content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
    if (!draft.isOpen) return
    CopyEntriesSheet(
        slots = content.meals.map { it.slot },
        selectedSlotId = draft.slotId,
        date = draft.date,
        today = content.today,
        offline = content.isOffline,
        onSlotSelect = draft::selectSlot,
        onDayChange = draft::selectDate,
        onConfirm = {
            onEvent(DiaryEvent.ConfirmCopy(draft.entryIds, draft.slotId, draft.date))
            draft.close()
        },
        onDismiss = draft::close,
    )
}

/** A finished copy or repeat that the snackbar announces; compared by identity so equal copies each show it. */
internal class CopiedNotice(val target: CopyTarget)

/** Shows "Copiado a Cena, mañana" with "Deshacer" for [notice]; the action sends [DiaryEvent.UndoCopy]. */
@Composable
internal fun CopiedSnackbarEffect(
    notice: CopiedNotice?,
    content: DiaryContent?,
    snackbarHost: SnackbarHostState,
    onEvent: (DiaryEvent) -> Unit,
) {
    if (notice == null || content == null) return
    val slotLabel = content.meals.firstOrNull { it.slot.id == notice.target.slotId }?.slot?.label.orEmpty()
    val message = stringResource(
        Res.string.diary_copied_to,
        slotLabel,
        dayText(notice.target.date, content.today).lowercase(),
    )
    val undo = stringResource(Res.string.diary_undo)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(notice) {
        val shown = snackbarHost.showSnackbar(message, undo, duration = SnackbarDuration.Long)
        if (shown == SnackbarResult.ActionPerformed) currentOnEvent(DiaryEvent.UndoCopy)
    }
}
