package com.l2farm.kvadratest

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Hawkeye v1.6.2: keep the manual control tests AND their outcome above the
 * long diagnostic history, even on the Pretech 1102 landscape tablet.
 */
class MainActivity : Activity() {
    private lateinit var quickStatus: TextView
    private lateinit var details: TextView
    private lateinit var stats: TextView
    private lateinit var overlayPermission: Button
    private lateinit var accessibilityTestButton: Button
    private val requestCode = 100
    private val navy = Color.rgb(11, 20, 37)
    private val panel = Color.rgb(23, 37, 58)
    private val muted = Color.rgb(160, 184, 211)
    private val cyan = Color.rgb(30, 182, 202)
    private val pale = Color.rgb(235, 246, 251)

    private fun rounded(color: Int, border: Int = color, radius: Float = 15f) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), border)
        }

    private fun smallButton(row: LinearLayout, label: String, action: () -> Unit): Button {
        val isStop = label.contains("СТОП") || label == "ОСТАНОВИТЬ"
        val isStart = label.contains("ПУСК АВТОБОЯ")
        val btnColor = when {
            isStop -> Color.rgb(176, 48, 60)
            isStart -> cyan
            else -> Color.rgb(36, 61, 86)
        }
        val button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if(isStart) navy else Color.WHITE)
            background = rounded(btnColor)
            minHeight = (44f * resources.displayMetrics.density).toInt()
            setPadding(5,6,5,6)
            setOnClickListener { action() }
        }
        val margin = (3 * resources.displayMetrics.density).toInt()
        row.addView(button, LinearLayout.LayoutParams(0,
            (50 * resources.displayMetrics.density).toInt(), 1f).apply {
                setMargins(margin,margin,margin,margin)
            })
        return button
    }

    private fun newRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        parent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad/2, pad, pad/2)
        }

        root.addView(TextView(this).apply {
            text = "HAWKEYE    /    L2 KVADRA  v1.9"
            setTextColor(pale)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "INTERLUDE  •  ${Build.MODEL}  •  API ${Build.VERSION.SDK_INT}  •  ЦЕЛЬ / АТАКА"
            setTextColor(muted)
            textSize = 11f
        })
        quickStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(pale)
            setPadding(18, 12, 18, 12)
            background = rounded(panel,Color.rgb(43, 73, 100))
            text = "Тест кнопок пока не запускался"
        }
        root.addView(quickStatus)
        stats = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(117, 224, 224))
            setPadding(8, 9, 8, 6)
        }
        root.addView(stats)

        val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
        val options = newRow(root)
        val settingsLabel = TextView(this).apply {
            text = "НАСТРОЙКИ БОЯ"
            setTextColor(muted)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
        }
        options.addView(settingsLabel,LinearLayout.LayoutParams(0,
            (46 * resources.displayMetrics.density).toInt(),0.8f))

        fun spinner(values: List<String>, initial: Int, onSelect: (Int)->Unit): Spinner {
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,values)
                background = rounded(Color.rgb(231, 242, 250))
                setSelection(initial.coerceIn(0,values.lastIndex))
                onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) { onSelect(position) }
                }
            }
        }
        val durationValues = CombatSettings.DURATIONS
        val durationIndex = durationValues.indexOf(prefs.getInt("durationMinutes",3))
            .coerceAtLeast(0)
        options.addView(spinner(durationValues.map { "$it мин" },durationIndex) {
            prefs.edit().putInt("durationMinutes",durationValues[it]).apply()
        },LinearLayout.LayoutParams(0,
            (44 * resources.displayMetrics.density).toInt(),1f))
        val intervals = CombatSettings.INTERVALS_MS
        val intervalIndex = intervals.indexOf(prefs.getLong("tapIntervalMs",3500L))
            .coerceAtLeast(0)
        options.addView(spinner(intervals.map {
            "${if (it == 3500L) "3,5" else (it/1000).toString()} сек"
        },intervalIndex) {
            prefs.edit().putLong("tapIntervalMs",intervals[it]).apply()
        },LinearLayout.LayoutParams(0,
            (44 * resources.displayMetrics.density).toInt(),1f))


        // Important controls are ALWAYS visible. Only the long log scrolls.
        val controlRow = newRow(root)
        smallButton(controlRow, "НАЧАТЬ НАБЛЮДЕНИЕ") {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
        }
        smallButton(controlRow, "ОБНОВИТЬ РЕЗУЛЬТАТ") { refresh() }
        smallButton(controlRow, "ОСТАНОВИТЬ") {
            startService(Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_STOP))
            refresh()
        }

        val autoRow = newRow(root)
        smallButton(autoRow, "ПУСК АВТОБОЯ") { confirmAutoStart() }
        smallButton(autoRow, "СТОП АВТОБОЯ") { stopAutoCombat() }

        val testRow = newRow(root)
        smallButton(testRow, "ОДНО КАСАНИЕ «ЦЕЛЬ»") { armManualTap("TARGET") }
        smallButton(testRow, "ОДНО КАСАНИЕ «АТАКА»") { armManualTap("ATTACK") }
        smallButton(testRow, "ОТМЕНА ТЕСТА") { confirmCancellation() }

        val overlayRow = newRow(root)
        overlayPermission = smallButton(
            overlayRow, "РАЗРЕШИТЬ ПЛАВАЮЩИЙ СТОП"
        ) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else Toast.makeText(this,"Плавающий СТОП уже разрешён",
                Toast.LENGTH_SHORT).show()
        }
        root.addView(TextView(this).apply {
            text = "АВАРИЙНЫЙ СТОП: кнопка в приложении, уведомление Android или " +
                "плавающая кнопка (если разрешена). Пауза при потере распознавания."
            setTextColor(muted)
            textSize = 11f
            setPadding(4, 5, 4, 5)
        })
        root.addView(TextView(this).apply {
            text = "ОДИНОЧНЫЙ ТЕСТ: для «Атака» нужна подтверждённая живая цель."
            setTextColor(muted)
            textSize = 11f
            setPadding(4, 4, 4, 6)
        })

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel)
        }
        details = TextView(this).apply {
            textSize = 13f
            setTextColor(pale)
            setPadding(16, 14, 16, 16)
        }
        body.addView(details)

        val extraRow = newRow(body)
        smallButton(extraRow, "СПЕЦВОЗМОЖНОСТИ") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        smallButton(extraRow, "КОПИРОВАТЬ ИСТОРИЮ ЦЕЛИ") {
            val history = getSharedPreferences("diagnostics", MODE_PRIVATE)
                .getString("transitionHistory", "Переходов пока нет")
                ?: "Переходов пока нет"
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("L2 target history", history))
            Toast.makeText(this, "История скопирована", Toast.LENGTH_SHORT).show()
        }
        accessibilityTestButton = smallButton(extraRow, "ТЕСТ КАСАНИЯ В ПРИЛОЖЕНИИ") {
            val service = TestAccessibilityService.instance
            if (service == null) {
                quickStatus.text = "Служба специальных возможностей выключена"
            } else {
                val position = IntArray(2)
                accessibilityTestButton.getLocationOnScreen(position)
                service.tap(
                    position[0] + accessibilityTestButton.width / 2f,
                    position[1] + accessibilityTestButton.height / 2f
                ) { ok ->
                    runOnUiThread {
                        quickStatus.text = if (ok) "Жест в приложении выполнен"
                            else "Жест в приложении отменён"
                    }
                }
            }
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun confirmAutoStart() {
        val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
        if (!prefs.getBoolean("captureActive",false)) {
            quickStatus.text = "Сначала разреши захват экрана через «Начать наблюдение»"
            return
        }
        if (TestAccessibilityService.instance == null) {
            quickStatus.text = "Включи L2 KVADRA в специальных возможностях"
            return
        }
        val settings = CombatSettings.safe(
            prefs.getInt("durationMinutes",3),prefs.getLong("tapIntervalMs",3500L)
        )
        AlertDialog.Builder(this)
            .setTitle("Запустить Hawkeye?")
            .setMessage(
                "Продолжительность: ${settings.durationMinutes} мин. " +
                "Интервал касаний: ${settings.tapIntervalMs / 1000.0} сек. " +
                "Лимит жестов: ${settings.maxGestures}. " +
                "Выбор цели → одна атака → контроль HP → следующая цель. " +
                "При ошибках — защитная пауза. " +
                "СТОП — на экране, в уведомлении и поверх игры при разрешении."
            )
            .setPositiveButton("ПУСК") { _,_ ->
                startService(Intent(this,CaptureService::class.java)
                    .setAction(CaptureService.ACTION_AUTO_START))
                quickStatus.text = "Автобой запускается. Переключись в L2Meta."
            }
            .setNegativeButton("Отмена",null).show()
    }

    private fun stopAutoCombat() {
        startService(Intent(this,CaptureService::class.java)
            .setAction(CaptureService.ACTION_AUTO_STOP))
        quickStatus.text = "Команда СТОП АВТОБОЯ отправлена"
    }

    private fun confirmCancellation() {
        val prefs = getSharedPreferences("diagnostics", MODE_PRIVATE)
        if (!prefs.contains("testTapAction")) {
            quickStatus.text = "Нет активного теста: отменять нечего"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Отменить тестовое касание?")
            .setMessage("Команда будет удалена. Никаких касаний после отмены не будет.")
            .setPositiveButton("Отменить тест") { _, _ ->
                prefs.edit().remove("testTapAction")
                    .remove("testTapDeadline")
                    .remove("testTapBlockedReason")
                    .putString("testTapStatus", "Тест касания отменён вручную")
                    .apply()
                refresh()
            }
            .setNegativeButton("Вернуться", null)
            .show()
    }

    private fun armManualTap(action: String) {
        val prefs = getSharedPreferences("diagnostics", MODE_PRIVATE)
        val mode = prefs.getString("autoMode","OFF")
        if (mode != "OFF") {
            quickStatus.text = "Для ручного теста сначала нажми «СТОП АВТОБОЯ»"
            return
        }
        if (!prefs.getBoolean("captureActive", false)) {
            quickStatus.text = "ЗАПРЕЩЕНО: сначала нажмите «Начать наблюдение»"
            return
        }
        if (TestAccessibilityService.instance == null) {
            quickStatus.text = "ЗАПРЕЩЕНО: включите службу специальных возможностей"
            return
        }
        val label = if (action == "TARGET") "Цель" else "Атака"
        val editor = prefs.edit().remove("testTapBlockedReason")
        if (action == "ATTACK") editor.remove("attackFeedback")
        editor.putString("testTapAction", action)
            .putLong("testTapDeadline", SystemClock.elapsedRealtime() + 30000L)
            .putString("testTapStatus",
                "ЗАПРОШЕНО ОДНО касание «$label»: перейдите в L2Meta за 30 секунд")
            .apply()
        refresh()
    }

    private fun refresh() {
        val p = getSharedPreferences("diagnostics", MODE_PRIVATE)
        val testStatus = p.getString("testTapStatus", "Тест не запускался")
            ?: "Тест не запускался"
        val reason = p.getString("testTapBlockedReason", null)
        val capture = if (p.getBoolean("captureActive", false)) "Захват активен"
            else "Захват выключен"
        val armed = if (p.contains("testTapAction")) " · ОЖИДАЕТ КАСАНИЯ" else ""
        val attackFeedback = p.getString("attackFeedback",null)
        val autoStatus = p.getString("autoStatus","Автобой выключен")
        val autoGestures = p.getInt("autoGestures",0)
        val autoLost = p.getInt("autoTargetsLost",0)
        val maxGestures = p.getInt("autoGestureLimit",12)
        quickStatus.text = "$capture$armed\nАВТОБОЙ: $autoStatus" +
            "\nЖестов: $autoGestures/$maxGestures · потеряно целей: $autoLost/6" +
            "\n$testStatus" +
            (if (reason != null) "\nПричина: $reason" else "") +
            (if (attackFeedback != null) "\nПроверка атаки: $attackFeedback" else "")

        stats.text = "ВРЕМЯ ${p.getLong("autoElapsedSeconds",0L)}с   |   " +
            "ЦЕЛИ ${p.getInt("autoSelections",0)}   |   " +
            "ПОИСК ${p.getInt("autoTargetTaps",0)}   |   " +
            "АТАКИ ${p.getInt("autoAttackTaps",0)}   |   " +
            "УРОН ${p.getInt("autoDamageObservations",0)}"
        if (::overlayPermission.isInitialized) overlayPermission.text =
            if(Settings.canDrawOverlays(this)) "ПЛАВАЮЩИЙ СТОП: РАЗРЕШЁН"
            else "РАЗРЕШИТЬ ПЛАВАЮЩИЙ СТОП"
        val last = p.getString("last", "Кадров пока нет") ?: "Кадров пока нет"
        val saved = p.getString("snapshot", "PNG ещё не сохранён")
            ?: "PNG ещё не сохранён"
        val paired = p.getString("snapshotFrame", "Нет данных")
            ?: "Нет данных"
        val failurePng = p.getString("failureSnapshot", "Кадр сбоя ещё не записан")
            ?: "Кадр сбоя ещё не записан"
        val failureDiagnostic = p.getString("failureSnapshotFrame", "Нет данных")
            ?: "Нет данных"
        val history = p.getString("transitionHistory", "Переходов пока нет")
            ?: "Переходов пока нет"
        val autoHistory = p.getString("autoHistory","Нет событий") ?: "Нет событий"
        val lastAutoTap = p.getString("autoLastGesture","Нет") ?: "Нет"
        details.text = "ПОСЛЕДНИЙ КАДР\n$last\n" +
            "Пропущено UI: ${p.getInt("skippedUiFrames", 0)}, " +
            "отклонено HUD: ${p.getInt("rejectedFrames", 0)}\n\n" +
            "СОХРАНЁННЫЙ PNG\n$saved\n$paired\n\n" +
            "КАДР ПРИ ПАУЗЕ АВТОБОЯ\n$failurePng\n$failureDiagnostic\n\n" +
            "ИСТОРИЯ ЦЕЛИ\n$history\n\n" +
            "ИСТОРИЯ АВТОБОЯ\n$autoHistory\n" +
            "Последний жест: $lastAutoTap"
    }

    override fun onResume() {
        super.onResume()
        if (::quickStatus.isInitialized) refresh()
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.RESULT_CODE, resultCode)
                .putExtra(CaptureService.RESULT_DATA, data))
            quickStatus.text = "Наблюдение запускается — откройте L2Meta"
        } else {
            quickStatus.text = "Разрешение на захват экрана не предоставлено"
        }
    }
}
