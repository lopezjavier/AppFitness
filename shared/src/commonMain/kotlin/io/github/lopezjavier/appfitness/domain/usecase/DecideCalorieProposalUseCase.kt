package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import kotlinx.datetime.LocalDate

/** The user's answer to a pending calorie proposal: "Aplicar" ([apply]) or "Ahora no" ([notNow]). */
class DecideCalorieProposalUseCase(private val repository: CoachPlanRepository) {
    suspend fun apply(id: String, today: LocalDate): Result<Unit> = repository.decideCalorieProposal(id, true, today)

    suspend fun notNow(id: String, today: LocalDate): Result<Unit> = repository.decideCalorieProposal(id, false, today)
}
