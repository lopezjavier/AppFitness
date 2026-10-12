plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    // Applied on the root too, so ktlintKotlinScriptCheck lints build.gradle.kts and settings.gradle.kts.
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt) apply false
}

// Shared lint setup (T-016): ktlint rules live in .editorconfig, detekt overrides in
// config/detekt/detekt.yml (no baseline).
// Trade-off: allprojects {} / subprojects {} is cross-project configuration. It works with the configuration
// cache but not with Isolated Projects; move it to a build-logic convention plugin if that
// is ever enabled.
val ktlintPluginId = libs.plugins.ktlint.get().pluginId
val detektPluginId = libs.plugins.detekt.get().pluginId
val ktlintVersion = libs.versions.ktlint.asProvider().get()
val detektKotlinVersion = libs.versions.detekt.kotlin.get()
val composeRulesDetekt = libs.compose.rules.detekt

allprojects {
    apply(plugin = ktlintPluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.replace('\\', '/').contains("/build/") }
        }
    }
}

subprojects {
    apply(plugin = detektPluginId)

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
        // The default source (src/main/java, src/main/kotlin and the test equivalents) misses the
        // KMP and flavor source sets; the task keeps only *.kt and *.kts files.
        source.setFrom("src")
    }

    dependencies {
        "detektPlugins"(composeRulesDetekt)
    }

    // detekt 1.23.8 is compiled with Kotlin 2.0.21; keep the Kotlin Gradle plugin from aligning
    // its classpath to the build's Kotlin version (workaround from detekt.dev/docs/gettingstarted/gradle).
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(detektKotlinVersion)
            }
        }
    }
}
