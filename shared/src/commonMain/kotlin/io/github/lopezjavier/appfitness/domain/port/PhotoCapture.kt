package io.github.lopezjavier.appfitness.domain.port

/** Where a food photo comes from. */
enum class PhotoSource { Camera, Gallery }

/** How much the captured JPEG is shrunk: [Compact] (smaller image, fewer bytes) is for amount estimates. */
enum class PhotoQuality { Full, Compact }

/** The outcome of one capture. [Photo.handle] is an opaque path of a compressed JPEG; it is never persisted. */
sealed interface CaptureResult {
    data class Photo(val handle: String) : CaptureResult

    /** The user closed the camera or the picker. */
    data object Cancelled : CaptureResult

    /** The camera or the picker could not run. */
    data object Error : CaptureResult
}

/** Takes or picks a photo (Android: system camera / photo picker, no permission) and hands out compressed JPEGs. */
interface PhotoCapture {
    suspend fun capture(source: PhotoSource, quality: PhotoQuality = PhotoQuality.Full): CaptureResult

    /** The bytes of the JPEG behind [handle]. */
    suspend fun readJpeg(handle: String): ByteArray

    /** Deletes the file behind [handle]. */
    fun discard(handle: String)

    /** Deletes every leftover photo file. */
    fun sweep()
}

/** Used until the platform implementation is bound: every capture fails. */
class UnconfiguredPhotoCapture : PhotoCapture {
    override suspend fun capture(source: PhotoSource, quality: PhotoQuality): CaptureResult = CaptureResult.Error

    override suspend fun readJpeg(handle: String): ByteArray = ByteArray(0)

    override fun discard(handle: String) = Unit

    override fun sweep() = Unit
}
