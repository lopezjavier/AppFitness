package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_count
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_equipment_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_fat_loss
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_helper
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_muscle
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_recomposition
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_strength
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_gym
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_helper
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_home
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_other
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_session_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_unset
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppToggleChip
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OptionPickerSheet
import io.github.lopezjavier.appfitness.designsystem.PickerOption
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.durationText
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SECONDS_PER_MINUTE = 60

/** Spanish label of this training intention (editor picker and hub row value). */
internal val TrainingIntention.label: StringResource
    get() = when (this) {
        TrainingIntention.Strength -> Res.string.my_training_intention_strength
        TrainingIntention.Muscle -> Res.string.my_training_intention_muscle
        TrainingIntention.FatLoss -> Res.string.my_training_intention_fat_loss
        TrainingIntention.Recomposition -> Res.string.my_training_intention_recomposition
        TrainingIntention.Maintenance -> Res.string.my_training_intention_maintenance
    }

private val TrainingPlace.label: StringResource
    get() = when (this) {
        TrainingPlace.Home -> Res.string.my_training_place_home
        TrainingPlace.Gym -> Res.string.my_training_place_gym
        TrainingPlace.Other -> Res.string.my_training_place_other
    }

private enum class MyTrainingPicker { Intention, Days, Session }

/**
 * "Mi entrenamiento" section of Preferencias (US-65): intention, place, equipment, days per week and session length.
 * Everything is a draft (R-10): a tap applies to the draft and the editor's "Guardar" saves. The optional fields
 * offer "Sin definir" as the first picker option.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class) // FlowRow wraps the equipment chips.
internal fun MyTrainingSection(form: PreferencesForm, onEvent: (PreferencesEvent) -> Unit) {
    var picker by rememberSaveable { mutableStateOf<MyTrainingPicker?>(null) }
    val unset = stringResource(Res.string.my_training_unset)
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NavigationRow(
                label = stringResource(Res.string.my_training_intention_label),
                onClick = { picker = MyTrainingPicker.Intention },
                modifier = Modifier.testTag("preferences_intention_field"),
                value = form.intention?.let { stringResource(it.label) } ?: unset,
            )
            Helper(stringResource(Res.string.my_training_intention_helper))
            Label(stringResource(Res.string.my_training_place_label))
            val places = TrainingPlace.entries
            SegmentedSelector(
                options = places.map {
                    AppOption(stringResource(it.label), "preferences_place_${it.name.lowercase()}")
                },
                selectedIndex = form.place?.let { places.indexOf(it) },
                onSelect = { onEvent(PreferencesEvent.SetPlace(places[it])) },
                modifier = Modifier.testTag("preferences_place_selector"),
            )
            Helper(stringResource(Res.string.my_training_place_helper))
            Label(stringResource(Res.string.my_training_equipment_label))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Equipment.entries.forEach { equipment ->
                    AppToggleChip(
                        label = stringResource(equipment.label),
                        checked = equipment in form.equipment,
                        onCheckedChange = { onEvent(PreferencesEvent.SetEquipment(equipment, it)) },
                        modifier = Modifier.testTag("preferences_equipment_${equipment.tagName()}"),
                    )
                }
            }
            NavigationRow(
                label = stringResource(Res.string.my_training_days_label),
                onClick = { picker = MyTrainingPicker.Days },
                modifier = Modifier.testTag("preferences_days_field"),
                value = form.daysPerWeek?.let { daysText(it) } ?: unset,
            )
            NavigationRow(
                label = stringResource(Res.string.my_training_session_label),
                onClick = { picker = MyTrainingPicker.Session },
                modifier = Modifier.testTag("preferences_session_field"),
                value = form.sessionMinutes?.let { durationText(it.toLong() * SECONDS_PER_MINUTE) } ?: unset,
            )
        }
    }
    val close = { picker = null }
    when (picker) {
        MyTrainingPicker.Intention -> IntentionPicker(form.intention, onEvent, close)
        MyTrainingPicker.Days -> DaysPicker(form.daysPerWeek, onEvent, close)
        MyTrainingPicker.Session -> SessionPicker(form.sessionMinutes, onEvent, close)
        null -> Unit
    }
}

@Composable
private fun daysText(days: Int) = pluralStringResource(Res.plurals.my_training_days_count, days, days)

@Composable
private fun Label(text: String) =
    Text(text = text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)

@Composable
private fun Helper(text: String) =
    Text(text = text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)

@Composable
private fun IntentionPicker(current: TrainingIntention?, onEvent: (PreferencesEvent) -> Unit, onClose: () -> Unit) {
    val none = PickerOption("none", stringResource(Res.string.my_training_unset), current == null) {
        onEvent(PreferencesEvent.SetIntention(null))
    }
    val options = TrainingIntention.entries.map {
        PickerOption(it.tagName(), stringResource(it.label), it == current) {
            onEvent(PreferencesEvent.SetIntention(it))
        }
    }
    OptionPickerSheet(
        title = stringResource(Res.string.my_training_intention_label),
        tagPrefix = "preferences_intention",
        options = listOf(none) + options,
        onClose = onClose,
    )
}

@Composable
private fun DaysPicker(current: Int?, onEvent: (PreferencesEvent) -> Unit, onClose: () -> Unit) {
    val none = PickerOption("none", stringResource(Res.string.my_training_unset), current == null) {
        onEvent(PreferencesEvent.SetDaysPerWeek(null))
    }
    val options = MyTraining.DAYS.map {
        PickerOption("$it", daysText(it), it == current) { onEvent(PreferencesEvent.SetDaysPerWeek(it)) }
    }
    OptionPickerSheet(
        title = stringResource(Res.string.my_training_days_label),
        tagPrefix = "preferences_days",
        options = listOf(none) + options,
        onClose = onClose,
    )
}

@Composable
private fun SessionPicker(current: Int?, onEvent: (PreferencesEvent) -> Unit, onClose: () -> Unit) {
    val none = PickerOption("none", stringResource(Res.string.my_training_unset), current == null) {
        onEvent(PreferencesEvent.SetSessionMinutes(null))
    }
    val options = MyTraining.SESSION_MINUTES.map {
        PickerOption("$it", durationText(it.toLong() * SECONDS_PER_MINUTE), it == current) {
            onEvent(PreferencesEvent.SetSessionMinutes(it))
        }
    }
    OptionPickerSheet(
        title = stringResource(Res.string.my_training_session_label),
        tagPrefix = "preferences_session",
        options = listOf(none) + options,
        onClose = onClose,
    )
}
