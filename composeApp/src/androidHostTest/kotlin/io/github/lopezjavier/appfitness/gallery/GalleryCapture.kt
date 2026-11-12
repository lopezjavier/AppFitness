package io.github.lopezjavier.appfitness.gallery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File
import javax.imageio.ImageIO

/** Compose time given to a new state to settle (sheets slide in, skeletons start). */
private const val SETTLE_MILLIS = 1_000L

/**
 * The one capture rule of the gallery (T-279): `AppTheme`, 360x640 es-ES window (the test's `@Config`), the real
 * screen composable with a fake state, `captureScreenRoboImage` so sheet and dialog windows are in the image, and a
 * sidecar JSON for the index. It only runs when `-Pgallery` set the `gallery.dir` system property.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.renderGallery(entries: List<GalleryEntry>) {
    val configured = System.getProperty("gallery.dir")
    assumeTrue("Gallery capture runs only with -Pgallery (python scripts/gallery.py)", configured != null)
    val root = checkNotNull(configured)
    val current = mutableStateOf<GalleryEntry?>(entries.firstOrNull())
    setContent {
        AppTheme {
            current.value?.let { entry ->
                key(entry.key) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, entry.fontScale),
                        LocalAnimationsEnabled provides false,
                        LocalNotificationsEntry provides NotificationsEntry(0) {},
                    ) {
                        entry.content()
                    }
                }
            }
        }
    }
    for (entry in entries) {
        runOnUiThread { current.value = entry }
        waitForIdle()
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        waitForIdle()
        entry.interact?.let {
            it(this)
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MILLIS)
            waitForIdle()
        }
        val png = File(root, "${entry.key}.png").also { it.parentFile.mkdirs() }
        captureScreenRoboImage(
            filePath = png.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        File(root, "${entry.key}.json").writeText(sidecar(entry), Charsets.UTF_8)
        assertTrue("Gallery: ${entry.key} rendered a blank image", hasMoreThanOneColour(png))
    }
}

private fun hasMoreThanOneColour(png: File): Boolean {
    val image = ImageIO.read(png)
    val first = image.getRGB(0, 0)
    return (0 until image.height).any { y -> (0 until image.width).any { x -> image.getRGB(x, y) != first } }
}

private fun sidecar(entry: GalleryEntry): String {
    val also = entry.alsoOpenedFrom.joinToString(",") { "\"${escape(it)}\"" }
    return """{"area":"${escape(entry.area)}","screen":"${escape(entry.screen)}","state":"${escape(entry.state)}",""" +
        """"kind":"${entry.kind.name.lowercase()}","alsoOpenedFrom":[$also]}"""
}

private fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

/** Writes `manifest.json` (the exemptions, with their reasons) for the index; runs only with `-Pgallery`. */
internal fun writeGalleryManifest() {
    val configured = System.getProperty("gallery.dir")
    assumeTrue("Gallery capture runs only with -Pgallery (python scripts/gallery.py)", configured != null)
    fun obj(map: Map<String, String>) =
        map.entries.joinToString(",", "{", "}") { "\"${escape(it.key)}\":\"${escape(it.value)}\"" }
    val dir = File(checkNotNull(configured)).also { it.mkdirs() }
    File(dir, "manifest.json").writeText("{\"exempt\":${obj(GalleryManifest.exempt)}}", Charsets.UTF_8)
}
