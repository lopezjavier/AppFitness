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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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
import io.github.lopezjavier.appfitness.composeapp.resources.name_error_blank
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
import io.github.lopezjavier.appfitness.composeapp.resources.profile_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.profile_personal_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sheet_done
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_value
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.feature.biometrics.ActivityCards
import io.github.lopezjavier.appfitness.feature.biometrics.BirthDateWheels
import io.github.lopezjavier.appfitness.feature.biometrics.HeightWheel
import io.github.lopezjavier.appfitness.feature.biometrics.SexSelector
import io.github.lopezjavier.appfitness.feature.biometrics.WeightWheels
import io.github.lopezjavier.appfitness.feature.biometrics.activityTitle
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val RowMinHeight: Dp = Spacing.minTouchTarget
private const val TENTHS_PER_KG = 10
private const val TAG_PREFIX = "preferences"

// Savers of the sheet drafts: an edit in progress survives recreation (a draft is never applied on its own).
private val LocalDateSaver: Saver<LocalDate, Any> =
    Saver(save = { it.toString() }, restore = { LocalDate.parse(it as String) })
private val IntSaver: Saver<Int, Any> = Saver(save = { it }, restore = { it as Int })
private val ActivitySaver: Saver<ActivityLevel, Any> =
    Saver(save = { it.name }, restore = { ActivityLevel.valueOf(it as String) })

/** Which picker sheet is open. */
private enum class PersonalSheet { BirthDate, Height, Weight, Activity }

/**
 * "Datos personales": the name field, sex inline, and birth date / height / current weight / activity as rows that
 * open a picker sheet. Sheet edits reach the form only through "Listo".
 */
@Composable
internal fun PersonalDataCard(
    state: PreferencesUiState.Loaded,
    onEvent: (PreferencesEvent) -> Unit,
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
        NameField(form.name, editable, onEvent, Modifier.padding(top = Spacing.md))
        Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(Res.string.onboarding_label_sex),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            SexSelector(form.sex, { onEvent(PreferencesEvent.SelectSex(it)) }, tagPrefix = TAG_PREFIX)
        }
        val age = BiometricBounds.ageOn(form.birthDate, state.today)
        val ageText = stringResource(Res.string.onboarding_age, age)
        EditableRow(
            label = stringResource(Res.string.profile_age_label),
            value = ageText,
            spokenValue = ageText,
            tag = "preferences_birth_date_row",
            enabled = editable,
        ) { sheet = PersonalSheet.BirthDate }
        EditableRow(
            label = stringResource(Res.string.onboarding_label_height),
            value = stringResource(Res.string.profile_height_value, form.heightCm),
            spokenValue = stringResource(Res.string.profile_height_spoken, form.heightCm),
            tag = "preferences_height_row",
            enabled = editable,
        ) { sheet = PersonalSheet.Height }
        val activity = stringResource(activityTitle(form.activity))
        EditableRow(
            label = stringResource(Res.string.profile_activity_label),
            value = activity,
            spokenValue = activity,
            tag = "preferences_activity_row",
            enabled = editable,
        ) { sheet = PersonalSheet.Activity }
        WeightRow(form.weightKg, editable) { sheet = PersonalSheet.Weight }
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

/** "Peso actual": an editable row like the others; the value is the weight with one decimal. */
@Composable
private fun WeightRow(weightKg: Double, enabled: Boolean, onClick: () -> Unit) {
    val tenths = (weightKg * TENTHS_PER_KG).roundToInt()
    val separator = stringResource(Res.string.onboarding_decimal_separator)
    val text =
        stringResource(Res.string.profile_weight_value, "${tenths / TENTHS_PER_KG}$separator${tenths % TENTHS_PER_KG}")
    EditableRow(
        label = stringResource(Res.string.onboarding_label_weight),
        value = text,
        spokenValue = text,
        tag = "preferences_weight_row",
        enabled = enabled,
        onClick = onClick,
    )
}

