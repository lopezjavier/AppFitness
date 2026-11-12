package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import io.github.lopezjavier.appfitness.feature.food.AddFoodContent
import io.github.lopezjavier.appfitness.feature.food.AddFoodSheet
import io.github.lopezjavier.appfitness.feature.food.AddFoodUiState
import io.github.lopezjavier.appfitness.feature.food.AlcoholMode
import io.github.lopezjavier.appfitness.feature.food.CreateFoodScreen
import io.github.lopezjavier.appfitness.feature.food.CreateFoodSection
import io.github.lopezjavier.appfitness.feature.food.CreateFoodUiState
import io.github.lopezjavier.appfitness.feature.food.EnergyWarning
import io.github.lopezjavier.appfitness.feature.food.FoodResults
import io.github.lopezjavier.appfitness.feature.food.FoodSearchScreen
import io.github.lopezjavier.appfitness.feature.food.FoodSearchTab
import io.github.lopezjavier.appfitness.feature.food.FoodSearchUiState
import io.github.lopezjavier.appfitness.feature.food.MicronutrientsState
import io.github.lopezjavier.appfitness.feature.food.PortionDraft
import io.github.lopezjavier.appfitness.feature.food.RetryState
import io.github.lopezjavier.appfitness.feature.food.TemplateSummary
import io.github.lopezjavier.appfitness.feature.food.TemplatesResults
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoErrorKind
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoStep
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoUiState

private val Chicken: Food = food("chicken", "Pechuga de pollo", kcal = 110.0)
private val Rice: Food = food("rice", "Arroz integral", kcal = 360.0)
private val Egg: Food = food("egg", "Huevo", kcal = 143.0, portions = listOf(FoodPortion("p1", "huevo", 55.0)))
private val Beer: Food = food("beer", "Cerveza", kcal = 43.0, baseUnit = "ml", isManual = true)
private val OffYogurt: Food = food("yogurt", "Yogur natural", kcal = 61.0, isOff = true)

private fun search(state: String, ui: FoodSearchUiState, vararg covers: String) =
    GalleryEntry(DIARIO_AREA, "Buscar alimento", state, covers = setOf("FoodSearchRoute") + covers) {
        FoodSearchScreen(ui, onEvent = {}, onBack = {})
    }

private fun foodSearch(): List<GalleryEntry> {
    val foods = FoodResults.Content(listOf(Chicken, Rice, Egg), manualCount = 1)
    val breakfast = GallerySlots.first()
    val comida = TemplateSummary(
        MealTemplate(
            "t1",
            "Comida de entreno",
            "lunch",
            listOf(MealTemplateItem(Chicken, 200.0, null), MealTemplateItem(Rice, 150.0, null)),
        ),
        kcal = 760.0,
    )
    return listOf(
        search("recent, loading", FoodSearchUiState(mealSlot = breakfast)),
        search(
            "recent, with foods",
            FoodSearchUiState(results = foods, favouriteIds = setOf("rice"), mealSlot = breakfast),
        ),
        search("recent, empty", FoodSearchUiState(results = FoodResults.Empty, mealSlot = breakfast)),
        search("most eaten", FoodSearchUiState(tab = FoodSearchTab.MostEaten, results = foods)),
        search("favourites, empty", FoodSearchUiState(tab = FoodSearchTab.Favourites, results = FoodResults.Empty)),
        search(
            "comidas tab, with comidas",
            FoodSearchUiState(tab = FoodSearchTab.Templates, templates = TemplatesResults.Content(listOf(comida))),
        ),
        search(
            "comidas tab, loading",
            FoodSearchUiState(tab = FoodSearchTab.Templates, templates = TemplatesResults.Loading),
        ),
        search(
            "comidas tab, empty",
            FoodSearchUiState(tab = FoodSearchTab.Templates, templates = TemplatesResults.Empty),
        ),
        search(
            "comidas tab, error",
            FoodSearchUiState(tab = FoodSearchTab.Templates, templates = TemplatesResults.Error),
        ),
        search("query with results", FoodSearchUiState(query = "pollo", results = foods)),
        search(
            "query, remote loading",
            FoodSearchUiState(query = "pollo", results = FoodResults.Content(listOf(Chicken), 1, remoteLoading = true)),
        ),
        search(
            "query, remote failed",
            FoodSearchUiState(query = "pollo", results = FoodResults.Content(listOf(Chicken), 1, remoteFailed = true)),
        ),
        search("query, no results", FoodSearchUiState(query = "xyzxyz", results = FoodResults.Empty)),
        search("error", FoodSearchUiState(results = FoodResults.Error)),
        search("offline", FoodSearchUiState(results = FoodResults.Error, isOffline = true)),
        search("barcode not found", FoodSearchUiState(results = foods, notFoundBarcode = "8410000000000")),
        search("pick mode, with foods", FoodSearchUiState(results = foods, pick = true)),
        search("pick mode, loading", FoodSearchUiState(pick = true)),
        search("pick mode, empty", FoodSearchUiState(results = FoodResults.Empty, pick = true)),
        search("view only", FoodSearchUiState(results = foods, viewOnly = true)),
    )
}

