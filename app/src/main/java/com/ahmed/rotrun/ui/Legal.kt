package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
fun LegalPage(asset: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val body = remember(asset) {
        context.assets.open("$asset.txt").bufferedReader().use { it.readText() }
    }
    Column(Modifier.fillMaxSize().background(Melt.Grouped).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("‹ $title", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(body, modifier = Modifier.padding(top = 16.dp, bottom = 40.dp), lineHeight = 22.sp)
    }
}

@Composable
fun SectionLabel(text: String, color: Color = Melt.Muted) {
    Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp, top = 16.dp))
}
