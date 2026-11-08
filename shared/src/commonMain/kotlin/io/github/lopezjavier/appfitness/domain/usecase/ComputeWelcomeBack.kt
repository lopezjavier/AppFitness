package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate

private const val WELCOME_BACK_AFTER_DAYS = 14

/**
 * The Trainer "Bienvenido de nuevo" card (US-107). It shows when a block is saved, the latest completed session is
 * [WELCOME_BACK_AFTER_DAYS] or more days old and the block was not saved after that session (a new block or a new
 * session ends it). A user without completed sessions never sees it.
 */
class ComputeWelcomeBack {
    operator fun invoke(
        today: LocalDate,
        lastCompletedDay: LocalDate?,
        blockSavedDay: LocalDate?,
        hasSavedBlock: Boolean,
    ): Boolean {
        if (!hasSavedBlock || lastCompletedDay == null || blockSavedDay == null) return false
        val gap = today.toEpochDays() - lastCompletedDay.toEpochDays()
        return gap >= WELCOME_BACK_AFTER_DAYS && blockSavedDay <= lastCompletedDay
    }
}
