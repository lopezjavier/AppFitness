import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "io.github.lopezjavier.appfitness.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
