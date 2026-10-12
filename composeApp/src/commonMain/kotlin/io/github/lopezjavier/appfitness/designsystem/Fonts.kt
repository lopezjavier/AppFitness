package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.plus_jakarta_sans_bold
import io.github.lopezjavier.appfitness.composeapp.resources.plus_jakarta_sans_extrabold
import io.github.lopezjavier.appfitness.composeapp.resources.plus_jakarta_sans_regular
import io.github.lopezjavier.appfitness.composeapp.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans, static weights 400/600/700/800 (planning/05-design-system.md §3).
 *
 * Source: https://github.com/tokotype/PlusJakartaSans/releases/tag/2.7.1
 * (`PlusJakartaSans-2.7.1.zip`, `ttf/PlusJakartaSans-{Regular,SemiBold,Bold,ExtraBold}.ttf`, font version 2.071).
 * License: SIL Open Font License 1.1, shipped in `composeResources/files/licenses/PlusJakartaSans-OFL.txt`.
 * The font has `tnum`; it has no `onum`, so its default figures are already lining (`lnum` is a no-op).
 *
 * SHA-256:
 * - plus_jakarta_sans_regular.ttf   6bcfbb10639b8a206b4f8a0a1a29459e7255b1481c95e20d587dd1f1a4b24646
 * - plus_jakarta_sans_semibold.ttf  de75ddb3917c5a3f2b7d0cd65fcf63795710f2e1fd944199a73a01731e66fd43
 * - plus_jakarta_sans_bold.ttf      14972f80fb151f32fb7129c6ad1893f6dd2a9d8bac30c40508c99e7f289b6682
 * - plus_jakarta_sans_extrabold.ttf 37f9ae473fd96337e942de37ae34042016853ec2c6fdf2d87e1d93e737041d49
 */
@Composable
internal fun plusJakartaSans(): FontFamily {
    val regular = Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal)
    val semiBold = Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold)
    val extraBold = Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
    // Same FontFamily instance across recompositions, so AppTheme's remember(fontFamily) holds.
    return remember(regular, semiBold, bold, extraBold) { FontFamily(regular, semiBold, bold, extraBold) }
}
