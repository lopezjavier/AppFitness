@file:Suppress("MatchingDeclarationName") // The file holds the label and the pure superset helpers.

package io.github.lopezjavier.appfitness.domain.usecase

/** "Superserie A1": [letter] is the group's letter by order of appearance, [number] the 1-based place in the group. */
data class SupersetLabel(val letter: Char, val number: Int)

private const val LETTER_COUNT = 26

/**
 * The superset label of every exercise, given the group value of each (null = standalone). Only runs of two or more
 * consecutive equal values are supersets; letters go by order of appearance, so the group values themselves
 * (arbitrary numbers in storage) never show.
 */
fun supersetLabels(groups: List<Int?>): List<SupersetLabel?> {
    val labels = MutableList<SupersetLabel?>(groups.size) { null }
    var letterIndex = 0
    forEachRun(groups) { start, end ->
        val letter = 'A' + (letterIndex++ % LETTER_COUNT)
        for (i in start..end) labels[i] = SupersetLabel(letter, i - start + 1)
    }
    return labels
}

/** [list] with the item at [from] moved to [to]; an index outside the list changes nothing. */
fun <T> moveExercise(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices || to !in list.indices || from == to) return list
    val moved = list.toMutableList()
    moved.add(to, moved.removeAt(from))
    return moved
}

/**
 * Cleans a group column after an edit: only runs of two or more consecutive equal values stay grouped (a lone member
 * or a repeated value that is no longer consecutive is cleared) and the groups are renumbered 1, 2, 3...
 */
fun normalizeGroups(groups: List<Int?>): List<Int?> {
    val result = MutableList<Int?>(groups.size) { null }
    var nextGroup = 1
    forEachRun(groups) { start, end ->
        for (i in start..end) result[i] = nextGroup
        nextGroup++
    }
    return result
}

/** Puts the item at [index] and the next one in the same superset (merging groups); the last item is a no-op. */
fun groupWithNext(groups: List<Int?>, index: Int): List<Int?> {
    if (index !in 0 until groups.lastIndex) return groups
    val current = groups[index]
    val next = groups[index + 1]
    val result = groups.toMutableList()
    when {
        current == null && next == null -> {
            val fresh = (groups.filterNotNull().maxOrNull() ?: 0) + 1
            result[index] = fresh
            result[index + 1] = fresh
        }

        current == null -> result[index] = next

        else -> {
            if (next != null) result.replaceAll { if (it == next) current else it }
            result[index + 1] = current
        }
    }
    return normalizeGroups(result)
}

/** Removes the whole superset the item at [index] belongs to; an ungrouped item changes nothing. */
fun ungroup(groups: List<Int?>, index: Int): List<Int?> {
    val group = groups.getOrNull(index) ?: return groups
    return normalizeGroups(groups.map { if (it == group) null else it })
}

/** Calls [onRun] with the first and last index of every run of two or more consecutive equal non-null values. */
private inline fun forEachRun(groups: List<Int?>, onRun: (start: Int, end: Int) -> Unit) {
    var start = 0
    while (start < groups.size) {
        val value = groups[start]
        var end = start
        while (end + 1 < groups.size && groups[end + 1] == value) end++
        if (value != null && end > start) onRun(start, end)
        start = end + 1
    }
}
