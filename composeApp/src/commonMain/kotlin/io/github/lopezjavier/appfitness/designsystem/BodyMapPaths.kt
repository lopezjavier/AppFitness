package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.geometry.Offset
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** Which side of the body a silhouette shows. */
enum class BodyView { Front, Back }

/**
 * In-house vector silhouettes of [BodyHeatmap] (no external asset): polygons in a 100 x 200 unit space. Muscle
 * regions of a symmetric group come as several polygons (the first one is on the viewer's left). [MuscleGroup.FullBody]
 * has no region.
 */
object BodyMapPaths {
    const val WIDTH = 100f
    const val HEIGHT = 200f

    private const val HEAD_X = 50f
    private const val HEAD_Y = 12f
    private const val HEAD_RADIUS = 9f
    private const val HEAD_SIDES = 8

    private fun poly(vararg xy: Float): List<Offset> = xy.toList().chunked(2).map { (x, y) -> Offset(x, y) }

    private fun mirror(shape: List<Offset>): List<Offset> = shape.map { Offset(WIDTH - it.x, it.y) }

    private fun pair(left: List<Offset>): List<List<Offset>> = listOf(left, mirror(left))

    private val head: List<Offset> = (0 until HEAD_SIDES).map {
        val angle = 2 * kotlin.math.PI * it / HEAD_SIDES
        Offset(
            HEAD_X + HEAD_RADIUS * kotlin.math.cos(angle).toFloat(),
            HEAD_Y + HEAD_RADIUS * kotlin.math.sin(angle).toFloat(),
        )
    }
    private val torso =
        poly(34f, 26f, 66f, 26f, 72f, 30f, 70f, 70f, 66f, 100f, 66f, 118f, 34f, 118f, 34f, 100f, 30f, 70f, 28f, 30f)
    private val arm = poly(13f, 34f, 27f, 30f, 28f, 66f, 24f, 102f, 14f, 102f, 11f, 68f)
    private val leg = poly(34f, 112f, 49f, 112f, 48f, 150f, 46f, 196f, 36f, 196f, 34f, 150f)

    private val outlineParts: List<List<Offset>> = listOf(head, torso) + pair(arm) + pair(leg)

    private val shoulder = poly(13f, 35f, 22f, 29f, 32f, 28f, 34f, 34f, 32f, 46f, 22f, 48f, 14f, 44f)
    private val upperArm = poly(14f, 50f, 26f, 50f, 27f, 72f, 17f, 74f, 13f, 66f)
    private val forearm = poly(13f, 76f, 26f, 76f, 24f, 100f, 14f, 100f)
    private val calf = poly(37f, 160f, 47f, 160f, 46f, 190f, 38f, 190f)

    private val front: Map<MuscleGroup, List<List<Offset>>> = mapOf(
        MuscleGroup.Shoulders to pair(shoulder),
        MuscleGroup.Chest to pair(poly(35f, 32f, 49f, 32f, 49f, 56f, 40f, 58f, 34f, 52f)),
        MuscleGroup.Biceps to pair(upperArm),
        MuscleGroup.Forearms to pair(forearm),
        MuscleGroup.Core to listOf(poly(40f, 60f, 60f, 60f, 62f, 78f, 58f, 106f, 42f, 106f, 38f, 78f)),
        MuscleGroup.Quads to pair(poly(35f, 114f, 49f, 114f, 48f, 150f, 36f, 150f)),
        MuscleGroup.Calves to pair(poly(37f, 156f, 47f, 156f, 46f, 190f, 38f, 190f)),
    )

    private val back: Map<MuscleGroup, List<List<Offset>>> = mapOf(
        MuscleGroup.Shoulders to pair(shoulder),
        MuscleGroup.Back to listOf(
            poly(38f, 28f, 62f, 28f, 66f, 42f, 64f, 80f, 58f, 96f, 42f, 96f, 36f, 80f, 34f, 42f),
        ),
        MuscleGroup.Triceps to pair(upperArm),
        MuscleGroup.Forearms to pair(forearm),
        MuscleGroup.Glutes to pair(poly(35f, 100f, 49f, 100f, 49f, 122f, 36f, 122f, 34f, 112f)),
        MuscleGroup.Hamstrings to pair(poly(35f, 124f, 49f, 124f, 48f, 156f, 36f, 156f)),
        MuscleGroup.Calves to pair(calf),
    )

    /** Neutral body parts (head, torso, arms, legs), the same in both views. */
    fun outline(): List<List<Offset>> = outlineParts

    /** Muscle regions of [view] by group. */
    fun regions(view: BodyView): Map<MuscleGroup, List<List<Offset>>> = if (view == BodyView.Front) front else back
}
