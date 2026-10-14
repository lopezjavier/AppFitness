package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
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
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private class ActivityOption(
    val level: ActivityLevel,
    val title: StringResource,
    val description: StringResource,
    val tag: String,
)

private val ActivityOptions =
    listOf(
        ActivityOption(
            ActivityLevel.Sedentary,
            Res.string.activity_sedentary_title,
            Res.string.activity_sedentary_description,
            "onboarding_activity_sedentary",
        ),
        ActivityOption(
            ActivityLevel.Light,
            Res.string.activity_light_title,
            Res.string.activity_light_description,
            "onboarding_activity_light",
        ),
        ActivityOption(
            ActivityLevel.Moderate,
            Res.string.activity_moderate_title,
            Res.string.activity_moderate_description,
            "onboarding_activity_moderate",
        ),
        ActivityOption(
            ActivityLevel.High,
            Res.string.activity_high_title,
            Res.string.activity_high_description,
            "onboarding_activity_high",
        ),
        ActivityOption(
            ActivityLevel.VeryHigh,
            Res.string.activity_very_high_title,
            Res.string.activity_very_high_description,
            "onboarding_activity_very_high",
        ),
    )

@Composable
internal fun ActivityStep(selected: ActivityLevel?, onSelect: (ActivityLevel) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ActivityOptions.forEach { option ->
            SelectableCard(
                title = stringResource(option.title),
                description = stringResource(option.description),
                selected = selected == option.level,
                onClick = { onSelect(option.level) },
                modifier = Modifier.testTag(option.tag),
            )
        }
    }
}