private fun addFoodContent(
    food: Food = Chicken,
    amount: String = "150",
    preview: NutritionSnapshot? = NutritionSnapshot(165.0, 31.0, 0.0, 3.6),
    offline: Boolean = false,
    submitting: Boolean = false,
    viewOnly: Boolean = false,
    energyWarning: EnergyWarning? = null,
    micro: MicronutrientsState = MicronutrientsState.Hidden,
) = AddFoodUiState.Content(
    AddFoodContent(
        food = food,
        amountText = amount,
        selectedPortionId = null,
        slots = GallerySlots,
        selectedSlotId = "lunch",
        resolvedAmount = preview?.let { 150.0 },
        preview = preview,
        quantityError = null,
        isOffline = offline,
        submitting = submitting,
        micronutrients = micro,
        viewOnly = viewOnly,
        energyWarning = energyWarning,
    ),
)

private fun addFood(): List<GalleryEntry> {
    fun entry(state: String, ui: AddFoodUiState, vararg covers: String) = GalleryEntry(
        DIARIO_AREA,
        "Añadir alimento",
        state,
        GalleryKind.Sheet,
        covers = setOf("AddFoodRoute", "AddFoodSheet") + covers,
    ) { AddFoodSheet(ui, onEvent = {}, onDismiss = {}) }
    return listOf(
        entry("loading", AddFoodUiState.Loading),
        entry("error", AddFoodUiState.Error),
        entry("normal", addFoodContent()),
        entry("manual food, Editar available", addFoodContent(Beer, "330")),
        entry("with portion", addFoodContent(Egg, "2")),
        entry("offline", addFoodContent(offline = true)),
        entry("submitting", addFoodContent(submitting = true)),
        entry("quantity not valid (Añadir disabled)", addFoodContent(amount = "", preview = null)),
        entry(
            "Open Food Facts energy check blocks",
            addFoodContent(OffYogurt, energyWarning = EnergyWarning(EnergyCheckLevel.ClearlyWrong, 61.0, 140.0, 79.0)),
        ),
        entry("view only", addFoodContent(viewOnly = true), "AddFoodContentSheet", "ViewOnlySheet"),
    )
}

