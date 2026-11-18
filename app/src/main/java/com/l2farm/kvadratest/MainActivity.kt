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
            text="L2 KVADRA Test v1.6 — Hawkeye\n${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}\nЦель и атака · наблюдение и одиночные тестовые касания"
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
        button("6. Скопировать историю состояний цели") {
            val history = getSharedPreferences("diagnostics",MODE_PRIVATE)
                .getString("transitionHistory","Переходов пока нет") ?: "Переходов пока нет"
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("L2 Hawkeye target transitions",history))
            Toast.makeText(this,"История состояний скопирована",Toast.LENGTH_SHORT).show()
        }
        button("7. ОДНО тестовое касание «Цель» (перейти в игру)") {
            armManualTap("TARGET")
        }
        button("8. ОДНО тестовое касание «Атака» (перейти в игру)") {
            armManualTap("ATTACK")
        }
        button("9. Отменить тестовое касание") {
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                .remove("testTapAction").remove("testTapDeadline")
                .putString("testTapStatus","Тест касания отменён вручную").apply()
            refresh()
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    private fun armManualTap(action: String) {
        val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
        if (!prefs.getBoolean("captureActive",false)) {
            status.text = "Сначала начните наблюдение за экраном."
            return
        }
        if (TestAccessibilityService.instance == null) {
            status.text = "Сначала включите L2 KVADRA в специальных возможностях."
            return
        }
        val label = if (action == "TARGET") "Цель" else "Атака"
        prefs.edit()
            .putString("testTapAction",action)
            .putLong("testTapDeadline",SystemClock.elapsedRealtime()+20000L)
            .putString("testTapStatus","Ожидается переход в L2Meta: ОДИН тест кнопки «$label», не позднее 20 секунд")
            .apply()
        status.text = "Переключитесь в L2Meta. Hawkeye сделает одно касание «$label» " +
            "только при распознанных кнопках и подходящем состоянии цели. " +
            "Если условия не выполнены за 20 секунд, касания не будет."
    }

    private fun refresh() {
        val p=getSharedPreferences("diagnostics",MODE_PRIVATE)
        status.text=(p.getString("last","Кадров пока нет") ?: "Кадров пока нет") + "\nПропущено UI: ${p.getInt("skippedUiFrames",0)}; отклонено HUD: ${p.getInt("rejectedFrames",0)}" + "\n" + (p.getString("snapshot","PNG ещё не сохранён") ?: "") + "\nДиагностика сохранённого PNG:\n" + (p.getString("snapshotFrame","Пока нет") ?: "Пока нет") +
            "\n\nИстория состояний цели (последние 24 перехода):\n" +
            (p.getString("transitionHistory","Переходов пока нет") ?: "Переходов пока нет") +
            "\\n\\nТест кнопок: " +
            (p.getString("testTapStatus","Не запускался") ?: "Не запускался")
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
