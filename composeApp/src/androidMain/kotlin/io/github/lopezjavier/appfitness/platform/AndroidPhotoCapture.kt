package io.github.lopezjavier.appfitness.platform

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.domain.port.PhotoQuality
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

private const val PHOTO_DIR = "food_photos"

/** Runs [block] and deletes the raw source file on every outcome, including a cancelled coroutine. */
internal suspend fun <T> deletingAfter(raw: File, block: suspend () -> T): T = try {
    block()
} finally {
    raw.delete()
}

/**
 * [PhotoCapture] over the system camera (`TakePicture` + [FileProvider]) and the system photo picker. Neither needs a
 * permission. Each capture registers its launcher on the current activity with a fresh random key, so a result is
 * never re-delivered to a recreated process. Every output is compressed by [PhotoCompressor] into
 * `cacheDir/food_photos` and the uncompressed source is deleted at once. If the activity is destroyed mid-capture the
 * result is `Cancelled`.
 */
class AndroidPhotoCapture(private val cacheDir: File, private val activityHolder: CurrentActivityHolder) :
    PhotoCapture {
    override suspend fun capture(source: PhotoSource, quality: PhotoQuality): CaptureResult {
        val activity = activityHolder.get() as? ComponentActivity ?: return CaptureResult.Error
        val dir = photoDir(cacheDir)
        val raw = File(dir, "src_${UUID.randomUUID()}.jpg")
        return deletingAfter(raw) {
            val outcome = try {
                withContext(Dispatchers.Main.immediate) { launchAndAwait(activity, source, raw) }
            } catch (_: IllegalArgumentException) {
                // Only the kind matters; the exception is never kept or logged.
                Outcome.Failed
            } catch (_: ActivityNotFoundException) {
                Outcome.Failed
            }
            when (outcome) {
                Outcome.Cancelled -> CaptureResult.Cancelled.also { raw.delete() }
                Outcome.Failed -> CaptureResult.Error.also { raw.delete() }
                is Outcome.Picked -> withContext(Dispatchers.IO) { finish(activity, outcome.uri, raw, dir, quality) }
            }
        }
    }

    override suspend fun readJpeg(handle: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(handle)
        if (isPhotoFile(file)) file.readBytes() else ByteArray(0)
    }

    override fun discard(handle: String) {
        val file = File(handle)
        if (isPhotoFile(file)) file.delete()
    }

    override fun sweep() {
        File(cacheDir, PHOTO_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun isPhotoFile(file: File): Boolean = file.parentFile?.name == PHOTO_DIR && file.isFile

    private fun finish(
        activity: ComponentActivity,
        picked: Uri?,
        raw: File,
        dir: File,
        quality: PhotoQuality,
    ): CaptureResult {
        if (picked != null) {
            val copied = runCatching {
                activity.contentResolver.openInputStream(picked)?.use { input ->
                    raw.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (!copied) {
                raw.delete()
                return CaptureResult.Error
            }
        }
        val target = File(dir, "photo_${UUID.randomUUID()}.jpg")
        val ok = runCatching { PhotoCompressor.compress(raw, target, quality) }.getOrDefault(false)
        raw.delete()
        return if (ok) {
            CaptureResult.Photo(target.path)
        } else {
            target.delete()
            CaptureResult.Error
        }
    }

    private suspend fun launchAndAwait(activity: ComponentActivity, source: PhotoSource, raw: File): Outcome =
        suspendCancellableCoroutine { continuation ->
            val key = "food_photo_${UUID.randomUUID()}"
            val registry = activity.activityResultRegistry
            var launcher: ActivityResultLauncher<*>? = null
            lateinit var observer: DefaultLifecycleObserver
            var done = false

            fun release() {
                activity.lifecycle.removeObserver(observer)
                launcher?.unregister()
            }

            fun finishWith(outcome: Outcome) {
                if (done) return
                done = true
                release()
                if (continuation.isActive) continuation.resume(outcome)
            }

            observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = finishWith(Outcome.Cancelled)
            }
            activity.lifecycle.addObserver(observer)
            continuation.invokeOnCancellation {
                if (!done) {
                    done = true
                    activity.runOnUiThread { release() }
                }
            }
            when (source) {
                PhotoSource.Camera -> {
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.photos", raw)
                    launcher = registry.register(key, ActivityResultContracts.TakePicture()) { taken ->
                        finishWith(if (taken) Outcome.Picked(null) else Outcome.Cancelled)
                    }.also { it.launch(uri) }
                }

                PhotoSource.Gallery -> {
                    launcher = registry.register(key, ActivityResultContracts.PickVisualMedia()) { uri ->
                        finishWith(if (uri != null) Outcome.Picked(uri) else Outcome.Cancelled)
                    }.also {
                        it.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
        }

    private fun photoDir(cacheDir: File): File = File(cacheDir, PHOTO_DIR).also { it.mkdirs() }

    private sealed interface Outcome {
        /** [uri] is the picked image, or null when the camera already wrote the raw file. */
        data class Picked(val uri: Uri?) : Outcome

        data object Cancelled : Outcome

        data object Failed : Outcome
    }
}
