package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ApplyFolderCopyParams
import io.github.lopezjavier.appfitness.data.remote.FolderBlockRequestBody
import io.github.lopezjavier.appfitness.data.remote.FolderBlockRequestInput
import io.github.lopezjavier.appfitness.data.remote.FolderCopyDto
import io.github.lopezjavier.appfitness.data.remote.FolderReviewRequestBody
import io.github.lopezjavier.appfitness.data.remote.FolderReviewRequestInput
import io.github.lopezjavier.appfitness.data.remote.FolderReviewResultDto
import io.github.lopezjavier.appfitness.data.remote.StoredFolderReviewDto
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.data.remote.wire
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.FolderCopy
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.model.requestKey
import io.github.lopezjavier.appfitness.domain.port.CoachModelPreference
import io.github.lopezjavier.appfitness.domain.repository.FolderReviewRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

private const val WRONG_OBJECT_STATE = "55000"
private const val INVALID_PARAMETER = "22023"
private const val REVIEW_COLUMNS = "id,folder_id,purpose,plan_id,input_hash,result"

/**
 * Folder reviews through the `coach` function (`folder_review`, and `plan_block` with the folder start, which makes no
 * model call), the `apply_folder_review_copy` RPC and the owner-select RLS of `coach_folder_reviews`.
 */
class SupabaseFolderReviewRepository internal constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
    private val modelPreference: CoachModelPreference,
    private val requestTimeout: Duration,
) : FolderReviewRepository {
    constructor(auth: Auth, postgrest: Postgrest, functions: Functions, modelPreference: CoachModelPreference) :
        this(auth, postgrest, functions, modelPreference, COACH_CLIENT_TIMEOUT)

    override suspend fun review(
        folderId: String,
        purpose: ReviewPurpose,
        planId: String?,
        today: LocalDate,
    ): Result<FolderReview> {
        val model = modelPreference.selected.value.requestKey()
        val input = FolderReviewRequestInput(today, folderId, purpose.wire(), planId)
        val body = coachPlanJson.encodeToString(FolderReviewRequestBody("folder_review", input, model))
        return callCoachFunction(auth, functions, requestTimeout, body) { text ->
            val dto = runCatching { coachPlanJson.decodeFromString<FolderReviewResultDto>(text) }.getOrNull()
            val review = dto?.reviewId?.let { dto.toDomain(it, folderId, purpose) }
            if (review == null) Result.failure(CoachException(CoachFailure.Other)) else Result.success(review)
        }
    }

    override suspend fun stored(reviewId: String): Result<FolderReview?> = auth.ownRow {
        postgrest.from("coach_folder_reviews")
            .select(Columns.raw(REVIEW_COLUMNS)) { filter { eq("id", reviewId) } }
            .decodeList<StoredFolderReviewDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun applyCopy(reviewId: String, changeIds: List<String>): Result<FolderCopy> = auth.ownRow {
        val copy = postgrest.rpc("apply_folder_review_copy", ApplyFolderCopyParams(reviewId, changeIds))
            .decodeAs<FolderCopyDto>()
        FolderCopy(copy.folderId, copy.routineIds)
    }.mapApplyErrors()

    override suspend fun blockFromReview(
        planId: String,
        today: LocalDate,
        reviewId: String,
        acceptedIds: List<String>,
    ): Result<CoachBlock> {
        val input = FolderBlockRequestInput(today, planId, "folder", reviewId, acceptedIds)
        val body = coachPlanJson.encodeToString(FolderBlockRequestBody("plan_block", input))
        return callCoachFunction(auth, functions, requestTimeout, body) {
            val block = loadStoredBlock(postgrest, planId)
            if (block != null) Result.success(block) else Result.failure(CoachException(CoachFailure.Other))
        }
    }

    /** SQLSTATE 55000: the folder changed since the review; 22023: a change id is not in the review. */
    private fun <T> Result<T>.mapApplyErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            WRONG_OBJECT_STATE -> throw CoachException(CoachFailure.Conflict)
            INVALID_PARAMETER -> throw CoachException(CoachFailure.InvalidReplacement)
            else -> throw failure
        }
    }
}
