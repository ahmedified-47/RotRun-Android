package com.ahmed.rotrun.ui

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.logic.BlockRules

@Composable
fun AppPickerDialog(
    apps: List<TrackedApp>,
    selected: List<TrackedApp>,
    onDismiss: () -> Unit,
    onSave: (List<TrackedApp>) -> Unit,
    exceedsCap: (Set<String>) -> Boolean = { false },
    onUpgrade: () -> Unit = {},
) {
    val context = LocalContext.current
    var chosen by remember(selected) { mutableStateOf(selected.map { it.packageName }.toSet()) }
    var query by remember { mutableStateOf("") }
    var capped by remember { mutableStateOf(false) }
    val known = remember(apps, selected) { (apps + selected).distinctBy { it.packageName } }
    val filtered = known.filter { app ->
        val needle = query.trim()
        needle.isEmpty() || app.label.contains(needle, ignoreCase = true) || app.packageName.contains(needle, ignoreCase = true)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Cancel", color = Melt.Action, modifier = Modifier.clickable(onClick = onDismiss))
                Text("Add Apps", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Save",
                    color = Melt.Action,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        onSave(known.filter { it.packageName in chosen })
                    },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                label = { Text("Search") },
            )
            Text(
                "${chosen.size} selected",
                color = Melt.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (capped) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF4E5)).padding(12.dp)) {
                    Text(BlockRules.FREE_LIMIT_MESSAGE, fontWeight = FontWeight.SemiBold)
                    Text(
                        "See Premium",
                        color = Melt.Action,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp).clickable(onClick = onUpgrade),
                    )
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    if (apps.isEmpty()) "No launchable apps yet. RotRun lists apps that have a home-screen icon." else "No apps match that search.",
                    color = Melt.Muted,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    val on = app.packageName in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (on) {
                                chosen = chosen - app.packageName
                                capped = false
                            } else {
                                val next = chosen + app.packageName
                                if (exceedsCap(next)) {
                                    capped = true
                                } else {
                                    chosen = next
                                    capped = false
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AndroidView(
                            modifier = Modifier.size(40.dp),
                            factory = { ImageView(it) },
                            update = { view ->
                                val icon = runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
                                view.setImageDrawable(icon)
                            },
                        )
                        Text(app.label, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Medium)
                        Checkbox(checked = on, onCheckedChange = null)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}
