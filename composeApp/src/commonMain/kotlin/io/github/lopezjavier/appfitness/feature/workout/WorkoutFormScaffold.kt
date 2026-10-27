package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_date
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_date_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_date_static_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_date_value
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_load_error
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextFieldDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private val SkeletonRowHeight = Spacing.xl * 5

/** "mié, 21 oct 2026": the weekday, day and month of [date] and its year. */
@Composable
internal fun fullDateText(date: LocalDate): String = stringResource(
    Res.string.workout_form_date_value,
    DateLabel.Other(date.dayOfWeek, date.day, date.month).text(),
    date.year,
)

/** The labelled date of the workout. With [onClick] it is a field that opens the date dialog; without, plain text. */
@Composable
internal fun FormDateField(date: LocalDate, tag: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val text = fullDateText(date)
    val label = stringResource(Res.string.workout_form_date)
    val description = stringResource(
        if (onClick != null) Res.string.workout_form_date_cd else Res.string.workout_form_date_static_cd,
        text,
    )
    val colors = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTextFieldDefaults.LabelSpacing)) {
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = AppTextFieldDefaults.MinHeight)
                .clip(AppShapes.base)
                .background(colors.surfaceOverlay)
                .border(AppBorder.thin, colors.borderStrong, AppShapes.base)
                .let {
                    if (onClick != null) {
                        it.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        it
                    }
                }
                .padding(horizontal = AppTextFieldDefaults.HorizontalPadding)
                .semantics { contentDescription = description }
                .testTag(tag),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = AppTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
internal fun WorkoutFormLoading(tagPrefix: String) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("${tagPrefix}_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.lg) }
    }
}

@Composable
internal fun WorkoutFormError(tagPrefix: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.workout_form_load_error),
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            testTagPrefix = "${tagPrefix}_error",
        )
    }
}
