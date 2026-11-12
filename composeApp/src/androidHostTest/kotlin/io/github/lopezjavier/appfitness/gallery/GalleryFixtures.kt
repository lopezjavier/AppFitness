package io.github.lopezjavier.appfitness.gallery

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Fixed values shared by the gallery fixtures, so images do not change from one day to the next. */
internal object GalleryFixtures {
    val today: LocalDate = LocalDate(2026, 10, 21)
    val now: Instant = Instant.parse("2026-10-21T10:00:00Z")
    const val USER_NAME = "Alex Ejemplo"
}
