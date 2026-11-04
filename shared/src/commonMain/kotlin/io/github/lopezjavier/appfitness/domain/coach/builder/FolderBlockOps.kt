package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise

/** "{folder} · bloque", then "... 2", "... 3" while the name is taken (Q-B3 / R-15). */
object FolderBlockName {
    private const val SUFFIX = " · bloque"
    private const val FALLBACK = "Carpeta"

    fun unique(folderName: String, existingNames: Set<String>): String {
        val base = folderName.trim().ifEmpty { FALLBACK } + SUFFIX
        val taken = existingNames.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }
}

/** One edit made on the folder copy; the list of them is what survives process death (ADR-40). */
sealed interface FolderBlockOp {
    data class Add(val routineIndex: Int, val exerciseId: String) : FolderBlockOp

    data class Swap(val routineIndex: Int, val exerciseIndex: Int, val exerciseId: String) : FolderBlockOp

    data class RepFix(val routineIndex: Int, val exerciseIndex: Int) : FolderBlockOp

    data class Remove(val routineIndex: Int, val exerciseIndex: Int) : FolderBlockOp
}

/**
 * String codec for the op list (`add 0 id;swap 0 1 id;rep 0 2;remove 0 3`) and the replay on a freshly loaded
 * folder.
 */
object FolderBlockOps {
    private const val SEP = ";"
    private const val ADD_PARTS = 3
    private const val SWAP_PARTS = 4
    private const val INDEX_PARTS = 3
    private const val SWAP_ID = 3

    fun encode(ops: List<FolderBlockOp>): String = ops.joinToString(SEP) {
        when (it) {
            is FolderBlockOp.Add -> "add ${it.routineIndex} ${it.exerciseId}"
            is FolderBlockOp.Swap -> "swap ${it.routineIndex} ${it.exerciseIndex} ${it.exerciseId}"
            is FolderBlockOp.RepFix -> "rep ${it.routineIndex} ${it.exerciseIndex}"
            is FolderBlockOp.Remove -> "remove ${it.routineIndex} ${it.exerciseIndex}"
        }
    }

    /** Malformed ops are skipped. */
    fun decode(raw: String): List<FolderBlockOp> = raw.split(SEP).mapNotNull(::decodeOne)

    private fun decodeOne(part: String): FolderBlockOp? {
        val p = part.trim().split(" ")
        val ri = p.getOrNull(1)?.toIntOrNull()
        return when {
            ri == null -> null

            p[0] == "add" && p.size == ADD_PARTS -> FolderBlockOp.Add(ri, p[2])

            p[0] == "swap" && p.size == SWAP_PARTS ->
                p[2].toIntOrNull()?.let { FolderBlockOp.Swap(ri, it, p[SWAP_ID]) }

            p[0] == "rep" && p.size == INDEX_PARTS -> p[2].toIntOrNull()?.let { FolderBlockOp.RepFix(ri, it) }

            p[0] == "remove" && p.size == INDEX_PARTS -> p[2].toIntOrNull()?.let { FolderBlockOp.Remove(ri, it) }

            else -> null
        }
    }

    /** Applies [ops] in order on [base]; an op that is invalid at its turn (unknown exercise) changes nothing. */
    fun replay(
        base: FolderBlockDraft,
        ops: List<FolderBlockOp>,
        catalogue: List<CatalogExercise>,
        history: Map<String, ExerciseHistory>,
    ): FolderBlockDraft {
        val byId = catalogue.associateBy { it.id }
        return ops.fold(base) { draft, op -> apply(draft, op, byId, history) }
    }

    fun apply(
        draft: FolderBlockDraft,
        op: FolderBlockOp,
        byId: Map<String, CatalogExercise>,
        history: Map<String, ExerciseHistory>,
    ): FolderBlockDraft = when (op) {
        is FolderBlockOp.Add -> byId[op.exerciseId]?.let { draft.add(op.routineIndex, it, history[op.exerciseId]) }

        is FolderBlockOp.Swap -> byId[op.exerciseId]?.let {
            draft.swap(op.routineIndex, op.exerciseIndex, it, history[op.exerciseId])
        }

        is FolderBlockOp.RepFix -> draft.applyStrengthRepFix(op.routineIndex, op.exerciseIndex)

        is FolderBlockOp.Remove -> draft.removeAdded(op.routineIndex, op.exerciseIndex)
    } ?: draft
}
