package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_age
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_decimal_separator
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_birth_date
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_height
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_sex
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_weight
import io.github.lopezjavier.appfitness.composeapp.resources.profile_activity_label
import io.github.lopezjavier.appfitness.composeapp.resources.profile_age_label
import io.github.lopezjavier.appfitness.composeapp.resources.profile_height_spoken
import io.github.lopezjavier.appfitness.composeapp.resources.profile_height_value
import io.github.lopezjavier.appfitness.composeapp.resources.profile_personal_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sheet_done
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_hint
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_value
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.feature.biometrics.ActivityCards
import io.github.lopezjavier.appfitness.feature.biometrics.BirthDateWheels
import io.github.lopezjavier.appfitness.feature.biometrics.HeightWheel
import io.github.lopezjavier.appfitness.feature.biometrics.SexSelector
import io.github.lopezjavier.appfitness.feature.biometrics.activityTitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val RowMinHeight: Dp = Spacing.minTouchTarget
private const val TENTHS_PER_KG = 10
private const val TAG_PREFIX = "profile"

/** Which picker sheet is open. */
private enum class PersonalSheet { BirthDate, Height, Activity }

/**
 * "Datos personales": sex inline, birth date / height / activity as rows that open a picker sheet, and the latest
 * weight as a read-only row. Sheet edits reach the form only through "Listo".
 */
@Composable
internal fun PersonalDataCard(
    state: ProfileUiState.Loaded,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by rememberSaveable { mutableStateOf<PersonalSheet?>(null) }
    val form = state.form
    val editable = !state.isSaving
    AppCard(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.profile_personal_title),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(Res.string.onboarding_label_sex),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            SexSelector(form.sex, { onEvent(ProfileEvent.SelectSex(it)) }, tagPrefix = TAG_PREFIX)
        }
        val age = BiometricBounds.ageOn(form.birthDate, state.today)
        val ageText = stringResource(Res.string.onboarding_age, age)
        EditableRow(
            label = stringResource(Res.string.profile_age_label),
            value = ageText,
            spokenValue = ageText,
            tag = "profile_birth_date_row",
            enabled = editable,
        ) { sheet = PersonalSheet.BirthDate }
        EditableRow(
            label = stringResource(Res.string.onboarding_label_height),
            value = stringResource(Res.string.profile_height_value, form.heightCm),
            spokenValue = stringResource(Res.string.profile_height_spoken, form.heightCm),
            tag = "profile_height_row",
            enabled = editable,
        ) { sheet = PersonalSheet.Height }
        val activity = stringResource(activityTitle(form.activity))
        EditableRow(
            label = stringResource(Res.string.profile_activity_label),
            value = activity,
            spokenValue = activity,
            tag = "profile_activity_row",
            enabled = editable,
        ) { sheet = PersonalSheet.Activity }
        WeightRow(state.weightKg)
    }
    PersonalSheets(sheet, state, onEvent) { sheet = null }
}

@Suppress("LongParameterList")
@Composable
private fun EditableRow(
    label: String,
    value: String,
    spokenValue: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spoken = stringResource(Res.string.profile_row_cd, label, spokenValue)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
        )
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}

/** The latest weight: no chevron, no click, with the hint that explains where it is edited. */
@Composable
private fun WeightRow(weightKg: Double) {
    val tenths = (weightKg * TENTHS_PER_KG).roundToInt()
    val separator = stringResource(Res.string.onboarding_decimal_separator)
    val text =
        stringResource(Res.string.profile_weight_value, "${tenths / TENTHS_PER_KG}$separator${tenths % TENTHS_PER_KG}")
    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(Res.string.onboarding_label_weight),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = text,
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("profile_weight_value"),
            )
        }
        Text(
            text = stringResource(Res.string.profile_weight_hint),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun PersonalSheets(
    sheet: PersonalSheet?,
    state: ProfileUiState.Loaded,
    onEvent: (ProfileEvent) -> Unit,
    onClose: () -> Unit,
) {
    val form = state.form
    when (sheet) {
        PersonalSheet.BirthDate -> PickerSheet(
            title = Res.string.onboarding_label_birth_date,
            contentTag = "profile_sheet_birth_date",
            initial = form.birthDate,
            onApply = { onEvent(ProfileEvent.SetBirthDate(it)) },
            onClose = onClose,
        ) { draft, onDraft -> BirthDateWheels(draft, state.today, onDraft, tagPrefix = TAG_PREFIX) }

        PersonalSheet.Height -> PickerSheet(
            title = Res.string.onboarding_label_height,
            contentTag = "profile_sheet_height",
            initial = form.heightCm,
            onApply = { onEvent(ProfileEvent.SetHeight(it)) },
            onClose = onClose,
        ) { draft, onDraft -> HeightWheel(draft, onDraft, tagPrefix = TAG_PREFIX) }

        PersonalSheet.Activity -> PickerSheet<ActivityLevel>(
            title = Res.string.profile_activity_label,
            contentTag = "profile_sheet_activity",
            initial = form.activity,
            onApply = { onEvent(ProfileEvent.SelectActivity(it)) },
            onClose = onClose,
        ) { draft, onDraft -> ActivityCards(draft, onDraft, tagPrefix = TAG_PREFIX) }

        null -> Unit
    }
}

/**
 * Bottom sheet with a title, the picker bound to a local draft and a full-width "Listo". Only "Listo" applies the
 * draft; the scrim, back and a drag discard it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun <T> PickerSheet(
    title: StringResource,
    contentTag: String,
    initial: T,
    onApply: (T) -> Unit,
    onClose: () -> Unit,
    picker: @Composable (draft: T, onDraft: (T) -> Unit) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AppBottomSheet(onDismissRequest = onClose, sheetState = sheetState, testTagPrefix = "profile_sheet") {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Column(
                Modifier.weight(1f, fill = false).testTag(contentTag).verticalScroll(rememberScrollState()),
            ) {
                picker(draft) { draft = it }
            }
            PrimaryButton(
                text = stringResource(Res.string.profile_sheet_done),
                onClick = {
                    onApply(draft)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
                },
                modifier = Modifier.fillMaxWidth().testTag("profile_sheet_done"),
            )
        }
    }
}
