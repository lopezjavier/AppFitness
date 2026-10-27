package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_body_weight_error
import io.github.lopezjavier.appfitness.composeapp.resources.live_body_weight_field_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_body_weight_hint
import io.github.lopezjavier.appfitness.composeapp.resources.live_body_weight_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_options_save_error
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.weight.decimalText
import org.jetbrains.compose.resources.stringResource

private const val WEIGHT_MAX_DECIMALS = 1
private const val WEIGHT_MAX_INTEGER_DIGITS = 3

/** What the volume-option sheets of one exercise render, taken from the live state. */
internal data class VolumeOptionsUi(
    val bodyWeightKg: Double? = null,
    val healthConnectToday: Boolean = false,
    val bodyWeightDraft: String? = null,
    val bodyWeightInvalid: Boolean = false,
    /** The last save of an option failed (cleared by the next edit or save). */
    val saveFailed: Boolean = false,
)

internal fun LiveSessionUiState.Active.volumeOptionsFor(saveFailed: Boolean) = VolumeOptionsUi(
    bodyWeightKg = bodyWeightKg,
    healthConnectToday = todayHasHealthConnectWeight,
    bodyWeightDraft = bodyWeightDraft,
    bodyWeightInvalid = bodyWeightInvalid,
    saveFailed = saveFailed,
)

/**
 * "Actualizar peso corporal": the weight field (decimal comma, prefilled with the latest weight) and "Guardar", which
 * stores it as today's manual weight. The typed text survives process death; closing the sheet discards it.
 */
@Composable
internal fun BodyWeightSheet(
    position: Int,
    options: VolumeOptionsUi,
    onEvent: (LiveSessionEvent) -> Unit,
    onClose: () -> Unit,
) {
    val tag = "live_exercise_${position}_body_weight"
    var text by rememberSaveable {
        mutableStateOf(options.bodyWeightDraft ?: options.bodyWeightKg?.let(::decimalText).orEmpty())
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val currentClose by rememberUpdatedState(onClose)
    LaunchedEffect(options.bodyWeightDraft) { if (submitted && options.bodyWeightDraft == null) currentClose() }
    val label = stringResource(Res.string.live_body_weight_field_label)
    val errorText = when {
        options.bodyWeightInvalid -> stringResource(Res.string.live_body_weight_error)
        submitted && options.saveFailed -> stringResource(Res.string.live_options_save_error)
        else -> null
    }
    OptionSheet(
        title = stringResource(Res.string.live_body_weight_title),
        tag = tag,
        saveEnabled = NumberInputDefaults.parse(text) != null,
        errorText = errorText,
        errorTag = "${tag}_error",
        onSave = {
            submitted = true
            onEvent(LiveSessionEvent.SaveBodyWeight(text))
        },
        onDismiss = {
            onEvent(LiveSessionEvent.DismissVolumeDrafts)
            onClose()
        },
    ) {
        NumberInput(
            value = text,
            onValueChange = {
                text = it
                onEvent(LiveSessionEvent.BodyWeightDraftChanged(it))
            },
            label = label,
            modifier = Modifier.fillMaxWidth().testTag("${tag}_field"),
            maxDecimals = WEIGHT_MAX_DECIMALS,
            maxIntegerDigits = WEIGHT_MAX_INTEGER_DIGITS,
        )
        Text(
            text = stringResource(Res.string.live_body_weight_hint),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** Frame of the option sheets (edit archetype, commit "Guardar"): the body, then an error line under it. */
@Composable
@Suppress("LongParameterList") // Frame slots of the option sheets.
private fun OptionSheet(
    title: String,
    tag: String,
    saveEnabled: Boolean,
    errorText: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    errorTag: String = "${tag}_error",
    body: @Composable () -> Unit,
) {
    AppEditSheet(
        title = title,
        commit = SheetCommit.Save,
        onCommit = onSave,
        onDismiss = onDismiss,
        commitEnabled = saveEnabled,
        testTagPrefix = "${tag}_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            body()
            errorText?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag(errorTag),
                )
            }
        }
    }
}
