package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.rest_sound_chime
import io.github.lopezjavier.appfitness.composeapp.resources.rest_sound_double_beep
import io.github.lopezjavier.appfitness.composeapp.resources.rest_sound_selected
import io.github.lopezjavier.appfitness.composeapp.resources.rest_sound_system
import io.github.lopezjavier.appfitness.composeapp.resources.rest_sound_title
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RestSound
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** TEMPORARY - removed by T-148. Stateful chooser: connects [RestSoundChooserViewModel]. */
@Composable
fun RestSoundChooserRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestSoundChooserViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RestSoundChooserScreen(state, viewModel::select, onBack, modifier)
}

/**
 * TEMPORARY - removed by T-148. One row per end-of-rest sound candidate; tapping one plays it and saves it at once
 * (a documented exception to the Guardar rule: a throwaway single-choice list, see the T-146 UX notes).
 */
@Composable
fun RestSoundChooserScreen(
    state: RestSoundChooserUiState,
    onSelect: (RestSound) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.rest_sound_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "rest_sound_chooser",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("rest_sound_chooser_screen"),
        ) {
            state.sounds.forEach { sound ->
                val isSelected = sound == state.selected
                AppListRow(
                    headline = sound.title(),
                    modifier = Modifier
                        .semantics { selected = isSelected }
                        .testTag("rest_sound_chooser_row_${sound.id}"),
                    onClick = { onSelect(sound) },
                    trailing = if (isSelected) {
                        {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.rest_sound_selected),
                                tint = AppTheme.colors.athleticCyan,
                                modifier = Modifier.size(Spacing.xl),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun RestSound.title(): String = stringResource(
    when (this) {
        RestSound.SYSTEM -> Res.string.rest_sound_system
        RestSound.CHIME -> Res.string.rest_sound_chime
        RestSound.DOUBLE_BEEP -> Res.string.rest_sound_double_beep
    },
)
