package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.Dated
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.MetricStatus
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.SummarizeCircumferences
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import io.github.lopezjavier.appfitness.domain.usecase.WeeklyRate
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.feature.weight.CircumferenceSheetState
import io.github.lopezjavier.appfitness.feature.weight.CircumferenceSiteScreen
import io.github.lopezjavier.appfitness.feature.weight.CircumferenceSiteUiState
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
import io.github.lopezjavier.appfitness.feature.weight.WeightTrendUiState
import io.github.lopezjavier.appfitness.feature.weight.WeightUiState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
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

private fun circ(id: String, site: CircumferenceSite, cm: Double, day: String): BodyCircumference {
    val at = Instant.parse("${day}T08:00:00Z")
    return BodyCircumference(id, site, cm, at, at)
}

/**
 * Every site (T-324, `07` §5 "Circumferences on Peso"): Cuello no 28-day pair (points, no change); Pecho and
 * Pantorrilla raw change (no margin); Cintura and Brazo inside their margin; Cadera exactly at its 2,0 cm margin (no
 * label); Muslo one measurement.
 */
private val SeveralSites = listOf(
    SiteHistory(
        CircumferenceSite.Neck,
        listOf(
            circ("n3", CircumferenceSite.Neck, 37.5, "2026-10-20"),
            circ("n2", CircumferenceSite.Neck, 38.0, "2026-10-10"),
            circ("n1", CircumferenceSite.Neck, 37.0, "2026-09-23"),
        ),
    ),
    SiteHistory(
        CircumferenceSite.Chest,
        listOf(
            circ("p3", CircumferenceSite.Chest, 100.0, "2026-10-20"),
            circ("p2", CircumferenceSite.Chest, 98.0, "2026-09-22"),
            circ("p1", CircumferenceSite.Chest, 95.0, "2026-09-01"),
        ),
    ),
    SiteHistory(
        CircumferenceSite.Waist,
        listOf(
            circ("c5", CircumferenceSite.Waist, 83.0, "2026-10-20"),
            circ("c4", CircumferenceSite.Waist, 84.0, "2026-10-06"),
            circ("c3", CircumferenceSite.Waist, 85.0, "2026-09-22"),
            circ("c2", CircumferenceSite.Waist, 86.5, "2026-09-08"),
            circ("c1", CircumferenceSite.Waist, 88.0, "2026-08-25"),
        ),
    ),
    SiteHistory(
        CircumferenceSite.Hip,
        listOf(
            circ("h3", CircumferenceSite.Hip, 99.0, "2026-10-20"),
            circ("h2", CircumferenceSite.Hip, 101.0, "2026-09-22"),
            circ("h1", CircumferenceSite.Hip, 101.5, "2026-08-25"),
        ),
    ),
    SiteHistory(
        CircumferenceSite.Arm,
        listOf(
            circ("a3", CircumferenceSite.Arm, 36.0, "2026-10-20"),
            circ("a2", CircumferenceSite.Arm, 35.5, "2026-09-22"),
            circ("a1", CircumferenceSite.Arm, 35.0, "2026-09-01"),
        ),
    ),
    SiteHistory(CircumferenceSite.Thigh, listOf(circ("t1", CircumferenceSite.Thigh, 58.0, "2026-10-20"))),
    SiteHistory(
        CircumferenceSite.Calf,
        listOf(
            circ("k2", CircumferenceSite.Calf, 38.5, "2026-10-20"),
            circ("k1", CircumferenceSite.Calf, 38.0, "2026-09-15"),
        ),
    ),
)

/** One measurement per site: every row shows the date only and a single lit point. */
private val OnePointSites = listOf(
    SiteHistory(CircumferenceSite.Waist, listOf(circ("c1", CircumferenceSite.Waist, 84.0, "2026-10-20"))),
    SiteHistory(CircumferenceSite.Hip, listOf(circ("h1", CircumferenceSite.Hip, 98.0, "2026-10-20"))),
)

