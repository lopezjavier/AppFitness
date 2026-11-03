package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.FolderCopy
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.repository.FolderReviewRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate

/** Test double of [FolderReviewRepository]: every call is recorded and each answer is a settable result. */
internal class FakeFolderReviewRepository : FolderReviewRepository {
    var reviewResult: Result<FolderReview> = Result.success(review())
    var storedResult: Result<FolderReview?> = Result.success(null)
    var applyResult: Result<FolderCopy> = Result.success(FolderCopy("copy-1", listOf("r1")))
    var blockResult: Result<CoachBlock> = Result.success(coachBlock())

    /** When set, [review] suspends until completed (the coach is thinking). */
    var reviewGate: CompletableDeferred<Unit>? = null

    /** The paid calls: [review] invocations. */
    var reviewCalls = 0
    val reviewArgs = mutableListOf<Triple<String, ReviewPurpose, String?>>()
    val storedReads = mutableListOf<String>()
    val applied = mutableListOf<Pair<String, List<String>>>()
    val blocks = mutableListOf<List<String>>()
    val blockDates = mutableListOf<LocalDate>()
    val blockPlans = mutableListOf<String>()

    override suspend fun review(
        folderId: String,
        purpose: ReviewPurpose,
        planId: String?,
        today: LocalDate,
    ): Result<FolderReview> {
        reviewCalls++
        reviewArgs += Triple(folderId, purpose, planId)
        reviewGate?.await()
        return reviewResult
    }

    override suspend fun stored(reviewId: String): Result<FolderReview?> {
        storedReads += reviewId
        return storedResult
    }

    override suspend fun applyCopy(reviewId: String, changeIds: List<String>): Result<FolderCopy> {
        applied += reviewId to changeIds
        return applyResult
    }

    override suspend fun blockFromReview(
        planId: String,
        today: LocalDate,
        reviewId: String,
        acceptedIds: List<String>,
    ): Result<CoachBlock> {
        blockPlans += planId
        blockDates += today
        blocks += acceptedIds
        return blockResult
    }
}
