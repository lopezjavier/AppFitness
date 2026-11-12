package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.Dated
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.MetricStatus
import io.github.lopezjavier.appfitness.feature.weight.CircumferenceSheetState
import io.github.lopezjavier.appfitness.feature.weight.CircumferencesContent
import io.github.lopezjavier.appfitness.feature.weight.CircumferencesUiState
import io.github.lopezjavier.appfitness.feature.weight.GoalContent
import io.github.lopezjavier.appfitness.feature.weight.GoalUiState
import io.github.lopezjavier.appfitness.feature.weight.MeasurementForm
import io.github.lopezjavier.appfitness.feature.weight.SiteHistory
import io.github.lopezjavier.appfitness.feature.weight.WeightContent
import io.github.lopezjavier.appfitness.feature.weight.WeightHistoryScreen
import io.github.lopezjavier.appfitness.feature.weight.WeightScreen
import io.github.lopezjavier.appfitness.feature.weight.WeightSheet
import io.github.lopezjavier.appfitness.feature.weight.WeightUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.time.Instant

private val Today = GalleryFixtures.today

private fun weightRow(
    id: String,
    at: String,
    kg: Double,
    fat: Double? = null,
    source: MeasurementSource = MeasurementSource.Manual,
) = BodyMeasurement(id, Instant.parse(at), kg, source, fat)

private val Latest = weightRow("m3", "2026-10-21T08:00:00Z", 82.4, fat = 18.5)

private val Days = mapOf(
    Today to DayMeasurements(Latest, emptyList()),
    LocalDate(2026, 10, 18) to DayMeasurements(weightRow("m2", "2026-10-18T08:00:00Z", 82.9), emptyList()),
    LocalDate(2026, 10, 12) to DayMeasurements(weightRow("m1", "2026-10-12T08:00:00Z", 83.5), emptyList()),
)

private fun weightContent(
    current: BodyMeasurement? = Latest,
    days: Map<LocalDate, DayMeasurements> = Days,
    online: Boolean = true,
    sheet: WeightSheet? = null,
    composition: LatestComposition? = null,
    compositionOpen: Boolean = false,
) = WeightUiState.Content(
    WeightContent(YearMonth(2026, 10), Today, false, current, days, online, sheet, composition, compositionOpen),
)

private fun circumferences(sheet: CircumferenceSheetState? = null, empty: Boolean = false): CircumferencesUiState {
    val at = Instant.parse("2026-10-20T08:00:00Z")
    val groups = if (empty) {
        emptyList()
    } else {
        listOf(
            SiteHistory(
                CircumferenceSite.Waist,
                listOf(BodyCircumference("c1", CircumferenceSite.Waist, 84.0, at, at)),
            ),
            SiteHistory(CircumferenceSite.Hip, listOf(BodyCircumference("c2", CircumferenceSite.Hip, 98.0, at, at))),
        )
    }
    return CircumferencesUiState.Content(CircumferencesContent(groups, if (empty) null else 0.86, true, sheet))
}

private val NewDaySheet =
    WeightSheet(Today, true, emptyList(), MeasurementForm(null, "", "", null, null, false, false, false))
private val EditDaySheet = WeightSheet(
    Today,
    true,
    emptyList(),
    MeasurementForm("m3", "82,4", "18,5", null, null, true, true, false),
    dayComposition = LatestComposition(bodyFat = Dated(18.5, Instant.parse("2026-10-21T08:00:00Z"))),
)
private val HealthDaySheet = WeightSheet(
    Today,
    true,
    listOf(weightRow("hc1", "2026-10-21T06:30:00Z", 82.2, source = MeasurementSource.HealthConnect)),
    null,
    manualLocked = true,
)
private val Composition = LatestComposition(
    bodyFat = Dated(18.5, Instant.parse("2026-10-21T08:00:00Z")),
    leanMass = Dated(67.1, Instant.parse("2026-10-21T08:00:00Z")),
    water = Dated(48.0, Instant.parse("2026-10-21T08:00:00Z")),
    bone = Dated(3.2, Instant.parse("2026-10-21T08:00:00Z")),
    bmr = Dated(1810, Instant.parse("2026-10-21T08:00:00Z")),
)
private val CircumferenceAdd = CircumferenceSheetState(
    existingId = null,
    values = CircumferenceSite.entries.associateWith { "" },
    problems = emptyMap(),
    date = Today,
    canGoNextDay = false,
    canSave = false,
    canDelete = false,
    isSaving = false,
)

