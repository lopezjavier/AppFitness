package io.github.lopezjavier.appfitness.platform

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.about_licenses_row
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberOpenSourceLicensesLauncher(): OpenSourceLicensesLauncher {
    val context = LocalContext.current
    val title = stringResource(Res.string.about_licenses_row)
    return remember(context, title) {
        OpenSourceLicensesLauncher {
            OssLicensesMenuActivity.setActivityTitle(title)
            context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
        }
    }
}