/** The waist beyond its 3,0 cm margin (−4,0 cm since 22 sept). */
private val WaistBeyondMargin = listOf(
    SiteHistory(
        CircumferenceSite.Waist,
        listOf(
            circ("c3", CircumferenceSite.Waist, 84.0, "2026-10-20"),
            circ("c2", CircumferenceSite.Waist, 86.0, "2026-10-06"),
            circ("c1", CircumferenceSite.Waist, 88.0, "2026-09-22"),
        ),
    ),
)

private fun circumferences(
    sheet: CircumferenceSheetState? = null,
    groups: List<SiteHistory> = SeveralSites,
    ratio: Double? = 0.84,
): CircumferencesUiState = CircumferencesUiState.Content(
    CircumferencesContent(
        groups,
        if (groups.isEmpty()) null else ratio,
        true,
        sheet,
        SummarizeCircumferences()(groups.flatMap { it.rows }, TimeZone.UTC),
    ),
)

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
        waistToHip = MetricInterpretation(MetricKind.WaistToHip, 0.86, MetricStatus.WhrLowRisk),
        isOnline = true,
        openMetric = open,
        sex = Sex.Male,
    ),
)

private fun trendFixture(): WeightTrendUiState {
    val weights = listOf(84.1, 83.8, 83.9, 83.4, 83.2, 83.3, 82.9, 82.8, 83.0, 82.6, 82.5, 82.7, 82.4, 82.4)
    val start = LocalDate(2026, 10, 8)
    val points = weights.mapIndexed { i, kg -> TrendPoint(start.plus(i, DateTimeUnit.DAY), kg, kg) }
    val rate = WeeklyRate(-0.45, RateTone.Favorable)
    return WeightTrendUiState.Content(
        range = WeightChartRange.Month,
        metric = BodyMetric.Weight,
        trend = TrendData(points, rate, true),
        weeklyRate = rate,
        selectedPoint = null,
    )
}

private class WeightVariant(
    val state: String,
    val ui: WeightUiState,
    val circ: CircumferencesUiState = circumferences(),
    val goal: GoalUiState? = null,
    val trend: WeightTrendUiState? = null,
    val kind: GalleryKind = GalleryKind.Screen,
    val wide: Boolean = false,
    val tall: Boolean = false,
    val fontScale: Float = 1f,
    val covers: Set<String> = emptySet(),
)

