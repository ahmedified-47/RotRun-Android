package com.ahmed.rotrun.logic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Android rows in blocker_lists / blocker_rules.
 * client_id is prefixed with "android:" and selection/config carry platform=android
 * so an iOS Family Controls row is never overwritten.
 */
object BlockBackup {
    const val PREFIX = "android:"

    data class AppRef(val packageName: String, val label: String)

    data class ListRow(
        val id: String,
        val name: String,
        val apps: List<AppRef>,
        val builtin: Boolean,
        val sort: Int,
    )

    data class RuleRow(
        val id: String,
        val name: String,
        val mode: String,
        val appListId: String,
        val startHour: Int,
        val endHour: Int,
        val limitMinutes: Int,
        val days: List<Int>,
        val apps: List<AppRef>,
        val modeApps: Map<String, List<AppRef>>,
    )

    fun clientId(localId: String): String = (PREFIX + localId).take(100)

    fun localId(clientId: String): String? {
        if (!clientId.startsWith(PREFIX)) return null
        return clientId.removePrefix(PREFIX).takeIf { it.isNotEmpty() }
    }

    fun isAndroidOwned(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return runCatching { JSONObject(raw).optString("platform") == "android" }.getOrDefault(false)
    }

    fun selectionData(apps: List<AppRef>): String {
        val packages = JSONArray()
        val labels = JSONObject()
        apps.forEach { app ->
            packages.put(app.packageName)
            if (app.label.isNotBlank()) labels.put(app.packageName, app.label.take(80))
        }
        return JSONObject()
            .put("platform", "android")
            .put("packages", packages)
            .put("labels", labels)
            .toString()
    }

    fun listJson(row: ListRow): JSONObject = JSONObject()
        .put("client_id", clientId(row.id))
        .put("name", row.name.trim().take(60).ifBlank { "RotRun Apps" })
        .put("is_builtin", row.builtin)
        .put("selection_data", selectionData(row.apps))
        .put("app_count", row.apps.size)
        .put("category_count", 0)
        .put("web_domain_count", 0)
        .put("sort_order", row.sort)
        .put("deleted_at", JSONObject.NULL)

    fun ruleJson(row: RuleRow): JSONObject {
        val schedule = JSONObject()
            .put("mode", row.mode)
            .put("startHour", row.startHour)
            .put("endHour", row.endHour)
            .put("days", JSONArray(row.days))
            .put("limitMinutes", row.limitMinutes)
        val modeApps = JSONObject()
        row.modeApps.forEach { (mode, apps) -> modeApps.put(mode, appArray(apps)) }
        val config = JSONObject()
            .put("platform", "android")
            .put("appListId", row.appListId)
            .put("packages", JSONArray().apply { row.apps.forEach { put(it.packageName) } })
            .put("labels", JSONObject().apply { row.apps.forEach { put(it.packageName, it.label) } })
            .put("modeApps", modeApps)
        val kind = if (row.id == "brick") "brick" else row.mode.take(40).ifBlank { "custom" }
        return JSONObject()
            .put("client_id", clientId(row.id))
            .put("list_client_id", clientId(row.appListId))
            .put("name", row.name.trim().take(60).ifBlank { "Blocker" })
            .put("kind", kind)
            .put("enabled", true)
            .put("schedule", schedule)
            .put("config", config)
            .put("deleted_at", JSONObject.NULL)
    }

    fun parseList(json: JSONObject): ListRow? {
        val id = localId(json.optString("client_id")) ?: return null
        val selection = json.optString("selection_data")
        if (!isAndroidOwned(selection)) return null
        return ListRow(
            id = id,
            name = json.optString("name").ifBlank { "RotRun Apps" },
            apps = appsFromSelection(selection),
            builtin = json.optBoolean("is_builtin"),
            sort = json.optInt("sort_order"),
        )
    }

    fun parseRule(json: JSONObject): RuleRow? {
        val id = localId(json.optString("client_id")) ?: return null
        val config = json.optJSONObject("config") ?: return null
        if (config.optString("platform") != "android") return null
        val schedule = json.optJSONObject("schedule")
        val modeApps = linkedMapOf<String, List<AppRef>>()
        val modeJson = config.optJSONObject("modeApps")
        if (modeJson != null) {
            val keys = modeJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                modeApps[key] = appsFromObjects(modeJson.optJSONArray(key))
            }
        }
        val mode = schedule?.optString("mode").orEmpty().ifBlank { "allDay" }
        val apps = appsFromLabels(config.optJSONArray("packages"), config.optJSONObject("labels"))
        val listId = localId(json.optString("list_client_id"))
            ?: config.optString("appListId").ifBlank { "list.brainmelt" }
        return RuleRow(
            id = id,
            name = json.optString("name").ifBlank { "Blocker" },
            mode = mode,
            appListId = listId,
            startHour = schedule?.optInt("startHour", 9) ?: 9,
            endHour = schedule?.optInt("endHour", 17) ?: 17,
            limitMinutes = schedule?.optInt("limitMinutes", 30) ?: 30,
            days = daysOf(schedule?.optJSONArray("days")),
            apps = apps.ifEmpty { modeApps[mode].orEmpty() },
            modeApps = modeApps,
        )
    }

    private fun appArray(apps: List<AppRef>): JSONArray {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(JSONObject().put("package", app.packageName).put("label", app.label))
        }
        return array
    }

    private fun appsFromSelection(raw: String): List<AppRef> {
        val json = JSONObject(raw)
        return appsFromLabels(json.optJSONArray("packages"), json.optJSONObject("labels"))
    }

    private fun appsFromLabels(packages: JSONArray?, labels: JSONObject?): List<AppRef> {
        if (packages == null) return emptyList()
        return buildList {
            for (index in 0 until packages.length()) {
                val pkg = packages.optString(index)
                if (pkg.isBlank()) continue
                val label = labels?.optString(pkg).orEmpty().ifBlank { pkg.substringAfterLast('.') }
                add(AppRef(pkg, label))
            }
        }
    }

    private fun appsFromObjects(array: JSONArray?): List<AppRef> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val pkg = item.optString("package")
                if (pkg.isBlank()) continue
                add(AppRef(pkg, item.optString("label").ifBlank { pkg.substringAfterLast('.') }))
            }
        }
    }

    private fun daysOf(array: JSONArray?): List<Int> {
        if (array == null || array.length() == 0) return (0..6).toList()
        return buildList {
            for (index in 0 until array.length()) add(array.optInt(index))
        }
    }
}