private fun createFood(): List<GalleryEntry> {
    fun entry(state: String, ui: CreateFoodUiState, fontScale: Float = 1f) = GalleryEntry(
        DIARIO_AREA,
        "Crear alimento",
        state,
        covers = setOf("CreateFoodRoute", "EditFoodRoute"),
        fontScale = fontScale,
    ) {
        CreateFoodScreen(ui, onEvent = {}, onBack = {})
    }
    val filled =
        CreateFoodUiState(
            name = "Tortilla de patata",
            brand = "Casera",
            kcal = "190",
            protein = "7",
            carbs = "16",
            fat = "11",
            isDirty = true,
            canSave = true,
        )
    return listOf(
        entry("empty (Guardar disabled)", CreateFoodUiState()),
        entry("filled (Guardar enabled)", filled),
        entry(
            "validation error",
            CreateFoodUiState(
                name = "",
                kcal = "abc",
                errors = NewFoodErrors(name = NewFoodError.Required),
                isDirty = true,
            ),
        ),
        entry("with barcode", filled.copy(barcode = "8410000000000")),
        entry("with portions", filled.copy(portions = listOf(PortionDraft("ración", "150")))),
        entry("AI estimate prefill", filled.copy(aiEstimateNote = true, retryState = RetryState.None)),
        entry(
            "energy check blocks, repeat available",
            filled.copy(
                canSave = false,
                energyWarning = EnergyWarning(EnergyCheckLevel.ClearlyWrong, 190.0, 330.0, 140.0),
                retryState = RetryState.Available,
                aiEstimateNote = true,
                expandedSections = setOf(CreateFoodSection.Fats),
            ),
        ),
        entry(
            "alcohol with grados",
            filled.copy(
                baseUnit = "ml",
                alcoholMode = AlcoholMode.Grados,
                alcoholAbv = "5",
                alcoholConvertedG = "3,9",
                expandedSections = setOf(CreateFoodSection.Alcohol),
            ),
        ),
        entry("offline", filled.copy(isOffline = true, canSave = false)),
        entry("saving", filled.copy(isSaving = true)),
        entry("save failed", filled.copy(saveFailed = true)),
        entry("edit, loading", CreateFoodUiState(isEdit = true, isLoading = true)),
        entry("edit, load failed", CreateFoodUiState(isEdit = true, loadFailed = true)),
        entry("edit, unit locked", filled.copy(isEdit = true, unitLocked = true, isDirty = false, canSave = false)),
        entry("large font (1.3)", filled, fontScale = 1.3f),
    )
}

private fun photoFlow(): List<GalleryEntry> {
    fun entry(state: String, ui: FoodPhotoUiState, vararg covers: String) =
        GalleryEntry(DIARIO_AREA, "Añadir con IA", state, GalleryKind.Sheet, covers = covers.toSet()) {
            FoodPhotoFlow(ui, onEvent = {}, readPhoto = { ByteArray(0) })
        }
    return listOf(
        entry("mode sheet", FoodPhotoUiState(FoodPhotoStep.ModeSheet), "PhotoModeSheet"),
        entry("source sheet", FoodPhotoUiState(FoodPhotoStep.SourceSheet, FoodPhotoMode.Dish), "PhotoSourceSheet"),
        entry(
            "source sheet, camera failed",
            FoodPhotoUiState(
                FoodPhotoStep.SourceSheet,
                FoodPhotoMode.Dish,
                lastSource = PhotoSource.Camera,
                captureFailed = true,
            ),
            "PhotoSourceSheet",
        ),
        entry("describe the meal, empty", FoodPhotoUiState(FoodPhotoStep.TextInput, FoodPhotoMode.Text)),
        entry(
            "describe the meal, draft",
            FoodPhotoUiState(
                FoodPhotoStep.TextInput,
                FoodPhotoMode.Text,
                mealText = "Dos huevos revueltos con tostada",
            ),
        ),
        entry(
            "describe the meal, offline",
            FoodPhotoUiState(
                FoodPhotoStep.TextInput,
                FoodPhotoMode.Text,
                mealText = "Arroz con pollo",
                isOffline = true,
            ),
        ),
        entry("estimating", FoodPhotoUiState(FoodPhotoStep.Estimating, FoodPhotoMode.Dish)),
        entry(
            "error, dish not recognised",
            FoodPhotoUiState(FoodPhotoStep.Error(FoodPhotoErrorKind.NotRecognizedDish), FoodPhotoMode.Dish),
        ),
        entry(
            "error, product not recognised",
            FoodPhotoUiState(FoodPhotoStep.Error(FoodPhotoErrorKind.NotRecognizedProduct), FoodPhotoMode.Label),
        ),
        entry(
            "error, meal not recognised",
            FoodPhotoUiState(FoodPhotoStep.Error(FoodPhotoErrorKind.NotRecognizedMeal), FoodPhotoMode.Text),
        ),
        entry(
            "error, no network",
            FoodPhotoUiState(FoodPhotoStep.Error(FoodPhotoErrorKind.Network), FoodPhotoMode.Dish),
        ),
        entry(
            "error, unavailable",
            FoodPhotoUiState(FoodPhotoStep.Error(FoodPhotoErrorKind.Unavailable), FoodPhotoMode.Dish),
        ),
    )
}

internal fun diarioFoodEntries(): List<GalleryEntry> = foodSearch() + addFood() + createFood() + photoFlow()
