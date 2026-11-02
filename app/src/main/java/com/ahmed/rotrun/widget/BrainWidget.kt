package com.ahmed.rotrun.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ahmed.rotrun.MainActivity
import com.ahmed.rotrun.R
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BrainGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("rotrun", Context.MODE_PRIVATE)
        val known = prefs.getBoolean("widget_known", false)
        val health = prefs.getInt("widget_health", 0)
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val offenders = prefs.getString("widget_offenders", "").orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split("\t")
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val seconds = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            if (name.isEmpty() || seconds <= 0L) null else name to seconds
        }.toList()
        provideContent { BrainWidgetContent(context, night, known, health, offenders) }
    }
}

@Composable
private fun BrainWidgetContent(context: Context, night: Boolean, known: Boolean, health: Int, offenders: List<Pair<String, Long>>) {
    val size = LocalSize.current
    val large = size.height > 220.dp
    val title = if (night) ColorProvider(Color(0xFFE6E6E6)) else ColorProvider(Color.White)
    val secondary = if (night) ColorProvider(Color(0xFF8E8E93)) else ColorProvider(Color.White.copy(alpha = 0.78f))
    val background = if (night) R.drawable.widget_meadow_dark else R.drawable.widget_meadow
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(background))
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        if (large) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(provider = ImageProvider(R.drawable.rot_mascot), contentDescription = "Rot", modifier = GlanceModifier.size(88.dp))
                    HealthColumn(known, health, 52, 12, title, secondary, night, showBrain = false, modifier = GlanceModifier.padding(start = 14.dp).defaultWeight())
                }
                Spacer(GlanceModifier.height(14.dp))
                Offenders(offenders, 5, 26, title, secondary)
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                HealthColumn(known, health, 34, 10, title, secondary, night, showBrain = true, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(14.dp))
                Offenders(offenders, 3, 22, title, secondary, modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun HealthColumn(
    known: Boolean,
    health: Int,
    percentSize: Int,
    bar: Int,
    title: ColorProvider,
    secondary: ColorProvider,
    night: Boolean,
    showBrain: Boolean,
    modifier: GlanceModifier,
) {
    Column(modifier) {
        if (showBrain) {
            Image(provider = ImageProvider(R.drawable.rot_mascot), contentDescription = "Rot", modifier = GlanceModifier.size(52.dp))
            Spacer(GlanceModifier.height(6.dp))
        }
        Text("Brain Health", style = TextStyle(color = secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Text(
            if (known) "$health%" else "Not in yet",
            style = TextStyle(color = title, fontSize = if (known) percentSize.sp else (percentSize * 0.55f).sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        HealthBar(if (known) health else null, bar, night)
    }
}

@Composable
private fun HealthBar(health: Int?, height: Int, night: Boolean) {
    val track = if (night) Color(0xFF3A3A3C) else Color.Black.copy(alpha = 0.22f)
    val fill = if (night) Color(0xFF217AFF) else Color.White
    LinearProgressIndicator(
        progress = (health ?: 0) / 100f,
        modifier = GlanceModifier.fillMaxWidth().height(height.dp),
        color = ColorProvider(fill),
        backgroundColor = ColorProvider(track),
    )
}

@Composable
private fun Offenders(
    offenders: List<Pair<String, Long>>,
    limit: Int,
    icon: Int,
    title: ColorProvider,
    secondary: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    val apps = offenders.take(limit)
    Column(modifier) {
        Text("Top Offenders", style = TextStyle(color = secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        if (apps.isEmpty()) {
            Text("Not in yet", style = TextStyle(color = secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        } else {
            apps.forEach { (name, seconds) ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        GlanceModifier.size(icon.dp).cornerRadius((icon * 0.28f).dp).background(ColorProvider(Color(0xFF8E8E93))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name.take(1).uppercase(), style = TextStyle(color = ColorProvider(Color.White), fontSize = (icon * 0.42f).sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Text(name, style = TextStyle(color = title, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(Scoring.formatDuration(seconds), style = TextStyle(color = secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }
}

class BrainWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrainGlanceWidget()

    companion object {
        fun updateAll(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.Main).launch {
                runCatching { BrainGlanceWidget().updateAll(app) }
            }
        }
    }
}
