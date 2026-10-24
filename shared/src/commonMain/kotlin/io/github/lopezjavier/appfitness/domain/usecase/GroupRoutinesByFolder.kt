package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineGroup
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary

/**
 * Folders first, in folder order (empty folders kept), then the no-folder group last. The routines of each
 * group keep their incoming order. A routine pointing to an unknown folder falls into the no-folder group.
 */
fun groupRoutines(folders: List<RoutineFolder>, routines: List<RoutineSummary>): List<RoutineGroup> {
    val folderIds = folders.mapTo(HashSet()) { it.id }
    val byFolder = routines.filter { it.folderId in folderIds }.groupBy { it.folderId }
    val folderGroups = folders.map { RoutineGroup(it, byFolder[it.id].orEmpty()) }
    return folderGroups + RoutineGroup(null, routines.filter { it.folderId !in folderIds })
}

/** Moves [id] by [delta] positions inside [ids] (clamped to the ends); unknown ids or no movement return [ids]. */
fun moveWithinGroup(ids: List<String>, id: String, delta: Int): List<String> {
    val from = ids.indexOf(id)
    val to = (from + delta).coerceIn(0, ids.lastIndex)
    return if (from < 0 || to == from) ids else ids.toMutableList().also { it.add(to, it.removeAt(from)) }
}
