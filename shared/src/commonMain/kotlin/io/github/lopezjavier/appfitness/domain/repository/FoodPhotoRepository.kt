package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode

/** Estimates a food from a photo through the `food-photo` Edge Function. A failure carries an `AppException`. */
interface FoodPhotoRepository {
    /** [imageBase64] is the JPEG as base64; [comment] is only sent for a dish. Neither is stored or logged. */
    suspend fun estimate(mode: FoodPhotoMode, imageBase64: String, comment: String?): Result<FoodPhotoDraft>

    /**
     * Estimates the amount (in [Food.baseUnit]) of the portion shown in the photo for an existing [food], which is sent
     * as data. Nothing is created or stored.
     */
    suspend fun estimateAmount(food: Food, imageBase64: String): Result<AmountEstimate>
}
