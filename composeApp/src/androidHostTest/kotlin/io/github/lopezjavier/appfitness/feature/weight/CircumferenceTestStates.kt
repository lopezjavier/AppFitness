package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.usecase.SummarizeCircumferences
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** A circumference row measured at [at] (ISO instant, UTC). */
internal fun circumferenceRow(id: String, site: CircumferenceSite, cm: Double, at: String = "2026-10-18T08:30:00Z") =
    BodyCircumference(id, site, cm, Instant.parse(at), Instant.parse(at))

/** The section state the view model builds from [rows] (UTC), for screens rendered without a view model. */
internal fun circumferenceSectionState(rows: List<BodyCircumference>, isOnline: Boolean = true): CircumferencesUiState {
    val ordered = rows.sortedByDescending { it.measuredAt }
    return CircumferencesUiState.Content(
        CircumferencesContent(
            groups = CircumferenceSite.entries.mapNotNull { site ->
                ordered.filter { it.site == site }.takeIf { it.isNotEmpty() }?.let { SiteHistory(site, it) }
            },
            ratio = WaistHipRatio()(ordered),
            isOnline = isOnline,
            sheet = null,
            sites = SummarizeCircumferences()(ordered, TimeZone.UTC),
        ),
    )
}

/** The site screen state the view model builds for [site] from [rows] (UTC). */
internal fun circumferenceSiteState(
    site: CircumferenceSite,
    rows: List<BodyCircumference>,
    isOnline: Boolean = true,
): CircumferenceSiteUiState.Content = CircumferenceSiteUiState.Content(
    summary = SummarizeCircumferences()(rows, TimeZone.UTC).first { it.site == site },
    rows = rows.filter { it.site == site }.sortedByDescending { it.measuredAt },
    isOnline = isOnline,
    sheet = null,
)
