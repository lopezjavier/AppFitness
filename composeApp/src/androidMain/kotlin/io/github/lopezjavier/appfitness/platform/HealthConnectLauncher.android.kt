package io.github.lopezjavier.appfitness.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

@Composable
actual fun rememberHealthConnectLauncher(onResult: (weightReadGranted: Boolean) -> Unit): HealthConnectLauncher {
    val context = LocalContext.current
    val latestOnResult = rememberUpdatedState(onResult)
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> latestOnResult.value(HealthPermission.getReadPermission(WeightRecord::class) in granted) }
    return remember(context, permissionLauncher) {
        object : HealthConnectLauncher {
            override fun requestPermissions() = permissionLauncher.launch(healthConnectPermissions())

            override fun openInstall() = openPlayStore(context)
        }
    }
}

/** Opens the Play Store app on Health Connect, or the web listing when no store app handles `market://`. */
private fun openPlayStore(context: Context) {
    val market = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
    val web = Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, market).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
