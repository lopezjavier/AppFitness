@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.name_sheet_duplicate
import org.jetbrains.compose.resources.stringResource

/** Pure rules of the [NameSheet] (R-15): trimmed names, uniqueness ignoring case and surrounding spaces. */
object NameSheetDefaults {
    const val TEST_TAG = "name_sheet"

    /** The name that is saved: surrounding spaces removed, internal spaces kept. */
    fun normalize(name: String): String = name.trim()

    /**
     * Whether [candidate] equals one of [existing] ignoring case and surrounding spaces. [ignore] is the name
     * being renamed, so keeping it unchanged is allowed.
     */
    fun isDuplicate(candidate: String, existing: Collection<String>, ignore: String? = null): Boolean {
        val key = key(candidate)
        val ignored = ignore?.let(::key)
        return existing.any { key(it) == key && key(it) != ignored }
    }

    /** Saving needs a name that is not blank and not [duplicate]. */
    fun canSave(name: String, duplicate: Boolean): Boolean = normalize(name).isNotEmpty() && !duplicate

    private fun key(name: String): String = name.trim().lowercase()
}

/**
 * Name sheet (R-15): an [AppEditSheet] with [SheetCommit.Save] and one text field that takes focus at once.
 * "Hecho" on the keyboard and the footer button share one save that runs only when the name is valid, only once
 * per edit and not while [saving]; it calls [onSave] with the trimmed name. [existingNames] are the names that are
 * taken; a rename passes its current name as [initialName], which stays allowed. After the first edit a duplicate
 * shows "Ya existe <[itemLabel]> con ese nombre" under the field ([itemLabel] is "una carpeta", "una comida"...)
 * and "Guardar" stays disabled. The check is client side only. The typed text survives recreation. Tags:
 * `<prefix>`, `<prefix>_input` (+ `_error`), `<prefix>_commit`, `<prefix>_close`.
 *
 * [canSubmit] false (for example offline) keeps "Guardar" disabled whatever the name; [extra] adds content under the
 * field (inside the sheet's scrolling body).
 *
 * The caller follows the [AppEditSheet] contract: close and fire the Confirm haptic after a successful save.
 */
@Suppress("LongParameterList") // Sheet contract of T-119.
@Composable
fun NameSheet(
    title: String,
    label: String,
    itemLabel: String,
    existingNames: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    saving: Boolean = false,
    commitHint: String? = null,
    maxLength: Int = AppTextFieldDefaults.DEFAULT_MAX_LENGTH,
    testTagPrefix: String = NameSheetDefaults.TEST_TAG,
    canSubmit: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(saving) { if (!saving) submitted = false }
    val duplicate = NameSheetDefaults.isDuplicate(name, existingNames, ignore = initialName.ifBlank { null })
    val canSave = NameSheetDefaults.canSave(name, duplicate) && canSubmit
    val save = {
        if (canSave && !saving && !submitted) {
            submitted = true
            onSave(NameSheetDefaults.normalize(name))
        }
    }
    AppEditSheet(
        title = title,
        commit = SheetCommit.Save,
        onCommit = save,
        onDismiss = onDismiss,
        modifier = modifier,
        commitEnabled = canSave,
        saving = saving,
        commitHint = commitHint,
        testTagPrefix = testTagPrefix,
    ) {
        AppTextField(
            value = name,
            onValueChange = {
                name = it
                edited = true
                submitted = false
            },
            label = label,
            maxLength = maxLength,
            errorText = if (edited && duplicate) stringResource(Res.string.name_sheet_duplicate, itemLabel) else null,
            testTagPrefix = "${testTagPrefix}_input",
            imeAction = ImeAction.Done,
            onImeAction = { save() },
            capitalization = KeyboardCapitalization.Sentences,
            autofocus = true,
        )
        extra?.invoke(this)
    }
}
