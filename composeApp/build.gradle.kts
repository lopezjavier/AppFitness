import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.lopezjavier.appfitness.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        // Package Compose resources (fonts, strings, files) with the Android-KMP library plugin.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.vico.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.health.connect.client)
            implementation(libs.play.services.code.scanner)
            implementation(libs.play.services.oss.licenses)
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.coil.test)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.lopezjavier.appfitness.composeapp.resources"
    publicResClass = false
}

// Single-variant module (ADR-19): keep `./gradlew testLocalDebugUnitTest` running these tests too.
tasks.register("testLocalDebugUnitTest") {
    group = "verification"
    description = "Runs the host (JVM) unit tests of this module, including commonTest."
    dependsOn("testAndroidHostTest")
}

// Robolectric on JDK 17+ needs these modules opened (FileDescriptor / reflection interceptors).
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    )
}

// Roborazzi without its Gradle plugin: goldens live in src/androidHostTest/snapshots. Default = verify against them;
// `-PrecordScreenshots` rewrites them (see planning/06-agent-workflow.md section 4, "Screenshot tests").
tasks.withType<Test>().configureEach {
    val record = providers.gradleProperty("recordScreenshots").isPresent
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
    systemProperty("roborazzi.test.compare", "false")
    val snapshots = layout.projectDirectory.dir("src/androidHostTest/snapshots")
    systemProperty("roborazzi.snapshots.dir", snapshots.asFile.path)
    // Goldens are test inputs: editing one must re-run the tests.
    inputs.dir(snapshots).withPropertyName("screenshotGoldens").optional()
}
