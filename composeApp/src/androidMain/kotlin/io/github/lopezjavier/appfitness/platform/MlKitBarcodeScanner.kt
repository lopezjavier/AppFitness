package io.github.lopezjavier.appfitness.platform

import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.lopezjavier.appfitness.domain.port.BarcodeScanner
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [BarcodeScanner] over the Google code scanner (Play Services; no camera permission needed). Reads the retail
 * formats EAN-13, EAN-8, UPC-A and UPC-E. Without Play Services or an Activity the result is [ScanResult.Error].
 */
class MlKitBarcodeScanner(private val activityHolder: CurrentActivityHolder) : BarcodeScanner {
    override suspend fun scan(): ScanResult {
        val activity = activityHolder.get() ?: return ScanResult.Error
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build()
        val client = GmsBarcodeScanning.getClient(activity, options)
        return suspendCancellableCoroutine { continuation ->
            client.startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue
                    continuation.resume(if (value.isNullOrBlank()) ScanResult.Error else ScanResult.Code(value))
                }
                .addOnCanceledListener { continuation.resume(ScanResult.Cancelled) }
                .addOnFailureListener { failure ->
                    val cancelled = (failure as? MlKitException)?.errorCode == MlKitException.CODE_SCANNER_CANCELLED
                    continuation.resume(if (cancelled) ScanResult.Cancelled else ScanResult.Error)
                }
        }
    }
}
