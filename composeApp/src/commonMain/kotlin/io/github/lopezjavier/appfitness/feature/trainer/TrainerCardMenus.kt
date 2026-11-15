package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_offline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_heart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.ic_pause
import io.github.lopezjavier.appfitness.composeapp.resources.ic_sleep
import io.github.lopezjavier.appfitness.composeapp.resources.offline_hint_template
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_diet_break_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_cardio_detail
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_deload_detail
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_diet_break_off
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_diet_break_on
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_extend_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_menu_extend_week_detail
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_new_block
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_new_block_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_replace
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_extend_offline
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Phase
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The card whose ⋮ sheet starts open (the gallery shows each menu open); null in the app. */
enum class TrainerRootMenu { Plan, Block }

/**
 * The ⋮ of a Trainer root card (R-105): a 48 dp icon button in the card's title row that opens its [AppActionSheet].
 * Hidden when [actions] is empty (no entry applies). The sheet's open state survives a recreation.
 */
@Composable
internal fun CardMenu(
    title: String,
    contentDescription: String,
    tagPrefix: String,
    actions: List<AppSheetAction>,
    initiallyOpen: Boolean = false,
) {
    if (actions.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    AppIconButton(
        contentDescription = contentDescription,
        icon = Res.drawable.ic_more_vert,
        testTag = "${tagPrefix}_menu",
        onClick = { open = true },
        tint = AppTheme.colors.textSecondary,
    )
    if (open) {
        AppActionSheet(
            actions = actions,
            onDismiss = { open = false },
            title = title,
            testTagPrefix = "${tagPrefix}_menu_sheet",
        )
    }
}

/** A row disabled offline says why in its hint: "Sin conexión: no se puede …" (R-30). */
@Composable
private fun offlineHint(online: Boolean, action: StringResource): String? =
    if (online) null else stringResource(Res.string.offline_hint_template, stringResource(action))

/**
 * The ⋮ of "Tu plan": "Crear plan nuevo", the diet-break switch as one entry ("Activar…" / "Quitar descanso de dieta",
 * only for an accepted cut the user did not edit) and "Editar cardio" (only with activity targets). Writes are
 * disabled offline with the existing hints; the cardio sheet saves on its own and gates offline itself.
 */
@Composable
@Suppress("LongParameterList") // The plan, the connection, the busy flag, its two events and the gallery hook.
internal fun PlanCardMenu(
    plan: CoachPlan,
    online: Boolean,
    dietBreakSaving: Boolean,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    onEvent: (TrainerEvent) -> Unit,
    initiallyOpen: Boolean = false,
) {
    val nutrition = plan.nutrition
    val actions = buildList {
        add(
            AppSheetAction(
                icon = Res.drawable.ic_add,
                title = stringResource(Res.string.trainer_plan_replace),
                hint = offlineHint(online, Res.string.trainer_plan_create_action),
                testTag = "trainer_plan_menu_new_plan",
                enabled = online,
                onClick = { onOpenBeforeStart(BeforeStartMode.Plan, null, plan.id) },
            ),
        )
        if (plan.phase == Phase.Cut && !nutrition.editedByUser) {
            val on = nutrition.dietBreak != null
            add(
                AppSheetAction(
                    icon = Res.drawable.ic_pause,
                    title = stringResource(
                        if (on) Res.string.trainer_menu_diet_break_off else Res.string.trainer_menu_diet_break_on,
                    ),
                    hint = offlineHint(online, Res.string.plan_proposal_diet_break_offline),
                    testTag = "trainer_plan_menu_diet_break",
                    enabled = online && !dietBreakSaving,
                    onClick = { onEvent(TrainerEvent.ToggleDietBreak(!on)) },
                ),
            )
        }
        nutrition.activity?.let { activity ->
            add(
                AppSheetAction(
                    icon = Res.drawable.ic_heart,
                    title = stringResource(Res.string.trainer_menu_cardio),
                    hint = stringResource(
                        Res.string.trainer_menu_cardio_detail,
                        AppNumberFormat.integer(activity.cardioMinWeek),
                    ),
                    testTag = "trainer_plan_menu_cardio",
                    onClick = { onEvent(TrainerEvent.OpenCardioEdit) },
                ),
            )
        }
    }
    CardMenu(
        title = stringResource(Res.string.trainer_plan_section),
        contentDescription = stringResource(Res.string.trainer_plan_menu_cd),
        tagPrefix = "trainer_plan",
        actions = actions,
        initiallyOpen = initiallyOpen,
    )
}

/**
 * The ⋮ of "Tu bloque": "Bloque nuevo" (accepted plan with a saved block, no review due), "Extender semana" (only while
 * the current week can be extended; opens the existing date picker), "Hacer descarga la próxima semana" (only while
 * one can be requested) and "Eliminar bloque" (danger, opens the R-08 dialog). Every entry writes, so each one is
 * disabled offline with its hint; the ⋮ is hidden when none applies.
 */
@Composable
internal fun BlockCardMenu(
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    initiallyOpen: Boolean = false,
) {
    val actions = buildList {
        if (state.canStartNewBlock && !state.blockReviewDue) {
            add(
                AppSheetAction(
                    icon = Res.drawable.ic_add,
                    title = stringResource(Res.string.trainer_new_block),
                    hint = offlineHint(online, Res.string.trainer_new_block_action),
                    testTag = "trainer_block_menu_new_block",
                    enabled = online,
                    onClick = { onEvent(TrainerEvent.OpenNewBlock) },
                ),
            )
        }
        if (state.weekExtension != null) {
            add(
                AppSheetAction(
                    icon = Res.drawable.ic_calendar,
                    title = stringResource(Res.string.trainer_menu_extend_week),
                    hint = offlineHint(online, Res.string.trainer_week_extend_offline)
                        ?: stringResource(Res.string.trainer_menu_extend_week_detail),
                    testTag = "trainer_block_menu_extend_week",
                    enabled = online && !state.extendingWeek,
                    onClick = { onEvent(TrainerEvent.OpenExtendWeekPicker) },
                ),
            )
        }
        if (state.canRequestDeload) {
            add(
                AppSheetAction(
                    icon = Res.drawable.ic_sleep,
                    title = stringResource(Res.string.coach_deload_request),
                    hint = offlineHint(online, Res.string.coach_deload_request_offline)
                        ?: stringResource(Res.string.trainer_menu_deload_detail),
                    testTag = "trainer_block_menu_deload",
                    enabled = online && !state.requestingDeload && state.busyChangeId == null,
                    onClick = { onEvent(TrainerEvent.RequestDeload) },
                ),
            )
        }
        if (state.block is BlockSummary.Saved) {
            add(
                AppSheetAction(
                    icon = Res.drawable.ic_delete,
                    title = stringResource(Res.string.trainer_block_remove),
                    hint = offlineHint(online, Res.string.trainer_block_remove_action),
                    testTag = "trainer_block_menu_remove",
                    enabled = online,
                    danger = true,
                    onClick = { onEvent(TrainerEvent.RequestRemoveBlock) },
                ),
            )
        }
    }
    CardMenu(
        title = stringResource(Res.string.trainer_block_title),
        contentDescription = stringResource(Res.string.trainer_block_menu_cd),
        tagPrefix = "trainer_block",
        actions = actions,
        initiallyOpen = initiallyOpen,
    )
}
