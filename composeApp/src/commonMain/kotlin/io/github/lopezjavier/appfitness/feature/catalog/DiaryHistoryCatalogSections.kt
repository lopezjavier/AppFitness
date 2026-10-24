package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_diary_history_row
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_month_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_empty
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_week_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_split_title
import io.github.lopezjavier.appfitness.designsystem.DiaryHistoryRow
import io.github.lopezjavier.appfitness.designsystem.HistoryTargets
import io.github.lopezjavier.appfitness.designsystem.HistoryTotals
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.MuscleSplitBar
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

private const val CATALOG_SPLIT_MAJOR = 0.6f
private const val CATALOG_SPLIT_MINOR = 0.4f
private val CatalogToday = LocalDate(2026, 10, 21)
private val CatalogMarked = setOf(LocalDate(2026, 10, 3), LocalDate(2026, 10, 18), LocalDate(2026, 10, 20))
private val CatalogTotals = HistoryTotals(kcal = 1840.0, proteinG = 142.0, carbsG = 210.0, fatG = 48.0)
private val CatalogTargets = HistoryTargets(kcal = 2400, proteinG = 180, carbsG = 270, fatG = 67)

/** Catalog sections of the diary history components: the month calendar and the day / period row. */
internal fun LazyListScope.diaryHistoryItems() {
    item { MonthCalendarSection() }
    item { DiaryHistoryRowSection() }
    item { MuscleSplitBarSection() }
}

@Composable
private fun MuscleSplitBarSection() {
    CatalogSection(
        stringResource(Res.string.session_detail_split_title),
        "catalog_section_muscle_split_bar",
    ) {
        Column(Modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            MuscleSplitBar("Pecho", "12 series · 60 %", CATALOG_SPLIT_MAJOR, "Pecho: 12 series, 60 %")
            MuscleSplitBar("Espalda", "8 series · 40 %", CATALOG_SPLIT_MINOR, "Espalda: 8 series, 40 %")
        }
    }
}

@Composable
private fun MonthCalendarSection() {
    var selected by rememberSaveable { mutableStateOf<String?>(CatalogMarked.first().toString()) }
    CatalogSection(
        stringResource(Res.string.catalog_section_month_calendar),
        "catalog_section_month_calendar",
    ) {
        MonthCalendarGrid(
            month = YearMonth(CatalogToday.year, CatalogToday.month),
            markedDays = CatalogMarked,
            selected = selected?.let(LocalDate::parse),
            today = CatalogToday,
            onSelect = { selected = it.toString() },
            onPrevMonth = {},
            onNextMonth = {},
            canGoNext = false,
        )
    }
}

@Composable
private fun DiaryHistoryRowSection() {
    CatalogSection(
        stringResource(Res.string.catalog_section_diary_history_row),
        "catalog_section_diary_history_row",
    ) {
        Column(Modifier, verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            val empty = stringResource(Res.string.diary_history_empty)
            DiaryHistoryRow(stringResource(Res.string.diary_history_week_title), CatalogTotals, CatalogTargets, empty)
            DiaryHistoryRow("18 oct", CatalogTotals, CatalogTargets, empty, onClick = {})
            DiaryHistoryRow("19 oct", null, CatalogTargets, empty, onClick = {})
        }
    }
}
