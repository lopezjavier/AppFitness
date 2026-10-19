package io.github.lopezjavier.appfitness.feature.biometrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_light_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_light_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sedentary_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sedentary_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_very_high_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_very_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.month_1
import io.github.lopezjavier.appfitness.composeapp.resources.month_10
import io.github.lopezjavier.appfitness.composeapp.resources.month_11
import io.github.lopezjavier.appfitness.composeapp.resources.month_12
import io.github.lopezjavier.appfitness.composeapp.resources.month_2
import io.github.lopezjavier.appfitness.composeapp.resources.month_3
import io.github.lopezjavier.appfitness.composeapp.resources.month_4
import io.github.lopezjavier.appfitness.composeapp.resources.month_5
import io.github.lopezjavier.appfitness.composeapp.resources.month_6
import io.github.lopezjavier.appfitness.composeapp.resources.month_7
import io.github.lopezjavier.appfitness.composeapp.resources.month_8
import io.github.lopezjavier.appfitness.composeapp.resources.month_9
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_decimal_separator
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_sex_female
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_sex_male
import io.github.lopezjavier.appfitness.composeapp.resources.unit_cm
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_day_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_height_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_month_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_decimal_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_kg_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_year_cd
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WheelPicker
import io.github.lopezjavier.appfitness.designsystem.WheelPickerDefaults
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val MONTH_COUNT = 12
private const val DECIMAL_MAX = 9
private const val TENTHS_PER_KG = 10
private const val DAY_WHEEL_WEIGHT = 1f
private const val MONTH_WHEEL_WEIGHT = 2f
private const val YEAR_WHEEL_WEIGHT = 1.4f

/*
 * Biometric pickers shared by Onboarding and Profile. Every tag is `<tagPrefix>_<name>` so each screen keeps its
 * own tag namespace (`onboarding_sex_male`, `profile_sex_male`, ...).
 */

@Composable
internal fun SexSelector(sex: Sex?, onSelect: (Sex) -> Unit, tagPrefix: String, modifier: Modifier = Modifier) {
    val options =
        listOf(
            AppOption(stringResource(Res.string.onboarding_sex_male), "${tagPrefix}_sex_male"),
            AppOption(stringResource(Res.string.onboarding_sex_female), "${tagPrefix}_sex_female"),
        )
    SegmentedSelector(
        options = options,
        selectedIndex = sex?.ordinal,
        onSelect = { onSelect(Sex.entries[it]) },
        modifier = modifier,
    )
}

/**
 * Day, month and year wheels; the composed date is always inside the allowed range. [onCentredChange] reports the
 * date composed from the items currently centred, even mid-scroll; it never feeds the wheels.
 */
@Composable
internal fun BirthDateWheels(
    date: LocalDate,
    today: LocalDate,
    onChange: (LocalDate) -> Unit,
    tagPrefix: String,
    onCentredChange: (LocalDate) -> Unit = {},
) {
    val range = BiometricBounds.birthDateRange(today)
    val monthNames = monthNames()
    var centred by remember(date) { mutableStateOf(Triple(date.year, date.month.number, date.day)) }
    fun centre(next: Triple<Int, Int, Int>) {
        centred = next
        onCentredChange(BiometricBounds.composeBirthDate(next.first, next.second, next.third, today))
    }
    fun compose(year: Int = date.year, month: Int = date.month.number, day: Int = date.day) =
        onChange(BiometricBounds.composeBirthDate(year, month, day, today))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        WheelPicker(
            range = 1..BiometricBounds.daysIn(date.year, date.month.number),
            value = date.day,
            onValueChange = { compose(day = it) },
            onCentredChange = { centre(centred.copy(third = it)) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_day_cd),
            stateDescription = { it.toString() },
            testTagPrefix = "${tagPrefix}_birth_day_picker",
            modifier = Modifier.weight(DAY_WHEEL_WEIGHT),
        )
        WheelPicker(
            range = 1..MONTH_COUNT,
            value = date.month.number,
            onValueChange = { compose(month = it) },
            onCentredChange = { centre(centred.copy(second = it)) },
            label = { monthNames[it - 1] },
            contentDescription = stringResource(Res.string.wheel_month_cd),
            stateDescription = { monthNames[it - 1] },
            testTagPrefix = "${tagPrefix}_birth_month_picker",
            modifier = Modifier.weight(MONTH_WHEEL_WEIGHT),
        )
        WheelPicker(
            range = range.start.year..range.endInclusive.year,
            value = date.year,
            onValueChange = { compose(year = it) },
            onCentredChange = { centre(centred.copy(first = it)) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_year_cd),
            stateDescription = { it.toString() },
            testTagPrefix = "${tagPrefix}_birth_year_picker",
            modifier = Modifier.weight(YEAR_WHEEL_WEIGHT),
        )
    }
}

