package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.feature.meals.MealNameSheet
import io.github.lopezjavier.appfitness.feature.meals.MealSlotRowUi
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsSaveError
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsScreen
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsUiState
import io.github.lopezjavier.appfitness.feature.meals.defaults.AssignSheetUi
import io.github.lopezjavier.appfitness.feature.meals.defaults.AssignmentUi
import io.github.lopezjavier.appfitness.feature.meals.defaults.DefaultComidaOptionUi
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsCardUi
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsSaveError
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsScreen
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsUiState

private fun mealSlots(): List<GalleryEntry> {
    fun row(
        key: String,
        label: String,
        up: Boolean = true,
        down: Boolean = true,
        inUse: Boolean = false,
        last: Boolean = false,
    ) = MealSlotRowUi(
        key,
        label,
        up,
        down,
        isNew = false,
        inUse = inUse,
        isLastMeal = last,
        canDelete = !inUse && !last,
    )

    val rows = listOf(
        row("breakfast", "Desayuno", up = false),
        row("lunch", "Comida", inUse = true),
        row("snack", "Merienda"),
        row("dinner", "Cena", down = false),
    )

    fun entry(state: String, ui: MealSlotsUiState, kind: GalleryKind = GalleryKind.Screen, vararg covers: String) =
        GalleryEntry(PERFIL_AREA, "Comidas del día", state, kind, covers = setOf("MealSlotsRoute") + covers) {
            MealSlotsScreen(ui, onEvent = {})
        }
    return listOf(
        entry("loading", MealSlotsUiState.Loading),
        entry("load error", MealSlotsUiState.LoadError),
        entry("unchanged (Guardar disabled)", MealSlotsUiState.Editing(rows)),
        entry(
            "changed (Guardar enabled)",
            MealSlotsUiState.Editing(rows, isDirty = true, canSave = true, canNavigateToDefaults = false),
        ),
        entry("offline", MealSlotsUiState.Editing(rows, isOnline = false, isDirty = true)),
        entry("saving", MealSlotsUiState.Editing(rows, isSaving = true, isDirty = true)),
        entry(
            "save rejected",
            MealSlotsUiState.Editing(rows, isDirty = true, canSave = true, saveError = MealSlotsSaveError.Rejected),
        ),
        entry(
            "one meal left",
            MealSlotsUiState.Editing(listOf(row("lunch", "Comida", up = false, down = false, last = true))),
        ),
        entry(
            "add meal sheet",
            MealSlotsUiState.Editing(rows, sheet = MealNameSheet.Create),
            GalleryKind.Sheet,
            "MealNameSheetHost",
        ),
        entry(
            "rename sheet",
            MealSlotsUiState.Editing(rows, sheet = MealNameSheet.Rename("snack", "Merienda")),
            GalleryKind.Sheet,
            "MealNameSheetHost",
        ),
    )
}

private fun mealDefaults(): List<GalleryEntry> {
    val assignment = AssignmentUi("t1", "Avena con fruta", 3, 420.0, setOf(1, 2, 3, 4, 5))
    val meals = listOf(
        MealDefaultsCardUi("breakfast", "Desayuno", listOf(assignment), canAdd = true),
        MealDefaultsCardUi("lunch", "Comida", emptyList(), canAdd = true),
        MealDefaultsCardUi("dinner", "Cena", emptyList(), canAdd = true),
    )
    val comidas = listOf(
        DefaultComidaOptionUi("t1", "Avena con fruta", 3, 420.0),
        DefaultComidaOptionUi("t2", "Comida de entreno", 2, 760.0),
    )

    fun content(
        isDirty: Boolean = false,
        online: Boolean = true,
        saving: Boolean = false,
        saveError: MealDefaultsSaveError? = null,
        hasComidas: Boolean = true,
        picker: String? = null,
        sheet: AssignSheetUi? = null,
    ) = MealDefaultsUiState.Content(
        meals = meals,
        comidas = if (hasComidas) comidas else emptyList(),
        hasSavedComidas = hasComidas,
        isOnline = online,
        isSaving = saving,
        isDirty = isDirty,
        canSave = isDirty && online && !saving,
        saved = false,
        saveError = saveError,
        picker = picker,
        sheet = sheet,
    )

    fun entry(state: String, ui: MealDefaultsUiState, kind: GalleryKind = GalleryKind.Screen, vararg covers: String) =
        GalleryEntry(PERFIL_AREA, "Comidas por defecto", state, kind, covers = setOf("MealDefaultsRoute") + covers) {
            MealDefaultsScreen(ui, onEvent = {})
        }
    val sheet =
        AssignSheetUi(
            "lunch",
            "Comida",
            "t2",
            "Comida de entreno",
            setOf(1, 3),
            mapOf(5 to "Menú del viernes"),
            canSave = true,
        )
    return listOf(
        entry("loading", MealDefaultsUiState.Loading),
        entry("load error", MealDefaultsUiState.LoadError),
        entry("unchanged (Guardar disabled)", content()),
        entry("changed (Guardar enabled)", content(isDirty = true)),
        entry("no saved comidas", content(hasComidas = false)),
        entry("offline", content(isDirty = true, online = false)),
        entry("saving", content(isDirty = true, saving = true)),
        entry("save failed", content(isDirty = true, saveError = MealDefaultsSaveError.Failed)),
        entry("comida picker", content(picker = "lunch"), GalleryKind.Sheet, "DefaultComidaPickerSheet"),
        entry("assign days sheet", content(sheet = sheet), GalleryKind.Sheet, "AssignDefaultSheet"),
    )
}

internal fun perfilMealsEntries(): List<GalleryEntry> = mealSlots() + mealDefaults()
