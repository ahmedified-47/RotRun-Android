package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    var confirmDelete by remember { mutableStateOf<Blocker?>(null) }
    val brick = state.blockers.firstOrNull { it.id == "brick" }
    val scheduled = state.blockers.filter { it.id != "brick" }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Block", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "+ Add Blocker",
                color = Melt.Action,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    editing = Blocker(id = "new-${System.currentTimeMillis()}", name = "New Blocker", mode = "allDay")
                },
            )
        }
        SectionLabel("Blocking Now", Melt.Ink)
        if (brick != null) BlockerCard(brick, active = true) { editing = brick }
        if (scheduled.isNotEmpty()) {
            SectionLabel("Scheduled Blockers", Melt.Ink)
            scheduled.forEach { blocker -> BlockerCard(blocker, active = false) { editing = blocker } }
        }
        SectionLabel("Presets for you", Melt.Ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Presets.catalog.forEach { preset ->
                Column(
                    Modifier.width(180.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable {
                        editing = Blocker(
                            id = "preset:${preset.title}",
                            name = preset.title,
                            mode = "schedule",
                            startHour = preset.start,
                            endHour = preset.end,
                            days = preset.days,
                        )
                    }.padding(14.dp),
                ) {
                    Text(preset.title, fontWeight = FontWeight.Bold)
                    Text(preset.subtitle, color = Melt.Muted, fontSize = 13.sp)
                    Text(preset.tags.joinToString(" · "), color = Melt.Action, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        SectionLabel("App Lists", Melt.Ink)
        state.appLists.forEach { list ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { editingList = list }.padding(14.dp),
            ) {
                Text(list.name, fontWeight = FontWeight.Bold)
                Text(if (list.apps.isEmpty()) "No apps yet" else "${list.apps.size} apps", color = Melt.Muted)
            }
        }
        Text(
            "New App List",
            color = Melt.Action,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).clickable {
                val n = state.appLists.size + 1
                editingList = AppList(id = "list.$n", name = if (n == 1) "RotRun Apps" else "RotRun Apps $n")
            },
        )
    }
    editing?.let { draft ->
        BlockerEditor(draft, installed, onDismiss = { editing = null }, onSave = { repo.saveBlocker(it); editing = null }, onDelete = { confirmDelete = it })
    }
    editingList?.let { list ->
        ListEditor(list, installed, onDismiss = { editingList = null }) { repo.saveList(it); editingList = null }
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

@Composable
private fun BlockerCard(blocker: Blocker, active: Boolean, onClick: () -> Unit) {
    val subtitle = when (blocker.mode) {
        "schedule" -> "${Scoring.hourLabel(blocker.startHour)} - ${Scoring.hourLabel(blocker.endHour)} · ${Scoring.daysLabel(blocker.days)}"
        "dailyLimit" -> "${blocker.limitMinutes} min a day · ${Scoring.daysLabel(blocker.days)}"
        else -> "All Day"
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(blocker.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(if (active) "● Active" else "Today", color = if (active) Color(0xFF34C759) else Melt.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(subtitle, color = Melt.Muted)
        Text(if (blocker.apps.isEmpty()) "No apps in this list" else "${blocker.apps.size} apps", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BlockerEditor(
    initial: Blocker,
    installed: List<TrackedApp>,
    onDismiss: () -> Unit,
    onSave: (Blocker) -> Unit,
    onDelete: (Blocker) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Melt.Grouped).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cancel", color = Melt.Action, modifier = Modifier.clickable(onClick = onDismiss))
            Text("Save", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSave(draft) })
        }
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("allDay" to "All Day", "schedule" to "Schedule", "dailyLimit" to "Daily Limit").forEach { (mode, label) ->
                Text(
                    label,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (draft.mode == mode) Melt.Action else Color.White).clickable { draft = draft.copy(mode = mode) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = if (draft.mode == mode) Color.White else Melt.Ink,
                )
            }
        }
        Text("Blocked Apps", fontWeight = FontWeight.Bold)
        Text("Used for ${modeLabel(draft.mode)}. Each mode keeps its own list.", color = Melt.Muted, fontSize = 13.sp)
        Text(if (draft.apps.isEmpty()) "No apps in this list" else draft.apps.joinToString { it.label }, modifier = Modifier.padding(vertical = 6.dp))
        Text("Add More Apps", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { picking = true })
        if (draft.mode == "schedule") {
            Text("Timeframe", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Scoring.hourLabel(draft.startHour), modifier = Modifier.chip().clickable { draft = draft.copy(startHour = (draft.startHour + 1) % 24) })
                Text(Scoring.hourLabel(draft.endHour), modifier = Modifier.chip().clickable { draft = draft.copy(endHour = (draft.endHour + 1) % 24) })
            }
        }
        if (draft.mode == "dailyLimit") {
            Text("${draft.limitMinutes} min a day, then blocked", modifier = Modifier.padding(top = 8.dp))
            Row {
                Text("−", modifier = Modifier.chip().clickable { draft = draft.copy(limitMinutes = (draft.limitMinutes - 5).coerceIn(5, 240)) })
                Text("+", modifier = Modifier.padding(start = 8.dp).chip().clickable { draft = draft.copy(limitMinutes = (draft.limitMinutes + 5).coerceIn(5, 240)) })
            }
        }
        Text("Active Days", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEachIndexed { index, label ->
                val on = index in draft.days
                Text(label, color = if (on) Color.White else Melt.Ink, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Melt.Action else Color.White).clickable {
                    val days = if (on) draft.days - index else (draft.days + index).sorted()
                    draft = draft.copy(days = days)
                }.padding(8.dp))
            }
        }
        PrimaryButton("Save Changes") { onSave(draft) }
        Text(
            if (draft.mode == "schedule") "Delete Schedule" else "Delete Block",
            color = Melt.House,
            modifier = Modifier.padding(top = 12.dp).clickable { onDelete(draft) },
        )
        if (picking) {
            AppPicker(installed, draft.apps.map { it.packageName }.toSet()) { chosen ->
                draft = draft.copy(apps = chosen)
                picking = false
            }
        }
    }
}

@Composable
private fun ListEditor(initial: AppList, installed: List<TrackedApp>, onDismiss: () -> Unit, onSave: (AppList) -> Unit) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Edit List", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Done", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSave(draft) })
        }
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("List Name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text("Apps (${draft.apps.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("Clear All", color = Melt.House, modifier = Modifier.clickable { draft = draft.copy(apps = emptyList()) })
        Text(if (draft.apps.isEmpty()) "No apps yet" else draft.apps.joinToString { it.label })
        Text("Add More Apps", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { picking = true })
        Text("Cancel", modifier = Modifier.padding(top = 8.dp).clickable(onClick = onDismiss))
        if (picking) AppPicker(installed, draft.apps.map { it.packageName }.toSet()) { draft = draft.copy(apps = it); picking = false }
    }
}

@Composable
private fun AppPicker(installed: List<TrackedApp>, selected: Set<String>, onDone: (List<TrackedApp>) -> Unit) {
    var chosen by remember { mutableStateOf(selected) }
    Column(Modifier.fillMaxWidth().background(Melt.Grouped).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Add Apps", fontWeight = FontWeight.Bold)
            Text("Save", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                onDone(installed.filter { it.packageName in chosen })
            })
        }
        installed.take(50).forEach { app ->
            val on = app.packageName in chosen
            Text(
                if (on) "✓ ${app.label}" else app.label,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                    chosen = if (on) chosen - app.packageName else chosen + app.packageName
                },
            )
        }
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "schedule" -> "Schedule"
    "dailyLimit" -> "Daily Limit"
    else -> "All Day"
}

private fun Modifier.chip(): Modifier = this.then(
    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
)
