package com.ahmed.rotrun.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ahmed.rotrun.MainActivity
import com.ahmed.rotrun.R
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("rotrun", Context.MODE_PRIVATE)
        val running = prefs.getBoolean("widget_focus_on", false)
        val started = prefs.getLong("widget_focus_started", 0L)
        val target = prefs.getInt("widget_focus_target", 0)
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        provideContent { FocusWidgetContent(context, night, running && started > 0L, started, target) }
    }
}

@Composable
private fun FocusWidgetContent(context: Context, night: Boolean, running: Boolean, started: Long, targetMinutes: Int) {
    val size = LocalSize.current
    val medium = size.width >= 250.dp
    val white = TextStyle(color = ColorProvider(Color.White))
    val background = if (night) R.drawable.widget_focus_dark else R.drawable.widget_meadow
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(background))
            .padding(12.dp),
    ) {
        if (!running) {
            if (medium) IdleMedium(context, white) else IdleSmall(context, white)
        } else {
            Active(context, white, medium, started, targetMinutes)
        }
    }
}

@Composable
private fun IdleSmall(context: Context, white: TextStyle) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(focusIntent(context, null))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(provider = ImageProvider(R.drawable.rot_brain_character), contentDescription = "Rot", modifier = GlanceModifier.size(36.dp))
            Column(GlanceModifier.padding(start = 8.dp)) {
                Text("Focus", style = white.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text("How long?", style = white.copy(fontSize = 11.sp, color = ColorProvider(Color.White.copy(alpha = 0.78f))))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            DurationButton(context, 15, 13, 7, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            DurationButton(context, 30, 13, 7, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            DurationButton(context, 45, 13, 7, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            DurationButton(context, 60, 13, 7, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun IdleMedium(context: Context, white: TextStyle) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            GlanceModifier.width(96.dp).clickable(actionStartActivity(focusIntent(context, null))),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(provider = ImageProvider(R.drawable.rot_brain_character), contentDescription = "Rot", modifier = GlanceModifier.size(72.dp))
            Spacer(GlanceModifier.height(6.dp))
            Text("Focus Mode", style = white.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(14.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text("Heal your brain", style = white.copy(fontSize = 13.sp, color = ColorProvider(Color.White.copy(alpha = 0.8f))))
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                listOf(15, 30, 45, 60).forEachIndexed { index, minutes ->
                    if (index > 0) Spacer(GlanceModifier.width(8.dp))
                    DurationButton(context, minutes, 15, 9, GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun Active(context: Context, white: TextStyle, medium: Boolean, started: Long, targetMinutes: Int) {
    val elapsed = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
    val countUp = targetMinutes <= 0
    val remaining = if (countUp) elapsed else (targetMinutes * 60L - elapsed).coerceAtLeast(0)
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(provider = ImageProvider(R.drawable.rot_brain_character), contentDescription = "Rot", modifier = GlanceModifier.size(if (medium) 56.dp else 40.dp))
            Column(GlanceModifier.defaultWeight().padding(start = 10.dp)) {
                Text(if (countUp) "Elapsed" else "Remaining", style = white.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ColorProvider(Color.White.copy(alpha = 0.75f))))
                Text(Scoring.formatClock(remaining), style = white.copy(fontSize = if (medium) 34.sp else 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            if (medium) EndButton(context, fill = false)
        }
        if (medium && targetMinutes > 0) {
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = (remaining.toFloat() / (targetMinutes * 60f)).coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth(),
                color = ColorProvider(Color.White),
                backgroundColor = ColorProvider(Color.White.copy(alpha = 0.35f)),
            )
        }
        if (!medium) {
            Spacer(GlanceModifier.height(6.dp))
            EndButton(context, fill = true)
        }
    }
}

@Composable
private fun DurationButton(context: Context, minutes: Int, font: Int, vertical: Int, modifier: GlanceModifier) {
    Text(
        "${minutes}m",
        style = TextStyle(
            color = ColorProvider(Color.White),
            fontSize = font.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .cornerRadius(20.dp)
            .background(ColorProvider(Color(0xFF217AFF)))
            .padding(vertical = vertical.dp)
            .clickable(actionStartActivity(focusIntent(context, minutes))),
    )
}

@Composable
private fun EndButton(context: Context, fill: Boolean) {
    Text(
        "End Focus",
        style = TextStyle(
            color = ColorProvider(Color(0xFF217AFF)),
            fontSize = if (fill) 13.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        modifier = (if (fill) GlanceModifier.fillMaxWidth() else GlanceModifier)
            .cornerRadius(20.dp)
            .background(ColorProvider(Color.White))
            .padding(horizontal = if (fill) 0.dp else 14.dp, vertical = if (fill) 7.dp else 8.dp)
            .clickable(actionStartActivity(focusIntent(context, null, end = true))),
    )
}

private fun focusIntent(context: Context, minutes: Int?, end: Boolean = false): Intent {
    val uri = when {
        end -> "rotrun://focus?end=1"
        minutes == null -> "rotrun://focus"
        else -> "rotrun://focus?minutes=$minutes"
    }
    return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(context, MainActivity::class.java)
}

class FocusWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusGlanceWidget()

    companion object {
        fun updateAll(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.Main).launch {
                runCatching { FocusGlanceWidget().updateAll(app) }
            }
        }
    }
}
