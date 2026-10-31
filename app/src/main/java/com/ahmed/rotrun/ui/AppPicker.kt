package com.ahmed.rotrun.ui

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toBitmap
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.logic.BlockRules
import com.ahmed.rotrun.logic.PickerSelection

@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
        }
        Box(Modifier.fillMaxSize().background(Color.White)) { content() }
    }
}

@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onDismiss))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(rot().sheet)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).width(36.dp).height(5.dp).clip(CircleShape).background(rot().muted.copy(alpha = 0.45f)))
                content()
            }
        }
    }
}

@Composable
fun ModalPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(max = 680.dp).clip(RoundedCornerShape(28.dp)).background(Color.White),
            ) { content() }
        }
    }
}

@Composable
fun AppGlyph(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(10.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(rot().inset))
    }
}

@Composable
fun AppPickerDialog(
    apps: List<TrackedApp>,
    selected: List<TrackedApp>,
    onDismiss: () -> Unit,
    onSave: (List<TrackedApp>) -> Unit,
    exceedsCap: (Set<String>) -> Boolean = { false },
    onUpgrade: () -> Unit = {},
) {
    var chosen by remember { mutableStateOf(selected.map { it.packageName }.toSet()) }
    var query by remember { mutableStateOf("") }
    var capped by remember { mutableStateOf(false) }
    val known = remember(apps, selected) { (apps + selected).distinctBy { it.packageName } }
    val filtered = known.filter { app ->
        val needle = query.trim()
        needle.isEmpty() || app.label.contains(needle, ignoreCase = true) || app.packageName.contains(needle, ignoreCase = true)
    }
    fun flip(packageName: String) {
        val result = PickerSelection.toggle(chosen, packageName, exceedsCap)
        chosen = result.chosen
        capped = result.capped
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val chrome = rot()
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(if (chrome.dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE))
        }
        Column(Modifier.fillMaxSize().background(chrome.page).statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Cancel", color = chrome.blue, modifier = Modifier.clickable(onClick = onDismiss))
                Text("Add Apps", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Save",
                    color = chrome.blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSave(known.filter { it.packageName in chosen }) },
                )
            }
            if (capped) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(chrome.card).padding(12.dp),
                ) {
                    Text(BlockRules.FREE_LIMIT_MESSAGE, color = chrome.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "See Premium",
                        color = chrome.blue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp).clickable(onClick = onUpgrade),
                    )
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    if (apps.isEmpty()) "No launchable apps yet. RotRun lists apps that have a home-screen icon." else "No apps match that search.",
                    color = chrome.muted,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    val on = app.packageName in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { flip(app.packageName) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppGlyph(app.packageName, Modifier.size(40.dp))
                        Text(app.label, color = chrome.ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(if (on) chrome.blue else Color.Transparent).border(1.5.dp, if (on) chrome.blue else chrome.muted, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = chrome.ink, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(chrome.inset).padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search apps", color = chrome.muted)
                    inner()
                },
            )
        }
    }
}
