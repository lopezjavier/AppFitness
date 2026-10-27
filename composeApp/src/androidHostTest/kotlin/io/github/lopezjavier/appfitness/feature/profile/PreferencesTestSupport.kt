package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The section editor with the Training section and no close handler, for tests that only render one section. */
@Composable
internal fun PreferencesScreen(
    state: PreferencesUiState,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<PreferencesEffect> = emptyFlow(),
    section: PreferencesSection = PreferencesSection.Training,
    onClose: () -> Unit = {},
) = PreferencesSectionScreen(section, state, onEvent, onClose, modifier, effects)
