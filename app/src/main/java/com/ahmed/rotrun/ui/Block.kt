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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
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
import com.ahmed.rotrun.logic.BlockRules
import com.ahmed.rotrun.logic.Presets
import com.ahmed.rotrun.logic.Scoring

@Composable
fun BlockScreen(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository) {
    var editing by remember { mutableStateOf<Blocker?>(null) }
    var editingList by remember { mutableStateOf<AppList?>(null) }
    var listsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Blocker?>(null) }
    val chrome = rot()
    val brick = state.blockers.firstOrNull { it.id == "brick" }
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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Presets.catalog.forEach { preset ->
                val look = presetLook(preset.title)
                Column(
                    Modifier.width(196.dp).height(248.dp).clip(RoundedCornerShape(28.dp)).background(look.first).clickable {
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
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            exceedsCap = { proposed, exceptBlocker, exceptList -> repo.wouldExceedFreeCap(proposed, exceptBlocker, exceptList) },
            onUpgrade = { repo.toast(BlockRules.FREE_LIMIT_MESSAGE); repo.requestPaywall() },
        )
    }
    editingList?.let { list ->
        ListEditor(
            list,
            installed,
            onDismiss = { editingList = null },
            exceedsCap = { proposed -> repo.wouldExceedFreeCap(proposed, exceptList = list.id) },
            onUpgrade = { repo.toast(BlockRules.FREE_LIMIT_MESSAGE); repo.requestPaywall() },
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
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = if (active) Color(0xFF5B6CFF) else chrome.muted)
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
    exceedsCap: (Set<String>, String?, String?) -> Boolean,
    onUpgrade: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val chrome = rot()
    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFD6E4FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF5B6CFF), modifier = Modifier.size(32.dp))
            }
            BasicTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                textStyle = TextStyle(color = chrome.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(chrome.inset).padding(16.dp),
            )
            Row(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("allDay" to "All Day", "schedule" to "Schedule", "dailyLimit" to "Daily Limit").forEach { (mode, label) ->
                    val on = draft.mode == mode
                    Text(
                        label,
                        color = if (on) Color.White else chrome.muted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(if (on) chrome.blue else Color.Transparent).clickable {
                            if (mode == draft.mode) return@clickable
                            val stash = draft.modeApps + (draft.mode to draft.apps)
                            draft = draft.copy(mode = mode, apps = stash[mode].orEmpty(), modeApps = stash)
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Text("Blocked Apps", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
            Text("Used for ${modeLabel(draft.mode)}. Each mode keeps its own list.", color = chrome.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(chrome.inset).clickable { picking = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(listName, color = chrome.ink, fontWeight = FontWeight.Bold)
                    Text(
                        if (draft.apps.isEmpty()) "No apps yet" else "${draft.apps.size} apps",
                        color = chrome.muted,
                        fontSize = 13.sp,
                    )
                }
                Text("🚀", modifier = Modifier.padding(end = 8.dp))
                Text("›", color = chrome.muted, fontWeight = FontWeight.Bold)
            }
            if (draft.mode == "schedule") {
                Text("Timeframe", color = chrome.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp).fillMaxWidth())
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
            Text("Active Days", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp).fillMaxWidth())
            Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Weekdays" to (1..5).toList(), "Weekends" to listOf(0, 6), "All" to (0..6).toList()).forEach { (label, days) ->
                    val on = draft.days == days
                    Text(
                        label,
                        color = if (on) Color.White else chrome.muted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(if (on) chrome.blue else Color.Transparent).clickable { draft = draft.copy(days = days) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEachIndexed { index, label ->
                    val on = index in draft.days
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (on) chrome.blue else chrome.inset).clickable {
                            val days = if (on) draft.days - index else (draft.days + index).sorted()
                            draft = draft.copy(days = days)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (on) Color.White else chrome.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            PrimaryButton("Save Changes") { onSave(draft.copy(modeApps = draft.modeApps + (draft.mode to draft.apps))) }
            Text(
                if (draft.mode == "schedule") "Delete Schedule" else "Delete Block",
                color = chrome.danger,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp).clickable { onDelete(draft) },
            )
        }
    }
    if (picking) {
        val reserved = draft.modeApps.filterKeys { it != draft.mode }.values.flatten().map { it.packageName }.toSet()
        AppPickerDialog(
            apps = installed,
            selected = draft.apps,
            onDismiss = { picking = false },
            onSave = { chosen ->
                val next = draft.copy(apps = chosen, modeApps = draft.modeApps + (draft.mode to chosen))
                draft = next
                if (onSave(next)) picking = false
            },
            exceedsCap = { proposed ->
                exceedsCap(proposed + reserved, draft.id, if (draft.id == "brick") draft.appListId else null)
            },
            onUpgrade = {
                picking = false
                onUpgrade()
            },
        )
    }
}

@Composable
private fun ListEditor(
    initial: AppList,
    installed: List<TrackedApp>,
    onDismiss: () -> Unit,
    exceedsCap: (Set<String>) -> Boolean,
    onUpgrade: () -> Unit,
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
            exceedsCap = exceedsCap,
            onUpgrade = { picking = false; onUpgrade() },
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

private fun modeLabel(mode: String) = when (mode) {
    "schedule" -> "Schedule"
    "dailyLimit" -> "Daily Limit"
    else -> "All Day"
}

@Composable
private fun Modifier.chip(): Modifier {
    val chrome = rot()
    return this.then(
        Modifier.clip(RoundedCornerShape(12.dp)).background(chrome.inset).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
