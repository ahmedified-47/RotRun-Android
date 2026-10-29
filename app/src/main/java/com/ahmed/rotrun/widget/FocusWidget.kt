package com.ahmed.rotrun.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ahmed.rotrun.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen Focus start. The live countdown also sits on the blocking notification. */
class FocusGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("rotrun", Context.MODE_PRIVATE)
        val running = prefs.getBoolean("widget_focus_on", false)
        val started = prefs.getLong("widget_focus_started", 0L)
        val target = prefs.getInt("widget_focus_target", 0)
        provideContent { FocusWidgetContent(context, running, started, target) }
    }
}

@Composable
private fun FocusWidgetContent(context: Context, running: Boolean, started: Long, targetMinutes: Int) {
    val white = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp)
    val label = when {
        !running || started <= 0L -> "Start Focus"
        targetMinutes <= 0 -> "Focus is running"
        else -> {
            val left = (targetMinutes * 60_000L - (System.currentTimeMillis() - started)).coerceAtLeast(0L)
            "Focus · ${left / 60_000}m left"
        }
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1C3A2A)))
            .padding(10.dp),
    ) {
        Text(
            "FOCUS",
            style = white.copy(fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.clickable(actionStartActivity(focusIntent(context, null))),
        )
        Text(label, style = white, modifier = GlanceModifier.padding(top = 4.dp, bottom = 8.dp))
        Row {
            listOf(15, 30, 45, 60).forEach { minutes ->
                Text(
                    "${minutes}m",
                    style = white.copy(fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier
                        .padding(end = 8.dp)
                        .clickable(actionStartActivity(focusIntent(context, minutes))),
                )
            }
        }
    }
}

private fun focusIntent(context: Context, minutes: Int?): Intent {
    val uri = if (minutes == null) "rotrun://focus" else "rotrun://focus?minutes=$minutes"
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
