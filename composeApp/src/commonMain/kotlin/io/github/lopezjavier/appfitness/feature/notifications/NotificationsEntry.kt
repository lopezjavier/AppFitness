package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_notifications
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_bell_cd
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_title
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The entry to the notification center that the shell offers to the tab roots: the pending [count] and the opener. */
@Immutable
class NotificationsEntry(val count: Int, val onOpen: () -> Unit)

/** Provided by the shell; null (previews, isolated tests) hides the bell. */
val LocalNotificationsEntry = compositionLocalOf<NotificationsEntry?> { null }

@Composable
private fun bellDescription(count: Int): String = if (count > 0) {
    pluralStringResource(Res.plurals.notifications_bell_cd, count, count)
} else {
    stringResource(Res.string.notifications_title)
}

/** The bell for the top bar of a tab root, or null when the shell offers no entry. */
@Composable
fun notificationsBarAction(testTag: String): AppTopBarAction? {
    val entry = LocalNotificationsEntry.current ?: return null
    val description = bellDescription(entry.count)
    return remember(entry, description, testTag) {
        AppTopBarAction(Res.drawable.ic_notifications, description, entry.onOpen, testTag, badgeCount = entry.count)
    }
}

/** The bell for a tab header that draws its own row (Diario, Entreno); same entry, description and badge. */
@Composable
fun NotificationsBell(testTag: String) {
    val entry = LocalNotificationsEntry.current ?: return
    AppIconButton(
        contentDescription = bellDescription(entry.count),
        icon = Res.drawable.ic_notifications,
        testTag = testTag,
        onClick = entry.onOpen,
        badgeCount = entry.count,
    )
}
