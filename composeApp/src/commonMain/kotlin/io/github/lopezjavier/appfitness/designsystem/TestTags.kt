package io.github.lopezjavier.appfitness.designsystem

/** "catalog_set_row_2" and "weight" give "catalog_set_row_2_weight"; no prefix gives no tag. */
internal fun childTestTag(prefix: String?, part: String): String? = prefix?.let { "${it}_$part" }
