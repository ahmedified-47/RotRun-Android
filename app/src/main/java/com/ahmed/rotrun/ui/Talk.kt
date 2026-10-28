package com.ahmed.rotrun.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.FreeTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TalkSheet(repo: RotRunRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var turns by remember { mutableStateOf(repo.backend.turns()) }
    var busy by remember { mutableStateOf(false) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) note = "Microphone access is off."
        else startVoice(repo) { note = it }
    }
    Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Talk to Brain", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Close", color = Melt.Action, modifier = Modifier.clickable {
                repo.backend.stopVoice()
                onClose()
            })
        }
        if (!repo.backend.signedIn) {
            CartoonBrain(BrainStyle.Meadow, Modifier.padding(vertical = 16.dp).size(120.dp))
            Text("Sign in with Google to talk to your brain. Your conversations help it learn about you and give better advice.")
            PrimaryButton("Sign in with Google") {
                val activity = context as? Activity ?: return@PrimaryButton
                repo.backend.signIn(activity) { error -> note = error }
            }
        } else if (repo.backend.needsConsent()) {
            Text("Rot can remember facts you share so advice gets better. You can forget everything later. Streak and XP stay.")
            PrimaryButton("I agree") {
                CoroutineScope(Dispatchers.IO).launch {
                    val error = repo.backend.grantConsent()
                    withContext(Dispatchers.Main) { note = error }
                }
            }
            Text("Not now", color = Melt.Muted, modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClose))
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (turns.isEmpty()) Text("Hey, how are you? How can I help you?", modifier = Modifier.padding(vertical = 8.dp))
                turns.forEach { (role, content) ->
                    Text(
                        content,
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().background(
                            if (role == "user") Color(0xFFE8F1FF) else Color(0xFFF2F2F7),
                            RoundedCornerShape(12.dp),
                        ).padding(10.dp),
                    )
                }
            }
            note?.let { Text(it, color = Melt.House, modifier = Modifier.padding(vertical = 6.dp)) }
            TextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Message Rot") })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).clickable(enabled = !busy) {
                    val message = draft.trim()
                    if (message.isEmpty() || busy) return@clickable
                    busy = true
                    draft = ""
                    CoroutineScope(Dispatchers.IO).launch {
                        val reply = repo.backend.sendChat(message, "home")
                        withContext(Dispatchers.Main) {
                            turns = repo.backend.turns().ifEmpty { turns + ("assistant" to reply) }
                            if (reply == FreeTier.limitMessage) note = reply
                            busy = false
                        }
                    }
                })
                Text("Voice", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).clickable {
                    mic.launch(Manifest.permission.RECORD_AUDIO)
                })
            }
        }
        note?.let { Text(it, color = Melt.Muted, modifier = Modifier.padding(top = 8.dp)) }
    }
}

private fun startVoice(repo: RotRunRepository, onNote: (String?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val error = repo.backend.startVoice("You are Rot, the RotRun brain. Keep it short, roasting, and never medical or fitness.")
        withContext(Dispatchers.Main) { onNote(error) }
    }
}
