package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.AppList
import com.ahmed.rotrun.data.Blocker
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.logic.Presets
import com.ahmed.rotrun.logic.Scoring

@Composable
fun BlockScreen(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository) {
    var editing by remember { mutableStateOf<Blocker?>(null) }
    var editingList by remember { mutableStateOf<AppList?>(null) }
    var listsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Blocker?>(null) }
    var confirmList by remember { mutableStateOf<AppList?>(null) }
    val chrome = rot()
    val brick = state.blockers.firstOrNull { it.id == "brick" }
    LaunchedEffect(editing?.id, editingList?.id) {
        if (editing != null || editingList != null) repo.loadInstalled()
    }
    val scheduled = state.blockers.filter { it.id != "brick" }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Block", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = chrome.ink)
            Text(
                "+ Add Blocker",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.clip(CircleShape).background(chrome.blue).clickable {
                    editing = Blocker(id = "new-${System.currentTimeMillis()}", name = "New Blocker", mode = "allDay")
                }.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        SectionLabel("Blocking Now", chrome.muted)
        if (brick != null) {
            BlockerCard(brick, listName(state, brick), active = true, onClick = { editing = brick }, onList = { listsOpen = true })
        }
        if (scheduled.isNotEmpty()) {
            SectionLabel("Scheduled Blockers", chrome.muted)
            scheduled.forEach { blocker ->
                BlockerCard(blocker, listName(state, blocker), active = false, onClick = { editing = blocker }, onList = { listsOpen = true })
            }
        }
        SectionLabel("Presets for you", chrome.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Presets.catalog.forEach { preset ->
                val look = presetLook(preset.title)
                Column(
                    Modifier.width(210.dp).heightIn(min = 248.dp).clip(RoundedCornerShape(28.dp)).background(look.first).clickable {
                        editing = Blocker(
                            id = "preset:${preset.title}",
                            name = preset.title,
                            mode = "schedule",
                            startHour = preset.start,
                            endHour = preset.end,
                            days = preset.days,
                        )
                    }.padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CartoonBrain(look.second, Modifier.size(120.dp), animated = false)
                    Text(preset.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
                    Text(preset.subtitle, color = Color.Black.copy(alpha = 0.45f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    FlowRow(
                        Modifier.padding(top = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        preset.tags.forEach { tag ->
                            Text(
                                tag,
                                color = Color.Black.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        SectionLabel("App Lists", chrome.muted)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(chrome.card).padding(12.dp)) {
            state.appLists.forEach { list ->
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(chrome.inset).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(list.name, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(if (list.apps.isEmpty()) "No apps yet" else "${list.apps.size} apps", color = chrome.muted, fontSize = 13.sp)
                        }
                        Text("🚀", fontSize = 18.sp)
                    }
                    Text(
                        "Edit",
                        color = chrome.ink,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(chrome.pill).clickable { editingList = list }.padding(vertical = 10.dp),
                    )
                    Text(
                        "Delete",
                        color = chrome.danger,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().listDeleteBar().clickable { confirmList = list },
                    )
                }
            }
            Text(
                "+ New App List",
                color = chrome.ink,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(CircleShape).background(chrome.inset).clickable {
                    val n = state.appLists.size + 1
                    editingList = AppList(id = "list.$n", name = if (n == 1) "RotRun Apps" else "RotRun Apps $n")
                }.padding(vertical = 12.dp),
            )
        }
    }
    if (listsOpen) {
        BottomSheet(onDismiss = { listsOpen = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("App Lists", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                state.appLists.forEach { list ->
                    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(chrome.inset).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(list.name, color = chrome.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("🚀")
                        }
                        Text(
                            "Edit",
                            color = chrome.ink,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(chrome.pill).clickable {
                                listsOpen = false
                                editingList = list
                            }.padding(vertical = 10.dp),
                        )
                        Text(
                            "Delete",
                            color = chrome.danger,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().listDeleteBar().clickable {
                                listsOpen = false
                                confirmList = list
                            },
                        )
                    }
                }
                Text(
                    "+ New App List",
                    color = chrome.ink,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(CircleShape).background(chrome.inset).clickable {
                        listsOpen = false
                        val n = state.appLists.size + 1
                        editingList = AppList(id = "list.$n", name = if (n == 1) "RotRun Apps" else "RotRun Apps $n")
                    }.padding(vertical = 12.dp),
                )
            }
        }
    }
    editing?.let { draft ->
        BlockerEditor(
            draft,
            installed,
            listName(state, draft),
            onDismiss = { editing = null },
            onSave = { blocker ->
                val saved = repo.saveBlocker(blocker)
                if (saved) editing = null
                saved
            },
            onDelete = { confirmDelete = it },
        )
    }
    editingList?.let { list ->
        ListEditor(
            list,
            installed,
            onDismiss = { editingList = null },
        ) { list ->
            val saved = repo.saveList(list)
            if (saved) editingList = null
            saved
        }
    }
    confirmDelete?.let { blocker ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this blocker?") },
            confirmButton = {
                TextButton(onClick = { repo.deleteBlocker(blocker.id); confirmDelete = null; editing = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    confirmList?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmList = null },
            title = { Text("Delete ${list.name}?") },
            text = { Text("This removes the list and stops blocking its apps.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteList(list.id)
                    if (editingList?.id == list.id) editingList = null
                    confirmList = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmList = null }) { Text("Cancel") } },
        )
    }
}

private fun listName(state: SavedState, blocker: Blocker): String =
    state.appLists.firstOrNull { it.id == blocker.appListId }?.name ?: "RotRun Apps"

@Composable
private fun BlockerCard(blocker: Blocker, listName: String, active: Boolean, onClick: () -> Unit, onList: () -> Unit) {
    val chrome = rot()
    val subtitle = when (blocker.mode) {
        "schedule" -> "${Scoring.hourLabel(blocker.startHour)} - ${Scoring.hourLabel(blocker.endHour)} · ${Scoring.daysLabel(blocker.days)}"
        "dailyLimit" -> "${blocker.limitMinutes} min a day · ${Scoring.daysLabel(blocker.days)}"
        else -> "All Day"
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(26.dp)).background(chrome.card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (active) Color(0xFFD6E4FF) else chrome.inset), contentAlignment = Alignment.Center) {
                Icon(blockSymbol(blocker.symbol), contentDescription = null, tint = if (active) Color(0xFF5B6CFF) else chrome.muted)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(blocker.name, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = chrome.muted, fontSize = 13.sp)
            }
            if (active) {
                Text(
                    "●  Active",
                    color = chrome.green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(chrome.green.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            } else {
                Text("Today", color = chrome.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(chrome.inset).clickable(onClick = onList).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(listName, color = chrome.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("🚀", fontSize = 16.sp)
        }
    }
}

@Composable
private fun BlockerEditor(
    initial: Blocker,
    installed: List<TrackedApp>,
    listName: String,
    onDismiss: () -> Unit,
    onSave: (Blocker) -> Boolean,
    onDelete: (Blocker) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    var choosingIcon by remember { mutableStateOf(false) }
    val changed = draft != initial
    val chrome = rot()
    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    "Cancel",
                    color = chrome.blue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "Edit Block",
                    color = chrome.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                SheetLabel("NAME")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFD6E4FF)).clickable { choosingIcon = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(blockSymbol(draft.symbol), contentDescription = "Block icon", tint = Color(0xFF5B6CFF), modifier = Modifier.size(24.dp))
                    }
                    BasicTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        textStyle = TextStyle(color = chrome.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 12.dp).weight(1f).clip(RoundedCornerShape(14.dp)).background(chrome.inset).padding(16.dp),
                    )
                }
                SheetLabel("MODE")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(chrome.inset).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("allDay" to "All Day", "schedule" to "Schedule", "dailyLimit" to "Limit").forEach { (mode, label) ->
                        val on = draft.mode == mode
                        Text(
                            label,
                            color = if (on) Color.White else chrome.muted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) chrome.blue else Color.Transparent).clickable {
                                if (mode == draft.mode) return@clickable
                                val stash = draft.modeApps + (draft.mode to draft.apps)
                                draft = draft.copy(mode = mode, apps = stash[mode].orEmpty(), modeApps = stash)
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
                if (draft.mode == "schedule") {
                    Text("Timeframe", color = chrome.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Scoring.hourLabel(draft.startHour), color = chrome.ink, modifier = Modifier.chip().clickable { draft = draft.copy(startHour = (draft.startHour + 1) % 24) })
                        Text(Scoring.hourLabel(draft.endHour), color = chrome.ink, modifier = Modifier.chip().clickable { draft = draft.copy(endHour = (draft.endHour + 1) % 24) })
                    }
                }
                if (draft.mode == "dailyLimit") {
                    Text("${draft.limitMinutes} min a day, then blocked", color = chrome.ink, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
                    Row(Modifier.padding(top = 8.dp)) {
                        Text("−", color = chrome.ink, modifier = Modifier.chip().clickable { draft = draft.copy(limitMinutes = (draft.limitMinutes - 5).coerceIn(5, 240)) })
                        Text("+", color = chrome.ink, modifier = Modifier.padding(start = 8.dp).chip().clickable { draft = draft.copy(limitMinutes = (draft.limitMinutes + 5).coerceIn(5, 240)) })
                    }
                }
                SheetLabel("BLOCKED APPS")
                Text("Used for ${modeLabel(draft.mode)}. Each mode keeps its own list.", color = chrome.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                BlockedAppsRow(draft.apps) { picking = true }
                SheetLabel("ACTIVE DAYS")
                Column(
                    Modifier.padding(bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(chrome.field).padding(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEachIndexed { index, label ->
                            val on = index in draft.days
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (on) chrome.blue else chrome.pill).clickable {
                                    val days = if (on) draft.days - index else (draft.days + index).sorted()
                                    draft = draft.copy(days = days)
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (on) Color.White else chrome.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Weekdays" to (1..5).toList(), "Weekends" to listOf(0, 6), "Every day" to (0..6).toList()).forEachIndexed { index, (label, days) ->
                            if (index > 0) Text("·", color = chrome.muted, fontWeight = FontWeight.Bold)
                            val on = draft.days == days
                            Text(
                                label,
                                color = if (on) chrome.blue else chrome.ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.clickable { draft = draft.copy(days = days) },
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                PrimaryButton("Save Changes", enabled = changed) {
                    onSave(draft.copy(modeApps = draft.modeApps + (draft.mode to draft.apps)))
                }
                Text(
                    if (draft.mode == "schedule") "Delete Schedule" else "Delete Block",
                    color = chrome.danger,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).clickable { onDelete(draft) },
                )
            }
        }
    }
    if (choosingIcon) {
        AlertDialog(
            onDismissRequest = { choosingIcon = false },
            title = { Text("Block icon") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    blockSymbols.forEach { (id, icon) ->
                        val on = draft.symbol == id
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(if (on) chrome.blue else chrome.inset).clickable {
                                draft = draft.copy(symbol = id)
                                choosingIcon = false
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = id, tint = if (on) Color.White else chrome.ink)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingIcon = false }) { Text("Done") } },
        )
    }
    if (picking) {
        AppPickerDialog(
            apps = installed,
            selected = draft.apps,
            onDismiss = { picking = false },
            onSave = { chosen ->
                val next = draft.copy(apps = chosen, modeApps = draft.modeApps + (draft.mode to chosen))
                draft = next
                if (onSave(next)) picking = false
            },
        )
    }
}

@Composable
private fun ListEditor(
    initial: AppList,
    installed: List<TrackedApp>,
    onDismiss: () -> Unit,
    onSave: (AppList) -> Boolean,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val chrome = rot()
    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Edit List", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "Done",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(chrome.blue).clickable { onSave(draft) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Text("List Name", color = chrome.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            BasicTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                textStyle = TextStyle(color = chrome.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(chrome.field).padding(14.dp),
            )
            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Apps (${draft.apps.size})", color = chrome.ink, fontWeight = FontWeight.Bold)
                Text("Clear All", color = chrome.danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { draft = draft.copy(apps = emptyList()) })
            }
            if (draft.apps.isEmpty()) Text("No apps yet", color = chrome.muted, modifier = Modifier.padding(vertical = 8.dp))
            draft.apps.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(app.packageName, Modifier.size(36.dp))
                    Text(app.label, color = chrome.ink, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                }
            }
            Text(
                "Add More Apps",
                color = chrome.blue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 10.dp).clickable { picking = true },
            )
        }
    }
    if (picking) {
        AppPickerDialog(
            apps = installed,
            selected = draft.apps,
            onDismiss = { picking = false },
            onSave = { chosen ->
                val next = draft.copy(apps = chosen)
                draft = next
                if (onSave(next)) picking = false
            },
        )
    }
}

