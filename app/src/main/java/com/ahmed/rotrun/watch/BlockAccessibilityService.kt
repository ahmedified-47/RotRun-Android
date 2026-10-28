package com.ahmed.rotrun.watch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmed.rotrun.InterventionActivity
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.UsageReader
import com.ahmed.rotrun.logic.InterventionCopy

/** Foreground-app block. The overlay uses the same shield copy as iOS. */
class BlockAccessibilityService : AccessibilityService() {
    private var overlay: LinearLayout? = null
    private var shownFor: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (!RotRunGraph.ready()) return
        val repo = RotRunGraph.repo
        if (!repo.isBlocked(packageName, repo.safetyPackages())) {
            if (shownFor != null) hide()
            return
        }
        if (shownFor == packageName && overlay != null) return
        val label = UsageReader.label(this, packageName)
        show(label)
        shownFor = packageName
    }

    override fun onInterrupt() {
        hide()
    }

    private fun show(appName: String) {
        hide()
        val copy = InterventionCopy.forApp(appName)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#3D2418"))
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 3, pad, pad)
        }
        val mascot = ImageView(this).apply {
            setImageResource(R.drawable.rot_mascot)
            val size = (160 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val title = TextView(this).apply {
            text = copy.headline
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val body = TextView(this).apply {
            text = listOfNotNull(copy.body, copy.footer).joinToString("\n")
            setTextColor(Color.parseColor("#F2E6DC"))
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val button = Button(this).apply {
            text = "Ask my brain"
            setTextColor(Color.parseColor("#3D2418"))
            background = GradientDrawable().apply {
                cornerRadius = 40 * resources.displayMetrics.density
                setColor(Color.WHITE)
            }
            setOnClickListener {
                hide()
                val mode = RotRunGraph.repo.pickStyle() ?: "standard"
                val open = Intent(this@BlockAccessibilityService, InterventionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(InterventionActivity.EXTRA_APP, appName)
                    putExtra(InterventionActivity.EXTRA_MODE, mode)
                }
                startActivity(open)
            }
        }
        root.addView(mascot)
        root.addView(title)
        root.addView(body)
        root.addView(button)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        manager.addView(root, params)
        overlay = root
    }

    private fun hide() {
        val view = overlay ?: return
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
        overlay = null
        shownFor = null
    }
}
