package com.ahmed.rotrun.logic

import com.ahmed.rotrun.data.SavedState

/**
 * Drop one app list and the Android rules that apply it.
 * Brick Mode stays, with its apps cleared, so that list stops blocking.
 * Callers still sync only android: rows and leave iOS Family Controls blobs alone.
 */
fun SavedState.withoutAppList(listId: String): SavedState {
    if (appLists.none { it.id == listId }) return this
    val lists = appLists.filterNot { it.id == listId }
    val blockers = blockers.mapNotNull { blocker ->
        if (blocker.appListId != listId) blocker
        else if (blocker.id == "brick") blocker.copy(apps = emptyList(), modeApps = emptyMap())
        else null
    }
    return copy(appLists = lists, blockers = blockers)
}
