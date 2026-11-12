package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck
import io.github.lopezjavier.appfitness.feature.diary.EditEntryContent
import io.github.lopezjavier.appfitness.feature.diary.EditEntrySheet
import io.github.lopezjavier.appfitness.feature.diary.EditEntryUiState
import io.github.lopezjavier.appfitness.feature.diary.RemovedEntryContent
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.templates.DetailsRow
import io.github.lopezjavier.appfitness.feature.templates.OpenFood
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateContent
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateItemRow
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateSheet
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateUiState
import io.github.lopezjavier.appfitness.feature.templates.TemplateDetailsScreen
import io.github.lopezjavier.appfitness.feature.templates.TemplateDetailsUiState
import io.github.lopezjavier.appfitness.feature.templates.TemplateEditorScreen
import io.github.lopezjavier.appfitness.feature.templates.TemplateNameError

private val Chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)

private fun editEntry(): List<GalleryEntry> {
    val entry = galleryEntryRow("3", "lunch", "Pechuga de pollo", 330.0, 200.0)
    fun content(
        offline: Boolean = false,
        saving: Boolean = false,
        amount: String = "200",
        preview: NutritionSnapshot? = NutritionSnapshot(220.0, 41.0, 0.0, 4.0),
    ) = EditEntryUiState.Content(
        EditEntryContent(
            entry, Chicken, amount, null, GallerySlots, "lunch",
            preview?.let {
                200.0
            },
            preview, null, offline, saving,
        ),
    )

    fun sheet(state: String, ui: EditEntryUiState, vararg covers: String) = GalleryEntry(
        DIARIO_AREA,
        "Editar entrada",
        state,
        GalleryKind.Sheet,
        covers = setOf("EditEntryRoute", "EditEntrySheet") + covers,
    ) { EditEntrySheet(ui, onEvent = {}, onDismiss = {}) }
    return listOf(
        sheet("loading", EditEntryUiState.Loading),
        sheet("error", EditEntryUiState.Error),
        sheet("normal", content(), "EditEntryContentSheet"),
        sheet("offline", content(offline = true)),
        sheet("saving", content(saving = true)),
        sheet("quantity not valid (Guardar disabled)", content(amount = "", preview = null)),
        sheet(
            "removed food, read only",
            EditEntryUiState.Removed(RemovedEntryContent(entry, "Comida", isOffline = false)),
            "RemovedEntrySheet",
        ),
        sheet("removed food, offline", EditEntryUiState.Removed(RemovedEntryContent(entry, null, isOffline = true))),
    )
}

private fun saveTemplate(): List<GalleryEntry> {
    val rows = listOf(
        SaveTemplateItemRow("e1", "Pechuga de pollo", 200.0, null, "g", CookingMethod.Raw, canCook = true),
        SaveTemplateItemRow("e2", "Arroz integral", 180.0, null, "g", CookingMethod.Raw, canCook = true),
        SaveTemplateItemRow("e3", "Zumo de naranja", 250.0, null, "ml", CookingMethod.Raw, canCook = false),
    )

    fun content(
        name: String = "Comida de entreno",
        check: TemplateNameCheck = TemplateNameCheck.Valid,
        taken: Boolean = false,
        offline: Boolean = false,
        saving: Boolean = false,
    ) = SaveTemplateUiState.Content(
        SaveTemplateContent(name, check, taken, rows.size, offline, saving, rows, listOf("Desayuno clásico")),
    )

    fun entry(state: String, ui: SaveTemplateUiState) = GalleryEntry(
        DIARIO_AREA,
        "Guardar como comida",
        state,
        GalleryKind.Sheet,
        covers = setOf("SaveTemplateRoute", "SaveTemplateSheet"),
    ) {
        SaveTemplateSheet(ui, onEvent = {}, onDismiss = {})
    }
    return listOf(
        entry("loading", SaveTemplateUiState.Loading),
        entry("error", SaveTemplateUiState.Error),
        entry("ready (Guardar enabled)", content()),
        entry("blank name (Guardar disabled)", content(name = "", check = TemplateNameCheck.Blank)),
        entry("name taken", content(name = "Desayuno clásico", taken = true)),
        entry("name too long", content(name = "x".repeat(60), check = TemplateNameCheck.TooLong)),
        entry("offline", content(offline = true)),
        entry("saving", content(saving = true)),
    )
}

