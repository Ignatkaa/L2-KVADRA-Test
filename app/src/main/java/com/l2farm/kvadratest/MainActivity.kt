package com.l2farm.kvadratest

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var testButton: Button
    private val requestCode = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        fun button(label:String, action:()->Unit): Button {
            val b=Button(this).apply { text=label; setOnClickListener { action() } }
            layout.addView(b)
            return b
        }
        layout.addView(TextView(this).apply {
            text="L2 KVADRA Test v0.8 — Hawkeye\n${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}\nНаблюдение, без касаний по игре"
            textSize=19f
        })
        status=TextView(this).apply { text="Ожидание проверки"; textSize=16f; setPadding(0,20,0,20) }
        layout.addView(status)
        button("1. Начать наблюдение (разрешить весь экран)") {
            val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(),requestCode)
        }
        button("2. Показать результат и путь PNG") { refresh() }
        button("3. Остановить наблюдение") {
            startService(Intent(this,CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
            refresh()
        }
        button("4. Специальные возможности") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        testButton=button("5. Тест касания только этой кнопки") {
            val service=TestAccessibilityService.instance
            if (service==null) status.text="Служба специальных возможностей выключена"
            else {
                val xy=IntArray(2)
                testButton.getLocationOnScreen(xy)
                service.tap(xy[0]+testButton.width/2f,xy[1]+testButton.height/2f) { ok ->
                    runOnUiThread { status.text=if(ok) "Жест выполнен" else "Жест отменён" }
                }
            }
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    private fun refresh() {
        val p=getSharedPreferences("diagnostics",MODE_PRIVATE)
        status.text=(p.getString("last","Кадров пока нет") ?: "Кадров пока нет") + "\n" + (p.getString("snapshot","PNG ещё не сохранён") ?: "")
    }
    override fun onResume() { super.onResume(); refresh() }
    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==this.requestCode && resultCode==RESULT_OK && data!=null) {
            startForegroundService(Intent(this,CaptureService::class.java)
                .putExtra(CaptureService.RESULT_CODE,resultCode)
                .putExtra(CaptureService.RESULT_DATA,data))
            status.text="Наблюдение запущено. Переключитесь в L2Meta, затем вернитесь за результатом."
        } else status.text="Разрешение на захват не предоставлено"
    }
}
