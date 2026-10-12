import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Build-time configuration (planning/08-environment-setup.md §3.1).
// Precedence: local.properties (non-blank) > environment variable (non-blank) > default.
// A missing value never fails the build. Never log the resolved values: CI logs are public.
val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
        load(StringReader(providers.fileContents(localPropertiesFile).asText.orElse("").get()))
    }

fun configValue(propertyKey: String, environmentVariable: String? = null, default: String = ""): String =
    localProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: environmentVariable?.let { providers.environmentVariable(it).orNull }?.takeIf { it.isNotBlank() }
        ?: default

// Backslash must be escaped first so the escapes added afterwards are not doubled.
fun String.toJavaStringLiteral(): String {
    val escaped =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    return "\"$escaped\""
}

android {
    namespace = "io.github.lopezjavier.appfitness"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.lopezjavier.appfitness"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            configValue("google.webClientId", "GOOGLE_WEB_CLIENT_ID").toJavaStringLiteral(),
        )
    }

    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            // The SUPABASE_* environment variables feed prod only: CI exports the production
            // secrets and also runs the local unit tests. The default is the fixed emulator
            // address of the local Supabase (planning/02-architecture.md §5), not a secret.
            buildConfigField(
                "String",
                "SUPABASE_URL",
                configValue("supabase.local.url", default = "http://10.0.2.2:54321").toJavaStringLiteral(),
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                configValue("supabase.local.publishableKey").toJavaStringLiteral(),
            )
            // Fixed, non-secret probe that tests toJavaStringLiteral() end to end (LocalBuildConfigTest).
            buildConfigField("String", "BUILD_CONFIG_ESCAPE_PROBE", "a\"b\\c\nd\re".toJavaStringLiteral())
        }
        create("prod") {
            dimension = "env"
            buildConfigField(
                "String",
                "SUPABASE_URL",
                configValue("supabase.prod.url", "SUPABASE_URL").toJavaStringLiteral(),
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                configValue("supabase.prod.publishableKey", "SUPABASE_PUBLISHABLE_KEY").toJavaStringLiteral(),
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}
