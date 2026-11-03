package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_superset_label
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import org.jetbrains.compose.resources.stringResource

/** Width of the cyan accent on the left edge of a superset member. */
val SupersetAccentWidth = Spacing.xs

/** "Superserie A1": a superset member's label, shared by the routine editor, the live session and the block. */
@Composable
fun supersetText(label: SupersetLabel): String =
    stringResource(Res.string.builder_superset_label, label.letter.toString(), label.number)

/** The cyan accent bar of a superset member; the caller sizes its height and places it. */
@Composable
fun SupersetAccent(modifier: Modifier = Modifier) {
    Box(modifier.width(SupersetAccentWidth).background(AppTheme.colors.athleticCyan))
}