private fun goal(open: MetricKind?) = GoalUiState.Content(
    GoalContent(
        currentKg = 82.4,
        strategy = GoalStrategy.Maintenance,
        bmi = MetricInterpretation(MetricKind.Bmi, 25.4, MetricStatus.Overweight),
        bodyFat = MetricInterpretation(MetricKind.BodyFat, 18.5, MetricStatus.Normal),
        waistToHip = null,
        isOnline = true,
        openMetric = open,
    ),
)

private class WeightVariant(
    val state: String,
    val ui: WeightUiState,
    val circ: CircumferencesUiState = circumferences(),
    val goal: GoalUiState? = null,
    val kind: GalleryKind = GalleryKind.Screen,
    val wide: Boolean = false,
    val fontScale: Float = 1f,
    val covers: Set<String> = emptySet(),
)

@Suppress("LongMethod") // A list of fixtures.
private fun weight(): List<GalleryEntry> = listOf(
    WeightVariant("loading", WeightUiState.Loading, CircumferencesUiState.Loading),
    WeightVariant("error", WeightUiState.Error, CircumferencesUiState.Error),
    WeightVariant("empty", weightContent(current = null, days = emptyMap()), circumferences(empty = true)),
    WeightVariant("with weights and circumferences", weightContent(), goal = goal(null)),
    WeightVariant("offline", weightContent(online = false)),
    WeightVariant(
        "new weight sheet (Guardar disabled)",
        weightContent(sheet = NewDaySheet),
        kind = GalleryKind.Sheet,
        covers = setOf("WeightDaySheet"),
    ),
    WeightVariant(
        "edit weight sheet",
        weightContent(sheet = EditDaySheet),
        kind = GalleryKind.Sheet,
        covers = setOf("WeightDaySheet"),
    ),
    WeightVariant(
        "Health Connect day sheet",
        weightContent(sheet = HealthDaySheet),
        kind = GalleryKind.Sheet,
        covers = setOf("WeightDaySheet"),
    ),
    WeightVariant(
        "composition sheet",
        weightContent(composition = Composition, compositionOpen = true),
        kind = GalleryKind.Sheet,
        covers = setOf("CompositionSheet"),
    ),
    WeightVariant(
        "circumference sheet",
        weightContent(),
        circumferences(CircumferenceAdd),
        kind = GalleryKind.Sheet,
        covers = setOf("CircumferenceSheet"),
    ),
    WeightVariant(
        "metric detail sheet",
        weightContent(),
        goal = goal(MetricKind.Bmi),
        kind = GalleryKind.Sheet,
        covers = setOf("MetricDetailSheet"),
    ),
    WeightVariant("tablet width", weightContent(), goal = goal(null), wide = true),
    WeightVariant("large font (1.3)", weightContent(), goal = goal(null), fontScale = 1.3f),
).map { v ->
    GalleryEntry(
        PERFIL_AREA,
        "Peso",
        v.state,
        v.kind,
        covers = setOf("WeightRoute") + v.covers,
        wide = v.wide,
        fontScale = v.fontScale,
    ) {
        WeightScreen(v.ui, onEvent = {
        }, circumferences = v.circ, onCircumferenceEvent = {}, goal = v.goal, timeZone = TimeZone.UTC)
    }
}

private fun weightHistory(): List<GalleryEntry> {
    fun entry(state: String, ui: WeightUiState) =
        GalleryEntry(PERFIL_AREA, "Historial de peso", state, covers = setOf("WeightHistoryRoute")) {
            WeightHistoryScreen(ui, onEvent = {}, onBack = {}, timeZone = TimeZone.UTC)
        }
    return listOf(
        entry("loading", WeightUiState.Loading),
        entry("error", WeightUiState.Error),
        entry("empty", weightContent(current = null, days = emptyMap())),
        entry("with weights", weightContent()),
    )
}

internal fun perfilWeightEntries(): List<GalleryEntry> = weight() + weightHistory()
