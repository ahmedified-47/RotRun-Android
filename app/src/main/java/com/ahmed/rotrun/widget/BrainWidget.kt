package com.ahmed.rotrun.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ahmed.rotrun.MainActivity
import com.ahmed.rotrun.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BrainGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("rotrun", Context.MODE_PRIVATE)
        val known = prefs.getBoolean("widget_known", false)
        val health = prefs.getInt("widget_health", 0)
        val caption = prefs.getString("widget_caption", "Not in yet").orEmpty()
        val offenders = prefs.getString("widget_offenders", "Not in yet").orEmpty()
        val streak = prefs.getInt("widget_streak", 0)
        provideContent { BrainWidgetContent(context, known, health, caption, offenders, streak) }
    }
}

@Composable
private fun BrainWidgetContent(context: Context, known: Boolean, health: Int, caption: String, offenders: String, streak: Int) {
    val white = TextStyle(color = ColorProvider(Color.White))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF24754D)))
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text("ROTRUN", style = white.copy(fontWeight = FontWeight.Bold))
        Image(provider = ImageProvider(R.drawable.rot_mascot), contentDescription = "Rot")
        Text(if (known) "$health%" else "Not in yet", style = white.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
        Text(caption, style = white)
        Text(offenders, style = white)
        Text("Streak $streak", style = white)
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
