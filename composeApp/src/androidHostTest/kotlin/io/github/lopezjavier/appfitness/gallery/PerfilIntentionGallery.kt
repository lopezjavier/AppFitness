package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.feature.profile.IntentionIcon
import io.github.lopezjavier.appfitness.feature.profile.label
import org.jetbrains.compose.resources.stringResource

/** Every training intention with its icon, selected (lime) and unselected, for the visual review (05 §6.0j). */
@Composable
private fun IntentionIconsOverview() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        listOf(true, false).forEach { selected ->
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    TrainingIntention.entries.forEach { intention ->
                        AppListRow(
                            headline = stringResource(intention.label),
                            supporting = if (selected) "Seleccionado" else "Sin seleccionar",
                            leading = { IntentionIcon(intention, selected) },
                        )
                    }
                }
            }
        }
    }
}

internal fun perfilIntentionEntries(): List<GalleryEntry> = listOf(
    GalleryEntry(PERFIL_AREA, "Iconos de objetivo", "all intentions, selected and unselected") {
        IntentionIconsOverview()
    },
)
