package com.l2farm.kvadratest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * User-controlled overlay in the game: START when observation is active and
 * auto-combat is OFF/PAUSED; STOP while auto-combat is running.
 *
 * A START tap is a new explicit user action. It never automatically resumes
 * after a fail-safe pause. Overlay is always removed when screen capture ends.
 * The notification STOP remains available if overlay permission is denied.
 */
class CombatStopOverlay(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit
) {
    private val manager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var showingRunning = false
    private var lastPressedAt = 0L

    val available: Boolean
        get() = Build.VERSION.SDK_INT >= 26 && Settings.canDrawOverlays(context)

    val visible: Boolean get() = view != null

    private fun styleButton(button: TextView, running: Boolean) {
        val background = GradientDrawable().apply {
            val main = if (running) Color.rgb(171,35,49)
                else Color.rgb(16,145,123)
            val outline = if (running) Color.rgb(245,150,150)
                else Color.rgb(123,236,220)
            setColor(main)
            cornerRadius = 22f * context.resources.displayMetrics.density
            setStroke((2f * context.resources.displayMetrics.density).toInt(),outline)
        }
        button.text = if (running) "■ СТОП" else "▶ СТАРТ"
        button.contentDescription =
            if (running) "Немедленно остановить автобой"
            else "Вручную запустить автобой с текущими настройками"
        button.background = background
        showingRunning = running
    }

    fun show(running: Boolean): Boolean {
        if (!available) {
            hide()
            return false
        }
        val existing = view
        if (existing != null) {
            if (showingRunning != running) styleButton(existing,running)
            return true
        }
        val button = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setOnClickListener {
                // Prevent a double tap on STOP from immediately restarting combat.
                val now = SystemClock.elapsedRealtime()
                if (now-lastPressedAt < 1200L) return@setOnClickListener
                lastPressedAt = now
                if (showingRunning) onStop() else onStart()
            }
        }
        styleButton(button,running)
        val density = context.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (110*density).toInt(), (56*density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = (12*density).toInt()
            y = (-120*density).toInt()
            title = "Hawkeye START/STOP"
        }
        return try {
            manager.addView(button,params)
            view = button
            true
        } catch (_:Exception) {
            false
        }
    }

    fun hide() {
        val old=view ?: return
        view=null
        try {
            manager.removeViewImmediate(old)
        } catch (_:Exception) {
            // OS may already have removed an overlay on service teardown.
        }
    }
}
