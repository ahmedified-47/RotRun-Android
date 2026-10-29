package com.ahmed.rotrun.watch

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmed.rotrun.R
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.ShieldPalette

/** Full-screen Rot shield. Accessibility uses TYPE_ACCESSIBILITY_OVERLAY. The fallback uses TYPE_APPLICATION_OVERLAY. */
object ShieldOverlay {
    fun show(
        context: Context,
        type: Int,
        packageName: String,
        label: String,
        onAsk: () -> Unit,
    ): View? {
        val copy = InterventionCopy.forPackage(packageName, label)
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(ShieldPalette.background(copy.id))
            val pad = (28 * density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        val mascot = ImageView(context).apply {
            setImageResource(R.drawable.rot_mascot)
            val size = (180 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val title = TextView(context).apply {
            text = copy.headline
            setTextColor(android.graphics.Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, (24 * density).toInt(), 0, 0)
        }
        val body = TextView(context).apply {
            text = listOfNotNull(copy.body, copy.footer).joinToString("\n")
            setTextColor(0xFFF2E6DC.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        val button = TextView(context).apply {
            text = "Ask my brain"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 28 * density
                setColor(ShieldPalette.accent(copy.id))
            }
            val padH = (18 * density).toInt()
            setPadding(padH, padH, padH, padH)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (28 * density).toInt() }
            setOnClickListener { onAsk() }
        }
        root.addView(mascot)
        root.addView(title)
        root.addView(body)
        root.addView(button)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return runCatching {
            manager.addView(root, params)
            root
        }.getOrNull()
    }

    fun hide(context: Context, view: View?) {
        if (view == null) return
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { manager.removeView(view) }
    }
}
