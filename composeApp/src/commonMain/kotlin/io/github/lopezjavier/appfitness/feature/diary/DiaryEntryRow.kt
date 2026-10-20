package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_amount
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_kcal
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

private const val NAME_MAX_LINES = 2
private const val HALF = 0.5
private const val TENTHS = 10L

/** Half-up whole number of a value that keeps 0.1 precision, in es-ES grouping. */
internal fun wholeHalfUp(value: Double): String = AppNumberFormat.integer(floor(value + HALF).toInt())

/** "220" for a whole amount, "12,5" for one with a decimal (the data keeps 0.1). */
internal fun amountText(amount: Double): String {
    val tenths = floor(amount * TENTHS + HALF).toLong()
    val whole = AppNumberFormat.integer((tenths / TENTHS).toInt())
    val decimal = tenths % TENTHS
    return if (decimal == 0L) whole else "$whole,$decimal"
}

/** A logged food: name and amount (or portion) on the left, its kcal on the right. Not interactive yet. */
@Composable
internal fun DiaryEntryRow(entry: FoodLogEntry, modifier: Modifier = Modifier) {
    val detail = entry.portionLabel
        ?: stringResource(Res.string.diary_entry_amount, amountText(entry.amount), entry.baseUnit.orEmpty()).trim()
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("diary_entry_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.foodName,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = detail, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        Text(
            text = stringResource(Res.string.diary_entry_kcal, wholeHalfUp(entry.kcal)),
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
        )
    }
}