private fun presetLook(title: String): Pair<Color, BrainStyle> = when (title) {
    "Bedtime" -> Color(0xFFD6E8FA) to BrainStyle.Sleeping
    "Touch Grass" -> Color(0xFFD7F0DE) to BrainStyle.Flower
    "Evening Wind Down" -> Color(0xFFE0D6F5) to BrainStyle.Reading
    "Mindful Morning" -> Color(0xFFEDE0C7) to BrainStyle.Mug
    else -> Color(0xFFE0D6F0) to BrainStyle.Newspaper
}

@Composable
private fun BlockedAppsRow(apps: List<TrackedApp>, onClick: () -> Unit) {
    val chrome = rot()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(chrome.field).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (apps.isEmpty()) {
            Text("Choose apps", color = chrome.muted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        } else {
            val shown = apps.take(4)
            Box(Modifier.width((28 + (shown.size - 1) * 16).dp).height(28.dp)) {
                shown.forEachIndexed { index, app ->
                    AppGlyph(
                        app.packageName,
                        Modifier.padding(start = (index * 16).dp).size(28.dp).border(2.dp, chrome.field, RoundedCornerShape(8.dp)),
                    )
                }
            }
            Text(
                "${apps.size} apps",
                color = chrome.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            Text("›", color = chrome.muted, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        color = rot().muted,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.1.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    )
}

private val blockSymbols = listOf(
    "phone" to Icons.Default.PhoneAndroid,
    "bed" to Icons.Default.Bedtime,
    "star" to Icons.Default.Star,
    "lock" to Icons.Default.Lock,
)

private fun blockSymbol(id: String) = blockSymbols.firstOrNull { it.first == id }?.second ?: Icons.Default.PhoneAndroid

private fun modeLabel(mode: String) = when (mode) {
    "schedule" -> "Schedule"
    "dailyLimit" -> "Daily Limit"
    else -> "All Day"
}

@Composable
private fun Modifier.listDeleteBar(): Modifier {
    val chrome = rot()
    val shape = RoundedCornerShape(12.dp)
    return this.then(
        Modifier.border(1.dp, chrome.muted, shape).clip(shape).padding(vertical = 10.dp),
    )
}

@Composable
private fun Modifier.chip(): Modifier {
    val chrome = rot()
    return this.then(
        Modifier.clip(RoundedCornerShape(12.dp)).background(chrome.inset).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
