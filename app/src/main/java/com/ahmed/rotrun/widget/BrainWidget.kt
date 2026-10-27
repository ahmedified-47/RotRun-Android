package com.ahmed.rotrun.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ahmed.rotrun.MainActivity
import com.ahmed.rotrun.R

class BrainWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BrainWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences("rotrun", Context.MODE_PRIVATE)
            val health = prefs.getInt("widget_health", 100)
            val caption = prefs.getString("widget_caption", "Looking sharp today")
            val streak = prefs.getInt("widget_streak", 1)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_brain).apply {
                setTextViewText(R.id.widget_health, "$health%")
                setTextViewText(R.id.widget_caption, caption)
                setTextViewText(R.id.widget_streak, "$streak")
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
