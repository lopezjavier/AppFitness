package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.record_best_1rm
import io.github.lopezjavier.appfitness.composeapp.resources.record_best_distance
import io.github.lopezjavier.appfitness.composeapp.resources.record_best_duration
import io.github.lopezjavier.appfitness.composeapp.resources.record_best_set
import io.github.lopezjavier.appfitness.composeapp.resources.record_cd
import io.github.lopezjavier.appfitness.composeapp.resources.record_heaviest_lift
import io.github.lopezjavier.appfitness.composeapp.resources.record_max_volume
import io.github.lopezjavier.appfitness.composeapp.resources.record_most_floors
import io.github.lopezjavier.appfitness.composeapp.resources.record_most_steps
import io.github.lopezjavier.appfitness.composeapp.resources.record_total_reps
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PillContainer
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The Spanish label of a record family; shared by the summary, the session detail and the records screen. */
internal fun PersonalRecordType.labelRes(): StringResource = when (this) {
    PersonalRecordType.HeaviestLift -> Res.string.record_heaviest_lift
    PersonalRecordType.BestSet -> Res.string.record_best_set
    PersonalRecordType.MaxVolume -> Res.string.record_max_volume
    PersonalRecordType.Best1Rm -> Res.string.record_best_1rm
    PersonalRecordType.TotalReps -> Res.string.record_total_reps
    PersonalRecordType.BestDuration -> Res.string.record_best_duration
    PersonalRecordType.BestDistance -> Res.string.record_best_distance
    PersonalRecordType.MostSteps -> Res.string.record_most_steps
    PersonalRecordType.MostFloors -> Res.string.record_most_floors
}

/** The localised label of this record family. */
@Composable
internal fun PersonalRecordType.label(): String = stringResource(labelRes())

/** The accessibility description of a record pill, "Récord: <label>". */
@Composable
internal fun PersonalRecordType.contentDescriptionText(): String = stringResource(Res.string.record_cd, label())

/**
 * One non-interactive pill per beaten record, in enum order, wrapping onto several lines. Renders nothing when
 * there are no records. Tags: `<tagPrefix>_records_<position>` for the container and
 * `<tagPrefix>_record_<position>_<key>` per pill.
 */
@Composable
internal fun PersonalRecordPills(
    records: Set<PersonalRecordType>,
    position: Int,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val shown = PersonalRecordType.entries.filter { it in records && it != PersonalRecordType.TotalReps }
    if (shown.isEmpty()) return
    FlowRow(
        modifier.testTag("${tagPrefix}_records_$position"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        shown.forEach { type ->
            val description = type.contentDescriptionText()
            PillContainer(
                Modifier.testTag("${tagPrefix}_record_${position}_${type.key}")
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Text(
                    text = type.label(),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.athleticCyan,
                )
            }
        }
    }
}