private fun templateDetails(): List<GalleryEntry> {
    fun row(index: Int, name: String, amount: String, kcal: Double, removable: Boolean = true, added: Boolean = false) =
        DetailsRow(index, "f$index", name, "g", amount, null, null, kcal, 20.0, 30.0, 8.0, removable, added)

    val baseRows =
        listOf(
            row(0, "Pechuga de pollo", "200", 330.0),
            row(1, "Arroz integral", "180", 220.0),
            row(2, "Brócoli", "100", 34.0),
        )

    fun content(
        isDirty: Boolean = false,
        offline: Boolean = false,
        nameError: TemplateNameError? = null,
        openFood: OpenFood? = null,
        rows: List<DetailsRow> = baseRows,
        addFailed: Boolean = false,
    ) = TemplateDetailsUiState.Content(
        title = "Comida de entreno",
        name = "Comida de entreno",
        nameError = nameError,
        totalKcal = 584.0,
        totalProteinG = 60.0,
        totalCarbsG = 90.0,
        totalFatG = 24.0,
        rows = rows,
        canSave = isDirty && nameError == null && !offline,
        isDirty = isDirty,
        isOffline = offline,
        busy = false,
        adding = false,
        addFailed = addFailed,
        canAdd = !offline,
        openFood = openFood,
    )

    fun details(
        state: String,
        ui: TemplateDetailsUiState,
        interact: (androidx.compose.ui.test.junit4.ComposeContentTestRule.() -> Unit)? = null,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
    ) = GalleryEntry(
        DIARIO_AREA,
        "Comida guardada",
        state,
        kind,
        covers = setOf("TemplateDetailsRoute") + covers,
        interact = interact,
    ) {
        TemplateDetailsScreen(ui, onEvent = {}, onBack = {}, onEdit = {})
    }

    fun editor(
        state: String,
        ui: TemplateDetailsUiState,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
    ) = GalleryEntry(DIARIO_AREA, "Editar comida", state, kind, covers = setOf("TemplateDetailsRoute") + covers) {
        TemplateEditorScreen(ui, onEvent = {}, onClose = {})
    }

    val open = OpenFood(baseRows[0], Chicken, micronutrients = null, canSave = true)
    return listOf(
        details("loading", TemplateDetailsUiState.Loading),
        details("error", TemplateDetailsUiState.Error),
        details("read only", content()),
        details("offline", content(offline = true)),
        details("add failed", content(addFailed = true)),
        details("food info sheet", content(), {
            onNodeWithTag("template_details_item_0").performClick()
        }, GalleryKind.Sheet, "TemplateItemInfoSheet"),
        editor("loading", TemplateDetailsUiState.Loading),
        editor("error", TemplateDetailsUiState.Error),
        editor("unchanged (Guardar disabled)", content()),
        editor("changed (Guardar enabled)", content(isDirty = true)),
        editor("name error", content(isDirty = true, nameError = TemplateNameError.Taken)),
        editor("offline", content(isDirty = true, offline = true)),
        editor("food sheet", content(isDirty = true, openFood = open), GalleryKind.Sheet, "TemplateFoodSheet"),
        editor(
            "single food (not removable)",
            content(isDirty = true, rows = listOf(row(0, "Pechuga de pollo", "200", 330.0, removable = false))),
        ),
    )
}

internal fun diarioTemplateEntries(): List<GalleryEntry> = editEntry() + saveTemplate() + templateDetails()
