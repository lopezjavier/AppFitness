package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_sheet_title_create
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_sheet_title_rename
import io.github.lopezjavier.appfitness.designsystem.NameSheet
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import org.jetbrains.compose.resources.stringResource

/**
 * Create / rename folder sheet: the shared `NameSheet` (R-15). The duplicate check against [folders] is client side;
 * closing it (X, back, scrim) discards the text, and offline "Guardar" stays disabled.
 */
@Composable
internal fun FolderSheet(
    sheet: FolderSheetState,
    folders: List<RoutineFolder>,
    saving: Boolean,
    isOnline: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
) {
    val initial = folders.firstOrNull { it.id == sheet.folderId }?.name.orEmpty()
    NameSheet(
        title = stringResource(
            if (sheet.mode == FolderSheetMode.Create) {
                Res.string.routines_folder_sheet_title_create
            } else {
                Res.string.routines_folder_sheet_title_rename
            },
        ),
        label = stringResource(Res.string.routines_folder_name_label),
        itemLabel = "una carpeta",
        existingNames = folders.map { it.name },
        onSave = { onEvent(RoutinesEvent.SaveFolder(it)) },
        onDismiss = { onEvent(RoutinesEvent.DismissFolderSheet) },
        initialName = initial,
        saving = saving,
        maxLength = RoutineBounds.NAME_MAX_LENGTH,
        testTagPrefix = "folder_sheet",
        canSubmit = isOnline,
        commitHint = if (isOnline) null else offlineHint("guardar"),
    )
}
