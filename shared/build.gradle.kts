import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "io.github.lopezjavier.appfitness.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Single-variant module (ADR-19): keep `./gradlew testLocalDebugUnitTest` running these tests too.
tasks.register("testLocalDebugUnitTest") {
    group = "verification"
    description = "Runs the host (JVM) unit tests of this module, including commonTest."
    dependsOn("testAndroidHostTest")
}
