package com.ahmed.rotrun.watch

import android.content.Context
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.AskIntervention
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.OverlayInsetTypes
import com.ahmed.rotrun.logic.ShieldPalette
import com.ahmed.rotrun.ui.InterventionFlow
import com.ahmed.rotrun.ui.RotRunTheme

/**
 * While Ask my brain is on screen, both shields stay down. Otherwise a window
 * event puts the shield back over the app the moment it opens.
 */
object ShieldSession {
    @Volatile
    private var held: String? = null

    @Volatile
    private var opened: Boolean = false

    @Volatile
    private var chatting: Boolean = false

    @Volatile
    private var heldLabel: String = ""

    fun hold(packageName: String) {
        if (packageName.isBlank()) return
        held = packageName
        heldLabel = ""
        opened = false
        chatting = false
    }

    /** The intervention is open. The shield stays down and this package is not blocked until [endChat]. */
    fun holdForChat(packageName: String, label: String = "") {
        if (packageName.isBlank()) return
        held = packageName
        heldLabel = label
        opened = false
        chatting = true
    }

    fun endChat() {
        if (!chatting) return
        chatting = false
        held = null
        heldLabel = ""
        opened = false
    }

    fun chatPackage(): String? = if (chatting) held else null

    fun chatLabel(): String = if (chatting) heldLabel else ""

    /** Ask my brain finished and the blocked app is coming forward. Keep the shield down. */
    fun markOpened(packageName: String) {
        if (packageName.isBlank()) return
        held = packageName
        heldLabel = ""
        opened = true
        chatting = false
    }

    /** A chat hold survives the intervention activity finishing. */
    fun release() {
        if (chatting) return
        held = null
        heldLabel = ""
        opened = false
    }

    fun blocks(packageName: String): Boolean = held != null && held == packageName

    fun openedInto(packageName: String): Boolean = opened && blocks(packageName)
}

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
        fun px(dp: Int) = (dp * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(ShieldPalette.background(copy.id))
        }
        val topGap = android.view.View(context).apply {
            minimumHeight = px(24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val mascot = ImageView(context).apply {
            setImageResource(R.drawable.rot_brain_character)
            val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            if (scale != 0f) {
                val breath = ObjectAnimator.ofPropertyValuesHolder(
                    this,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.03f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.03f),
                ).apply {
                    duration = 2400L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) { breath.start() }
                    override fun onViewDetachedFromWindow(v: View) { breath.cancel() }
                })
            }
            val size = px(196)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = px(36)
            }
        }
        val title = TextView(context).apply {
            text = copy.headline
            setTextColor(android.graphics.Color.WHITE)
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(px(28), 0, px(28), 0)
        }
        val body = TextView(context).apply {
            text = copy.body
            setTextColor(0xFF9E9EA3.toInt())
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(px(36), px(16), px(36), 0)
        }
        val footer = copy.footer?.let { line ->
            TextView(context).apply {
                text = line
                setTextColor(0xFF9E9EA3.toInt())
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(px(36), px(14), px(36), 0)
            }
        }
        val bottomGap = android.view.View(context).apply {
            minimumHeight = px(32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val button = TextView(context).apply {
            text = "Ask my brain"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 999f * density
                setColor(ShieldPalette.accent(copy.id))
            }
            val padH = px(18)
            setPadding(padH, padH, padH, padH)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = px(28)
                marginEnd = px(28)
                bottomMargin = px(12)
            }
            setOnClickListener {
                if (!AskLaunchPolicy.acceptTap(AskBrainLaunch.launching)) return@setOnClickListener
                isEnabled = false
                onAsk()
            }
        }
        root.addView(topGap)
        root.addView(mascot)
        root.addView(title)
        root.addView(body)
        footer?.let(root::addView)
        root.addView(bottomGap)
        root.addView(button)
        padForBars(root)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
            }
        }
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return runCatching {
            manager.addView(root, params)
            Log.i(TAG, "shown $packageName")
            root
        }.onFailure { error ->
            Log.w(TAG, "overlay failed for $packageName: ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    fun cover(
        context: Context,
        type: Int,
        packageName: String,
        label: String,
        ask: AskIntervention?,
        onAsk: () -> Unit,
        onNah: () -> Unit,
        onUnlock: (Int) -> Unit,
    ): View? {
        if (ask == null || !RotRunGraph.ready()) return show(context, type, packageName, label, onAsk)
        val host = OverlayHost(context)
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                RotRunTheme {
                    InterventionFlow(
                        appName = ask.label.ifBlank { label },
                        startMode = ask.mode,
                        packageName = packageName,
                        repo = RotRunGraph.repo,
                        onDismiss = onNah,
                        onUnlock = onUnlock,
                    )
                }
            }
        }
        host.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        padForBars(host)
        ViewCompat.setOnApplyWindowInsetsListener(host) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return runCatching {
            manager.addView(host, params(type))
            host.requestApplyInsets()
            Log.i(TAG, "chat $packageName")
            host
        }.onFailure { error ->
            Log.w(TAG, "chat failed for $packageName: ${error.javaClass.simpleName}")
            host.destroy()
        }.getOrNull()
    }

    fun hide(context: Context, view: View?) {
        if (view == null) return
        (view as? OverlayHost)?.destroy()
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { manager.removeView(view) }
    }

    private fun params(type: Int): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        type,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 30) {
            fitInsetsTypes = 0
            fitInsetsSides = 0
        }
    }

    private fun padForBars(view: View) {
        val pad = barPadding(view.context)
        view.setPadding(
            view.paddingLeft + pad[0],
            view.paddingTop + pad[1],
            view.paddingRight + pad[2],
            view.paddingBottom + pad[3],
        )
    }

    private fun barPadding(context: Context): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            val measured = runCatching { measuredBars(context) }.getOrNull()
            if (measured != null) return measured
        }
        val density = context.resources.displayMetrics.density
        return intArrayOf(0, (24 * density).toInt(), 0, (48 * density).toInt())
    }

    @androidx.annotation.RequiresApi(30)
    private fun measuredBars(context: Context): IntArray {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val windowInsets = manager.currentWindowMetrics.windowInsets
        val stableMask = OverlayInsetTypes.ignoringVisibility(
            android.view.WindowInsets.Type.systemBars(),
            android.view.WindowInsets.Type.displayCutout(),
        )
        val stable = windowInsets.getInsetsIgnoringVisibility(stableMask)
        val ime = windowInsets.getInsets(android.view.WindowInsets.Type.ime())
        return intArrayOf(
            maxOf(stable.left, ime.left),
            maxOf(stable.top, ime.top),
            maxOf(stable.right, ime.right),
            maxOf(stable.bottom, ime.bottom),
        )
    }

    const val TAG = "RotShield"
}

private class OverlayHost(context: Context) : FrameLayout(context), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
    }
}
