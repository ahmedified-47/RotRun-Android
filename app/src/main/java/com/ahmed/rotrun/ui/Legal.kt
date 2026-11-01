package com.ahmed.rotrun.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object HostedPolicy {
    const val PRIVACY = "https://ahmedified-47.github.io/rotrun-site/privacy.html"
    const val TERMS = "https://ahmedified-47.github.io/rotrun-site/terms.html"

    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { context.startActivity(intent) }
    }
}

@Composable
fun LegalDialog(which: String?, onClose: () -> Unit) {
    if (which == null) return
    val terms = which == "terms"
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LegalPage(asset = if (terms) "terms" else "privacy", title = if (terms) "Terms of Service" else "Privacy Policy", onBack = onClose)
    }
}

@Composable
fun LegalPage(asset: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val body = remember(asset) {
        context.assets.open("$asset.txt").bufferedReader().use { it.readText() }
    }
    val chrome = rot()
    Column(
        Modifier.fillMaxSize().background(chrome.page).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text("‹ $title", color = chrome.blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
        Text(title, color = chrome.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        body.split("\n").forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Text("", modifier = Modifier.padding(top = 8.dp))
                trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 18.dp))
                trimmed.startsWith("- ") -> Text(trimmed.removePrefix("- "), color = chrome.ink, modifier = Modifier.padding(top = 6.dp, start = 8.dp), lineHeight = 22.sp)
                else -> Text(trimmed, color = chrome.ink, modifier = Modifier.padding(top = 8.dp), lineHeight = 22.sp)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, color: Color = Melt.Muted) {
    Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp, top = 16.dp))
}
