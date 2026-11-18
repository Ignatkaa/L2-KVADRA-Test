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
    private lateinit var accessibilityTestButton: Button
    private val requestCode = 100

    private fun smallButton(row: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            textSize = 13f
            setOnClickListener { action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
        }

        root.addView(TextView(this).apply {
            text = "L2 KVADRA Test v1.6.2 — Hawkeye · ${Build.MODEL} · API ${Build.VERSION.SDK_INT}"
            textSize = 18f
        })
        quickStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, 6, 0, 6)
            text = "Тест кнопок пока не запускался"
        }
        root.addView(quickStatus)

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

        val testRow = newRow(root)
        smallButton(testRow, "ОДНО КАСАНИЕ «ЦЕЛЬ»") { armManualTap("TARGET") }
        smallButton(testRow, "ОДНО КАСАНИЕ «АТАКА»") { armManualTap("ATTACK") }
        smallButton(testRow, "ОТМЕНА ТЕСТА") { confirmCancellation() }

        root.addView(TextView(this).apply {
            text = "Тест запускается только вручную. После нажатия перейдите в L2Meta за 30 секунд. " +
                "«Атака» требует подтверждённую живую цель."
            textSize = 13f
            setPadding(0, 6, 0, 8)
        })

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        details = TextView(this).apply {
            textSize = 15f
            setPadding(0, 6, 0, 16)
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
        if (!prefs.getBoolean("captureActive", false)) {
            quickStatus.text = "ЗАПРЕЩЕНО: сначала нажмите «Начать наблюдение»"
            return
        }
        if (TestAccessibilityService.instance == null) {
            quickStatus.text = "ЗАПРЕЩЕНО: включите службу специальных возможностей"
            return
        }
        val label = if (action == "TARGET") "Цель" else "Атака"
        prefs.edit()
            .remove("testTapBlockedReason")
            .putString("testTapAction", action)
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
        quickStatus.text = "$capture$armed\n$testStatus" +
            (if (reason != null) "\nПричина: $reason" else "")

        val last = p.getString("last", "Кадров пока нет") ?: "Кадров пока нет"
        val saved = p.getString("snapshot", "PNG ещё не сохранён")
            ?: "PNG ещё не сохранён"
        val paired = p.getString("snapshotFrame", "Нет данных")
            ?: "Нет данных"
        val history = p.getString("transitionHistory", "Переходов пока нет")
            ?: "Переходов пока нет"
        details.text = "ПОСЛЕДНИЙ КАДР\n$last\n" +
            "Пропущено UI: ${p.getInt("skippedUiFrames", 0)}, " +
            "отклонено HUD: ${p.getInt("rejectedFrames", 0)}\n\n" +
            "СОХРАНЁННЫЙ PNG\n$saved\n$paired\n\n" +
            "ИСТОРИЯ ЦЕЛИ\n$history"
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
