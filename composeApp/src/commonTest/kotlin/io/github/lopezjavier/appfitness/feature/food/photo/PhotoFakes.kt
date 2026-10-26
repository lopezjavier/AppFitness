package io.github.lopezjavier.appfitness.feature.food.photo

import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.domain.port.PhotoQuality
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import kotlinx.coroutines.CompletableDeferred

/** Test double of [PhotoCapture]: hands out "photo-<n>" handles, or [results] in order; records every call. */
internal class FakePhotoCapture : PhotoCapture {
    /** Answers of the next captures; when empty a new photo handle is returned. */
    val results = ArrayDeque<CaptureResult>()
    val captured = mutableListOf<PhotoSource>()
    val qualities = mutableListOf<PhotoQuality>()
    val discarded = mutableListOf<String>()
    val reads = mutableListOf<String>()
    var sweeps = 0
    var bytes = byteArrayOf(1, 2, 3)

    /** When set, [capture] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null
    private var counter = 0

    override suspend fun capture(source: PhotoSource, quality: PhotoQuality): CaptureResult {
        captured += source
        qualities += quality
        gate?.await()
        return results.removeFirstOrNull() ?: CaptureResult.Photo("photo-${++counter}")
    }

    override suspend fun readJpeg(handle: String): ByteArray {
        reads += handle
        return bytes
    }

    override fun discard(handle: String) {
        discarded += handle
    }

    override fun sweep() {
        sweeps++
    }
}

/** One recorded call of [FakeFoodPhotoRepository.estimate]. */
internal data class EstimateCall(val mode: FoodPhotoMode, val imageBase64: String, val comment: String?)

/** Test double of [FoodPhotoRepository]: answers [result]; records the calls. */
internal class FakeFoodPhotoRepository(var result: Result<FoodPhotoDraft> = Result.success(lentils)) :
    FoodPhotoRepository {
    val calls = mutableListOf<EstimateCall>()

    /** When set, [estimate] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun estimate(mode: FoodPhotoMode, imageBase64: String, comment: String?): Result<FoodPhotoDraft> {
        calls += EstimateCall(mode, imageBase64, comment)
        gate?.await()
        return result
    }

    /** Answer of [estimateAmount]. */
    var amountResult: Result<AmountEstimate> = Result.success(AmountEstimate(180.0, "g", null))
    val amountCalls = mutableListOf<Pair<Food, String>>()

    /** When set, [estimateAmount] suspends until it completes. */
    var amountGate: CompletableDeferred<Unit>? = null

    override suspend fun estimateAmount(food: Food, imageBase64: String): Result<AmountEstimate> {
        amountCalls += food to imageBase64
        amountGate?.await()
        return amountResult
    }

    companion object {
        /** The dish fixture: 180 kcal, 6 g protein, 15 g carbs, 10 g fat per 100 g; one 250 g serving. */
        val lentils = FoodPhotoDraft(
            name = "Lentejas con pollo",
            brand = null,
            baseUnit = "g",
            kcal = 180.0,
            protein = 6.0,
            carbs = 15.0,
            fat = 10.0,
            micronutrients = emptyMap(),
            estimatedGrams = 250.0,
            confidence = null,
        )
    }
}

/** A [FoodPhotoViewModel] over fakes. */
internal fun foodPhotoModel(
    repository: FoodPhotoRepository = FakeFoodPhotoRepository(),
    capture: PhotoCapture = FakePhotoCapture(),
    connectivity: io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver =
        io.github.lopezjavier.appfitness.app.FakeConnectivityObserver(),
    state: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
) = FoodPhotoViewModel(repository, capture, connectivity, state)