@Composable
private fun monthNames(): List<String> = listOf(
    stringResource(Res.string.month_1),
    stringResource(Res.string.month_2),
    stringResource(Res.string.month_3),
    stringResource(Res.string.month_4),
    stringResource(Res.string.month_5),
    stringResource(Res.string.month_6),
    stringResource(Res.string.month_7),
    stringResource(Res.string.month_8),
    stringResource(Res.string.month_9),
    stringResource(Res.string.month_10),
    stringResource(Res.string.month_11),
    stringResource(Res.string.month_12),
)

@Composable
internal fun HeightWheel(
    heightCm: Int,
    onChange: (Int) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    onCentredChange: (Int) -> Unit = {},
) {
    val cm = stringResource(Res.string.unit_cm)
    WheelPicker(
        range = BiometricBounds.HEIGHT_CM,
        value = heightCm,
        onValueChange = onChange,
        label = { it.toString() },
        contentDescription = stringResource(Res.string.wheel_height_cd),
        stateDescription = { "$it $cm" },
        testTagPrefix = "${tagPrefix}_height_picker",
        modifier = modifier,
        onCentredChange = onCentredChange,
    )
}

/**
 * Kilos and tenths wheels (20,0 to 400,0 kg) around a decimal separator. [weightTenths] is the weight in tenths of a
 * kilogram. [onCentredChange] reports the weight composed from the items currently centred, even mid-scroll; it
 * never feeds the wheels.
 */
@Composable
internal fun WeightWheels(
    weightTenths: Int,
    onChange: (Int) -> Unit,
    tagPrefix: String,
    onCentredChange: (Int) -> Unit = {},
) {
    val kilos = weightTenths / TENTHS_PER_KG
    val decimal = weightTenths % TENTHS_PER_KG
    val maxKilos = BiometricBounds.WEIGHT_TENTHS.last / TENTHS_PER_KG
    val kg = stringResource(Res.string.unit_kg)
    val separator = stringResource(Res.string.onboarding_decimal_separator)
    var centred by remember(weightTenths) { mutableStateOf(kilos to decimal) }
    fun centre(next: Pair<Int, Int>) {
        centred = next
        onCentredChange(BiometricBounds.clampWeightTenths(next.first * TENTHS_PER_KG + next.second))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            range = (BiometricBounds.WEIGHT_TENTHS.first / TENTHS_PER_KG)..maxKilos,
            value = kilos,
            onValueChange = { onChange(it * TENTHS_PER_KG + decimal) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_weight_kg_cd),
            stateDescription = { "$it $kg" },
            testTagPrefix = "${tagPrefix}_weight_kg_picker",
            modifier = Modifier.width(WheelPickerDefaults.MinWidth),
            onCentredChange = { centre(it to centred.second) },
        )
        Text(
            text = separator,
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
        )
        WheelPicker(
            range = 0..(if (kilos == maxKilos) 0 else DECIMAL_MAX),
            value = decimal,
            onValueChange = { onChange(kilos * TENTHS_PER_KG + it) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_weight_decimal_cd),
            stateDescription = { "$separator$it" },
            testTagPrefix = "${tagPrefix}_weight_decimal_picker",
            modifier = Modifier.width(WheelPickerDefaults.CompactWidth),
            onCentredChange = { centre(centred.first to it) },
        )
    }
}

private class ActivityOption(
    val level: ActivityLevel,
    val title: StringResource,
    val description: StringResource,
    val tagName: String,
)

private val ActivityOptions =
    listOf(
        ActivityOption(
            ActivityLevel.Sedentary,
            Res.string.activity_sedentary_title,
            Res.string.activity_sedentary_description,
            "sedentary",
        ),
        ActivityOption(
            ActivityLevel.Light,
            Res.string.activity_light_title,
            Res.string.activity_light_description,
            "light",
        ),
        ActivityOption(
            ActivityLevel.Moderate,
            Res.string.activity_moderate_title,
            Res.string.activity_moderate_description,
            "moderate",
        ),
        ActivityOption(
            ActivityLevel.High,
            Res.string.activity_high_title,
            Res.string.activity_high_description,
            "high",
        ),
        ActivityOption(
            ActivityLevel.VeryHigh,
            Res.string.activity_very_high_title,
            Res.string.activity_very_high_description,
            "very_high",
        ),
    )

/** The localized title of an activity level (the Profile row value). */
internal fun activityTitle(level: ActivityLevel): StringResource = ActivityOptions.first { it.level == level }.title

@Composable
internal fun ActivityCards(
    selected: ActivityLevel?,
    onSelect: (ActivityLevel) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ActivityOptions.forEach { option ->
            SelectableCard(
                title = stringResource(option.title),
                description = stringResource(option.description),
                selected = selected == option.level,
                onClick = { onSelect(option.level) },
                modifier = Modifier.testTag("${tagPrefix}_activity_${option.tagName}"),
            )
        }
    }
}
