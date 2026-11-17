package com.l2farm.kvadratest

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var testButton: Button
    private val requestCode = 100
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            status.text = intent?.getStringExtra("message") ?: "Нет результата"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        fun button(label: String, action: (Button) -> Unit): Button {
            val b = Button(this).apply { text = label; setOnClickListener { action(this) } }
            layout.addView(b)
            return b
        }
        layout.addView(TextView(this).apply { text = "L2 KVADRA Test v0.2\nУстройство: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid API: ${Build.VERSION.SDK_INT}"; textSize = 20f })
        status = TextView(this).apply { text = "Проверки ещё не запускались"; textSize = 17f; setPadding(0,24,0,24) }
        layout.addView(status)
        button("1. Разрешить захват экрана") {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
        }
        button("2. Открыть специальные возможности") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        testButton = button("3. Тест касания этой кнопки") {
            if (TestAccessibilityService.instance == null) {
                status.text = "Сначала включите службу L2 KVADRA Test в специальных возможностях"
            } else {
                val coords = IntArray(2)
                testButton.getLocationOnScreen(coords)
                val x = coords[0] + testButton.width / 2f
                val y = coords[1] + testButton.height / 2f
                TestAccessibilityService.instance?.tap(x, y) { ok ->
                    runOnUiThread { status.text = if (ok) "Программный жест выполнен" else "Жест отменён" }
                }
            }
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(CaptureService.ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }
    override fun onStop() { unregisterReceiver(receiver); super.onStop() }
    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java).putExtra(CaptureService.RESULT_CODE, resultCode).putExtra(CaptureService.RESULT_DATA, data)
            startForegroundService(i)
            status.text = "Ожидание кадра..."
        } else status.text = "Захват отменён"
    }
}