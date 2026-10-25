package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.app.InterceptTopBarBack
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_comment_counter
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_comment_label
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_comment_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_error_dish
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_error_manual
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_error_network
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_error_product
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_error_unavailable
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_estimating
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_preview_image_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_retake
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_use
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_camera
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppButtonDefaults
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MaxImageHeight = Spacing.xl * 12

/** A full-screen step of the flow: opaque over the search, back goes one step back (never while estimating). */
@Composable
internal fun FullScreenStep(
    tag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onBack,
    )
    InterceptTopBarBack(enabled = true, onBack = onBack)
    Box(
        modifier.fillMaxSize().background(AppTheme.colors.surfaceBase).testTag(tag),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}

/**
 * Preview of the captured photo with "Repetir" and "Usar foto" pinned at the bottom. In dish mode an optional comment
 * (max [MAX_PHOTO_COMMENT] characters) sits under the photo. [readPhoto] returns the JPEG bytes behind a handle.
 */
@Composable
fun PhotoPreview(
    handle: String,
    state: FoodPhotoUiState,
    readPhoto: suspend (String) -> ByteArray,
    onEvent: (FoodPhotoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FullScreenStep("food_photo_preview", { onEvent(FoodPhotoEvent.Back) }, modifier) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                PhotoImage(handle, readPhoto)
                if (state.mode == FoodPhotoMode.Dish) CommentField(state.comment, onEvent)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    text = stringResource(Res.string.food_photo_retake),
                    onClick = { onEvent(FoodPhotoEvent.Retake) },
                    modifier = Modifier.widthIn(min = AppButtonDefaults.CompactMinWidth)
                        .testTag("food_photo_preview_retake"),
                )
                PrimaryButton(
                    text = stringResource(Res.string.food_photo_use),
                    onClick = { onEvent(FoodPhotoEvent.UsePhoto) },
                    modifier = Modifier.weight(1f).testTag("food_photo_preview_use"),
                    enabled = !state.isOffline,
                )
            }
        }
    }
}

@Composable
internal fun PhotoImage(handle: String, readPhoto: suspend (String) -> ByteArray) {
    val currentRead by rememberUpdatedState(readPhoto)
    val bitmap by produceState<ImageBitmap?>(null, handle) {
        value = runCatching { currentRead(handle).decodeToImageBitmap() }.getOrNull()
    }
    val description = stringResource(Res.string.food_photo_preview_image_cd)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = MaxImageHeight)
            .clip(AppShapes.md)
            .background(AppTheme.colors.surfaceOverlay)
            .semantics { contentDescription = description }
            .testTag("food_photo_preview_image"),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = MaxImageHeight),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_photo_camera),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(Spacing.xl).size(Spacing.xl * 2),
            )
        }
    }
}

@Composable
private fun CommentField(comment: String, onEvent: (FoodPhotoEvent) -> Unit) {
    val counter = stringResource(Res.string.food_photo_comment_counter, comment.length, MAX_PHOTO_COMMENT)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AppTextField(
            value = comment,
            onValueChange = { onEvent(FoodPhotoEvent.CommentChanged(it)) },
            label = stringResource(Res.string.food_photo_comment_label),
            placeholder = stringResource(Res.string.food_photo_comment_placeholder),
            maxLength = MAX_PHOTO_COMMENT,
            testTagPrefix = "food_photo_comment_input",
            stateDescription = counter,
        )
        Text(
            text = counter,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().testTag("food_photo_comment_input_counter"),
        )
    }
}

/** "Estimando…": progress and text only; back is disabled and nothing can be cancelled. */
@Composable
fun PhotoEstimating(modifier: Modifier = Modifier) {
    FullScreenStep("food_photo_estimating", onBack = {}, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Decorative: the text carries the meaning.
            CircularProgressIndicator(color = AppTheme.colors.neonLime)
            Text(
                text = stringResource(Res.string.food_photo_estimating),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
        }
    }
}

/** The estimate failed: the message of [kind], "Reintentar" with the same photo and "Crear manualmente". */
@Composable
fun PhotoError(kind: FoodPhotoErrorKind, onEvent: (FoodPhotoEvent) -> Unit, modifier: Modifier = Modifier) {
    val message = stringResource(
        when (kind) {
            FoodPhotoErrorKind.NotRecognizedDish -> Res.string.food_photo_error_dish
            FoodPhotoErrorKind.NotRecognizedProduct -> Res.string.food_photo_error_product
            FoodPhotoErrorKind.Network -> Res.string.food_photo_error_network
            FoodPhotoErrorKind.Unavailable -> Res.string.food_photo_error_unavailable
        },
    )
    FullScreenStep("food_photo_error_screen", { onEvent(FoodPhotoEvent.Back) }, modifier) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            ErrorState(
                message = message,
                action = AppStateAction(
                    label = stringResource(Res.string.state_retry),
                    onClick = { onEvent(FoodPhotoEvent.Retry) },
                ),
                secondaryAction = AppStateAction(
                    label = stringResource(Res.string.food_photo_error_manual),
                    onClick = { onEvent(FoodPhotoEvent.CreateManually) },
                ),
                testTagPrefix = "food_photo_error",
                actionTestTag = "food_photo_error_retry",
                secondaryActionTestTag = "food_photo_error_manual",
            )
        }
    }
}
