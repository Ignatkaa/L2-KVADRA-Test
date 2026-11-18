package com.l2farm.kvadratest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Optional floating emergency STOP, visible only during a running session.
 * Requires explicit Android "Display over other apps" approval.
 * Notification STOP remains available if overlays are prohibited by KVADRA OS.
 */
class CombatStopOverlay(
    private val context: Context,
    private val onStop: () -> Unit
) {
    private var view: TextView? = null
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val available: Boolean
        get() = Build.VERSION.SDK_INT >= 26 && Settings.canDrawOverlays(context)

    val visible: Boolean get() = view != null

    fun show(): Boolean {
        if (view != null) return true
        if (!available) return false
        val bg = GradientDrawable().apply {
            setColor(Color.rgb(171, 35, 49))
            cornerRadius = 22f * context.resources.displayMetrics.density
            setStroke(2, Color.rgb(245, 150, 150))
        }
        val button = TextView(context).apply {
            text = "■ СТОП"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = bg
            setOnClickListener { onStop() }
            contentDescription = "Немедленно остановить автобой"
        }
        val density = context.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (104 * density).toInt(), (56 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = (12 * density).toInt()
            y = (-120 * density).toInt()
            title = "Hawkeye STOP"
        }
        return try {
            manager.addView(button, params)
            view = button
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hide() {
        val old = view ?: return
        view = null
        try {
            manager.removeViewImmediate(old)
        } catch (_: Exception) {
            // System may already have removed the overlay on service teardown.
        }
    }
}
