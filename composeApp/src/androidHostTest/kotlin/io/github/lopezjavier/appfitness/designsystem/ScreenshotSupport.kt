package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File

/**
 * Screenshot assertion for the golden-image tests (Roborazzi, no Gradle plugin).
 *
 * Goldens live in `composeApp/src/androidHostTest/snapshots/<name>.png` (the directory comes from the
 * `roborazzi.snapshots.dir` system property that `composeApp/build.gradle.kts` sets). A normal test run VERIFIES
 * against them; `./gradlew :composeApp:testAndroidHostTest -PrecordScreenshots` re-records them. Goldens were
 * recorded on Windows and CI runs on Linux, so the comparison tolerates [CHANGE_THRESHOLD] of changed pixels
 * (anti-aliasing drift) while a layout, colour or icon change still fails.
 */
internal fun SemanticsNodeInteraction.assertScreenshot(name: String) {
    val dir = checkNotNull(System.getProperty("roborazzi.snapshots.dir")) { "roborazzi.snapshots.dir is not set" }
    captureRoboImage(
        filePath = File(dir, "$name.png").path,
        roborazziOptions =
        RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD),
        ),
    )
}

private const val CHANGE_THRESHOLD = 0.01f
