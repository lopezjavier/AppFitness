package io.github.lopezjavier.appfitness.domain.port

/** The outcome of one scan. */
sealed interface ScanResult {
    data class Code(val value: String) : ScanResult

    /** The user closed the scanner. */
    data object Cancelled : ScanResult

    /** The scanner could not run (for example Google Play Services is missing). */
    data object Error : ScanResult
}

/** Reads a product barcode with the camera (Android: Google code scanner, no camera permission). */
interface BarcodeScanner {
    suspend fun scan(): ScanResult
}
