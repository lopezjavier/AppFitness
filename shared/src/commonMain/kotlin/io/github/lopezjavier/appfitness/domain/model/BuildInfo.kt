package io.github.lopezjavier.appfitness.domain.model

private const val PROD_FLAVOR = "prod"

/** What the About screen shows about the installed build. Provided by the app module from its `BuildConfig`. */
data class BuildInfo(val appName: String, val versionName: String, val versionCode: Long, val flavor: String) {
    val isProd: Boolean get() = flavor == PROD_FLAVOR

    /** The flavor to append to the version line, or null for prod (and when the flavor is unknown). */
    val flavorLabel: String? get() = flavor.takeIf { it.isNotBlank() && !isProd }
}