@Suppress("LongMethod") // A list of fixtures.
private fun weight(): List<GalleryEntry> = listOf(
    WeightVariant("loading", WeightUiState.Loading, CircumferencesUiState.Loading),
    WeightVariant("error", WeightUiState.Error, CircumferencesUiState.Error),
    WeightVariant("empty", weightContent(current = null, days = emptyMap()), circumferences(groups = emptyList())),
    WeightVariant("with weights and circumferences", weightContent(), goal = goal(null), trend = trendFixture()),
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
    WeightVariant(
        "circumferences, several sites",
        weightContent(),
        goal = goal(null),
        trend = trendFixture(),
        tall = true,
    ),
    WeightVariant(
        "circumferences, one measurement per site",
        weightContent(),
        circumferences(groups = OnePointSites, ratio = 0.86),
        goal = goal(null),
        trend = trendFixture(),
        tall = true,
    ),
    WeightVariant(
        "circumferences, none yet",
        weightContent(),
        circumferences(groups = emptyList()),
        goal = goal(null),
        trend = trendFixture(),
        tall = true,
    ),
    WeightVariant(
        "circumferences, large font (1.3)",
        weightContent(),
        goal = goal(null),
        trend = trendFixture(),
        tall = true,
        fontScale = 1.3f,
    ),
    WeightVariant(
        "circumferences, large font (2.0)",
        weightContent(),
        goal = goal(null),
        trend = trendFixture(),
        tall = true,
        fontScale = 2f,
    ),
    WeightVariant("tablet width", weightContent(), goal = goal(null), trend = trendFixture(), wide = true),
    WeightVariant("large font (1.3)", weightContent(), goal = goal(null), trend = trendFixture(), fontScale = 1.3f),
).map { v ->
    GalleryEntry(
        PERFIL_AREA,
        "Peso",
        v.state,
        v.kind,
        covers = setOf("WeightRoute") + v.covers,
        wide = v.wide,
        tall = v.tall,
        fontScale = v.fontScale,
    ) {
        WeightScreen(v.ui, onEvent = {
        }, circumferences = v.circ, onCircumferenceEvent = {}, goal = v.goal, trend = v.trend, timeZone = TimeZone.UTC)
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

private class SiteVariant(
    val state: String,
    val site: CircumferenceSite,
    val ui: CircumferencesUiState = circumferences(),
    val kind: GalleryKind = GalleryKind.Screen,
    val wide: Boolean = false,
    val fontScale: Float = 1f,
    val covers: Set<String> = emptySet(),
)

private val EditWaistSheet = CircumferenceSheetState(
    existingId = "c5",
    values = mapOf(CircumferenceSite.Waist to "83,0"),
    problems = emptyMap(),
    date = LocalDate(2026, 10, 20),
    canGoNextDay = true,
    canSave = false,
    canDelete = true,
    isSaving = false,
)

/** T-324 proposal: the history of one site, opened from its row on Peso. */
private fun circumferenceSite(): List<GalleryEntry> = listOf(
    SiteVariant("Cintura, inside the margin", CircumferenceSite.Waist),
    SiteVariant("Cintura, beyond the margin", CircumferenceSite.Waist, circumferences(groups = WaistBeyondMargin)),
    SiteVariant("Cadera, at its margin (no label)", CircumferenceSite.Hip),
    SiteVariant("Brazo, inside its margin", CircumferenceSite.Arm),
    SiteVariant("Pecho, change without a margin", CircumferenceSite.Chest),
    SiteVariant("Cuello, no comparable measurement yet", CircumferenceSite.Neck),
    SiteVariant("Muslo, one measurement", CircumferenceSite.Thigh),
    SiteVariant(
        "edit sheet over the history",
        CircumferenceSite.Waist,
        circumferences(EditWaistSheet),
        kind = GalleryKind.Sheet,
        covers = setOf("CircumferenceSheet"),
    ),
    SiteVariant("loading", CircumferenceSite.Waist, CircumferencesUiState.Loading),
    SiteVariant("error", CircumferenceSite.Waist, CircumferencesUiState.Error),
    SiteVariant("large font (1.3)", CircumferenceSite.Waist, fontScale = 1.3f),
    SiteVariant("large font (2.0)", CircumferenceSite.Waist, fontScale = 2f),
    SiteVariant("tablet width", CircumferenceSite.Waist, wide = true),
).map { v ->
    GalleryEntry(
        PERFIL_AREA,
        "Circunferencia",
        v.state,
        v.kind,
        covers = setOf("CircumferenceSiteRoute") + v.covers,
        wide = v.wide,
        fontScale = v.fontScale,
    ) {
        CircumferenceSiteScreen(
            site = v.site,
            state = siteStateOf(v.ui, v.site),
            today = Today,
            onEvent = {},
            onBack = {},
            timeZone = TimeZone.UTC,
        )
    }
}

/** The site screen's state for [site], from the section state (compile-only bridge; T-325 deletes the gallery). */
private val siteStateOf: (CircumferencesUiState, CircumferenceSite) -> CircumferenceSiteUiState = { ui, site ->
    when (ui) {
        CircumferencesUiState.Loading -> CircumferenceSiteUiState.Loading

        CircumferencesUiState.Error -> CircumferenceSiteUiState.Error

        is CircumferencesUiState.Content -> ui.content.sites.firstOrNull { it.site == site }?.let { summary ->
            CircumferenceSiteUiState.Content(
                summary = summary,
                rows = ui.content.groups.firstOrNull { it.site == site }?.rows.orEmpty(),
                isOnline = ui.content.isOnline,
                sheet = ui.content.sheet,
            )
        } ?: CircumferenceSiteUiState.Gone
    }
}

internal fun perfilWeightEntries(): List<GalleryEntry> = weight() + weightHistory() + circumferenceSite()
