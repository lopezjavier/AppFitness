package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden image of [CoachChangeRow] in each state, one under the other on a 360 dp wide screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
class CoachChangeRowScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyState() {
        rule.setContent {
            AppTheme {
                Column(
                    Modifier.padding(Spacing.lg),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
                ) {
                    CoachChangeRow(
                        "Press de banca",
                        "Subida de 60 kg a 62,5 kg: completaste todas las series en el máximo del rango.",
                        CoachChangeRowState.Applied,
                        "applied",
                        actions = listOf(CoachChangeRowAction("Deshacer", "undo", {})),
                    )
                    CoachChangeRow(
                        "Remo con barra",
                        "El peso se mantiene mientras tengas una molestia abierta en este ejercicio.",
                        CoachChangeRowState.Kept,
                        "kept",
                    )
                    CoachChangeRow(
                        "Sentadilla",
                        "Bajada del 5 % de 80 kg a 76 kg: 2 sesiones seguidas sin llegar al mínimo del rango.",
                        CoachChangeRowState.Undone,
                        "undone",
                        detail = "Vuelve a 80 kg.",
                        statusText = "Deshecho",
                    )
                    CoachChangeRow(
                        "Press militar",
                        "La siguiente subida es de 22,5 kg (+12,5 %), más de lo recomendable de una vez.",
                        CoachChangeRowState.Pending,
                        "pending",
                        actions = listOf(
                            CoachChangeRowAction("Subir a 22,5 kg", "raise", {}, primary = true),
                            CoachChangeRowAction("Mantener y sumar repeticiones", "keep", {}),
                        ),
                    )
                    CoachChangeRow(
                        "Curl",
                        "Este ejercicio no tiene un rango de repeticiones.",
                        CoachChangeRowState.NoRange,
                        "norange",
                        actions = listOf(CoachChangeRowAction("Añadir un rango", "range", {})),
                    )
                    CoachChangeRow(
                        "Dominadas",
                        "La siguiente subida es de 5 kg.",
                        CoachChangeRowState.Decided,
                        "decided",
                        statusText = "Subida aceptada",
                    )
                }
            }
        }
        rule.onRoot().assertScreenshot("coach_change_row_states")
    }
}