/** The name text field; a blank name shows "Escribe tu nombre" and blocks saving. */
@Composable
private fun NameField(
    name: String,
    enabled: Boolean,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = name,
        onValueChange = { onEvent(PreferencesEvent.SetName(it)) },
        label = stringResource(Res.string.profile_name_label),
        modifier = modifier,
        maxLength = BiometricBounds.NAME_MAX_LENGTH,
        errorText = if (name.isBlank()) stringResource(Res.string.name_error_blank) else null,
        enabled = enabled,
        testTagPrefix = "preferences_name_field",
    )
}

@Composable
private fun PersonalSheets(
    sheet: PersonalSheet?,
    state: PreferencesUiState.Loaded,
    onEvent: (PreferencesEvent) -> Unit,
    onClose: () -> Unit,
) {
    val form = state.form
    when (sheet) {
        PersonalSheet.BirthDate -> PickerSheet(
            title = Res.string.onboarding_label_birth_date,
            contentTag = "preferences_sheet_birth_date",
            initial = form.birthDate,
            saver = LocalDateSaver,
            onApply = { onEvent(PreferencesEvent.SetBirthDate(it)) },
            onClose = onClose,
        ) { draft, onDraft, onCentred ->
            BirthDateWheels(draft, state.today, onDraft, tagPrefix = TAG_PREFIX, onCentredChange = onCentred)
        }

        PersonalSheet.Height -> PickerSheet(
            title = Res.string.onboarding_label_height,
            contentTag = "preferences_sheet_height",
            initial = form.heightCm,
            saver = IntSaver,
            onApply = { onEvent(PreferencesEvent.SetHeight(it)) },
            onClose = onClose,
        ) { draft, onDraft, onCentred ->
            HeightWheel(draft, onDraft, tagPrefix = TAG_PREFIX, onCentredChange = onCentred)
        }

        PersonalSheet.Weight -> PickerSheet(
            title = Res.string.onboarding_label_weight,
            contentTag = "preferences_sheet_weight",
            initial = (form.weightKg * TENTHS_PER_KG).roundToInt(),
            saver = IntSaver,
            onApply = { onEvent(PreferencesEvent.SetWeight(it)) },
            onClose = onClose,
        ) { draft, onDraft, onCentred ->
            WeightWheels(draft, onDraft, tagPrefix = TAG_PREFIX, onCentredChange = onCentred)
        }

        PersonalSheet.Activity -> PickerSheet<ActivityLevel>(
            title = Res.string.profile_activity_label,
            contentTag = "preferences_sheet_activity",
            initial = form.activity,
            saver = ActivitySaver,
            onApply = { onEvent(PreferencesEvent.SelectActivity(it)) },
            onClose = onClose,
        ) { draft, onDraft, _ -> ActivityCards(draft, onDraft, tagPrefix = TAG_PREFIX) }

        null -> Unit
    }
}

/**
 * Bottom sheet with a title, the picker bound to a local draft and a full-width "Listo". Only "Listo" applies the
 * centred value (what the wheels show, even mid-snap); the scrim, back and a drag discard it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun <T> PickerSheet(
    title: StringResource,
    contentTag: String,
    initial: T,
    saver: Saver<T, Any>,
    onApply: (T) -> Unit,
    onClose: () -> Unit,
    picker: @Composable (draft: T, onDraft: (T) -> Unit, onCentred: (T) -> Unit) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = saver) { mutableStateOf(initial) }

    // What the wheels show right now. It runs ahead of [draft] while a snap animation is still settling, so "Listo"
    // applies the centred item and not the value from before the swipe.
    var centred by rememberSaveable(stateSaver = saver) { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AppBottomSheet(onDismissRequest = onClose, sheetState = sheetState, testTagPrefix = "preferences_sheet") {
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
                picker(draft, {
                    draft = it
                    centred = it
                }) { centred = it }
            }
            PrimaryButton(
                text = stringResource(Res.string.profile_sheet_done),
                onClick = {
                    onApply(centred)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
                },
                modifier = Modifier.fillMaxWidth().testTag("preferences_sheet_done"),
            )
        }
    }
}
