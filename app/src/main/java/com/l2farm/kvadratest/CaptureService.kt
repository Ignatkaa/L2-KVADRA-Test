package com.l2farm.kvadratest

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.content.ContentValues
import android.provider.MediaStore
import java.nio.ByteBuffer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureService : Service() {
    companion object {
        const val RESULT_CODE = "result_code"
        const val RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.l2farm.kvadratest.STOP"
        const val CHANNEL = "capture"
        const val ACTION_SNAPSHOT = "com.l2farm.kvadratest.SNAPSHOT"
        const val ACTION_AUTO_START = "com.l2farm.kvadratest.AUTO_START"
        const val ACTION_AUTO_STOP = "com.l2farm.kvadratest.AUTO_STOP"
    }
    private var projection: MediaProjection? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastFrame = 0L
    private var frames = 0
    private var startedAt = 0L
    private var snapshotSaved = false
    private var failureSnapshotSaved = false
    private var stopping = false
    private var skippedUiFrames = 0
    private var rejectedFrames = 0
    private val targetTracker = TargetCombatTracker()
    private val targetHistory = TargetTransitionHistory()
    private val attackImpact = AttackImpactMonitor()
    private val autoCombat = AutoCombatController()
    private lateinit var stopOverlay: CombatStopOverlay
    private val autoHistory = java.util.ArrayDeque<String>()
    private var lastAutoStatus = ""
    private var lastManualTapAt = 0L
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        stopOverlay = CombatStopOverlay(this) {
            stopAutoCombat("Автобой остановлен плавающей кнопкой СТОП")
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL,"L2 screen diagnostics",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAutoCombat("Аварийная остановка: захват экрана выключен")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_AUTO_STOP) {
            stopAutoCombat("Автобой выключен кнопкой СТОП")
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_AUTO_START) {
            val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
            if (projection == null || !prefs.getBoolean("captureActive",false) ||
                TestAccessibilityService.instance == null) {
                prefs.edit().putString("autoStatus",
                    "Автобой не запущен: нет захвата экрана или AccessibilityService")
                    .putBoolean("autoRunning",false).apply()
            } else {
                val settings = CombatSettings.safe(
                    prefs.getInt("durationMinutes",3),
                    prefs.getLong("tapIntervalMs",3500L)
                )
                autoCombat.start(SystemClock.elapsedRealtime(),settings)
                failureSnapshotSaved = false
                prefs.edit().remove("failureSnapshot")
                    .remove("failureSnapshotFrame").apply()
                targetTracker.reset()
                autoHistory.clear()
                lastAutoStatus = ""
                prefs.edit().remove("testTapAction").remove("testTapDeadline")
                    .remove("autoLastGesture")
                    .putString("autoHistory","")
            .putString("overlayStatus","Не запущен")
                    .putString("overlayStatus",if(stopOverlay.available)
                        "Плавающий СТОП доступен" else
                        "Разрешение на плавающий СТОП не выдано; используй уведомление")
                    .apply()
                persistAutoStatus(SystemClock.elapsedRealtime())
            }
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(RESULT_DATA,Intent::class.java)
        else intent?.getParcelableExtra(RESULT_DATA) as? Intent
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        startedAt = System.currentTimeMillis()
        startedAtElapsed = SystemClock.elapsedRealtime()
        snapshotSaved = false
        failureSnapshotSaved = false
        frames = 0
        skippedUiFrames = 0
        rejectedFrames = 0
        targetTracker.reset()
        targetHistory.reset()
        attackImpact.reset()
        autoCombat.stop()
        autoHistory.clear()
        lastAutoStatus = ""
        lastManualTapAt = 0L
        getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
            .remove("last").remove("snapshot").remove("snapshotFrame").remove("transitionHistory")
            .remove("failureSnapshot").remove("failureSnapshotFrame")
            .remove("attackFeedback")
            .putBoolean("autoRunning",false)
            .putString("autoMode","OFF")
            .putString("autoStatus","Автобой выключен")
            .putString("autoHistory","")
            .remove("testTapAction").remove("testTapDeadline")
            .remove("testTapBlockedReason")
            .putBoolean("captureActive",false)
            .putString("testTapStatus","Одиночный тест касания не запрошен")
            .putInt("skippedUiFrames",0).putInt("rejectedFrames",0).apply()
        val stopIntent = Intent(this,CaptureService::class.java).setAction(ACTION_AUTO_STOP)
        val stopPending = PendingIntent.getService(
            this, 1007, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this,CHANNEL)
            .setContentTitle("L2 KVADRA Test v1.9")
            .setContentText("Анализ кадров активен; вернитесь в L2Meta")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_delete,"СТОП БОЯ",stopPending).build())
            .build()
        startForeground(1,notification)
        try {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mgr.getMediaProjection(code,data)
            projection = p
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels.coerceAtLeast(2)
            val h = metrics.heightPixels.coerceAtLeast(2)
            val r = ImageReader.newInstance(w,h,android.graphics.PixelFormat.RGBA_8888,2)
            reader = r
            p.registerCallback(object: MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            },Handler(mainLooper))
            r.setOnImageAvailableListener({ source ->
                val img = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrame >= 750) {
                        lastFrame = now
                        expireArmedTapIfNeeded()
                        autoCombat.timeCheck(SystemClock.elapsedRealtime())
                        val plane = img.planes[0]
                        val buf = plane.buffer
                        fun pale(xx:Int, yy:Int):Boolean {
                            val i=yy.toLong()*plane.rowStride+xx.toLong()*plane.pixelStride
                            if(i<0 || i+2>=buf.limit()) return false
                            val k=i.toInt()
                            val r=buf.get(k).toInt() and 255
                            val g=buf.get(k+1).toInt() and 255
                            val bl=buf.get(k+2).toInt() and 255
                            return r>205 && g>205 && bl>205 && maxOf(r,g,bl)-minOf(r,g,bl)<12
                        }
                        val ownUi=listOf(0.25,0.45,0.65,0.85).count {
                            pale((img.width*0.5).toInt(),(img.height*it).toInt())
                        }>=3
                        if(ownUi) {
                            autoCombat.onOwnUi(SystemClock.elapsedRealtime())
                            persistAutoStatus(SystemClock.elapsedRealtime())
                            skippedUiFrames++
                            val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
                            if (prefs.contains("testTapAction")) prefs.edit()
                                .putString("testTapBlockedReason",
                                    "На экране Hawkeye — переключитесь в L2Meta")
                                .apply()
                        } else {
                        val vision = ScreenVision.inspect(img)
                        if (!vision.gameHud) {
                            autoCombat.onFrame(
                                SystemClock.elapsedRealtime(),false,false,false,
                                null,TargetAction.HUD_UNAVAILABLE
                            )
                            persistAutoStatus(SystemClock.elapsedRealtime())
                            val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
                            if (prefs.contains("testTapAction")) prefs.edit()
                                .putString("testTapBlockedReason",
                                    "Игровой HUD не найден на экране")
                                .apply()
                            rejectedFrames++
                            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                                .putInt("rejectedFrames",rejectedFrames).apply()
                            return@setOnImageAvailableListener
                        }
                        val o = vision.observation
                        val decision = targetTracker.update(
                            gameHud = vision.gameHud,
                            panelVisible = o.targetVisible,
                            targetHp = o.targetHp
                        )
                        frames++
                        val impactResult = attackImpact.observe(
                            now, o.targetVisible, o.targetHp
                        )
                        if (impactResult != null) {
                            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                                .putString("attackFeedback",impactResult).apply()
                        }
                        tryArmedSingleTap(vision, decision, img.width, img.height)
                        if (autoCombat.running) {
                            val step = autoCombat.onFrame(
                                SystemClock.elapsedRealtime(),
                                vision.gameHud, vision.controls != null,
                                o.targetVisible, o.targetHp, decision.action
                            )
                            if (step.tap != null)
                                dispatchAutoTap(step.tap,vision,img.width,img.height,decision)
                        }
                        persistAutoStatus(SystemClock.elapsedRealtime())
                        val stateChanged = targetHistory.record(
                            frame = frames,
                            elapsedMs = now - startedAt,
                            decision = decision,
                            panelVisible = o.targetVisible
                        )
                        val message = "Кадр #$frames ${img.width}x${img.height} | " +
                            "Цель=${if(o.targetVisible) "выбрана" else "нет"} | " +
                            "HP цели=${o.targetHp?.toString() ?: "?"}% | " +
                            "Состояние=${decision.action} | " +
                            "${decision.recommendation} | ${vision.diagnostics}"
                        if (!failureSnapshotSaved &&
                            autoCombat.mode == CombatMode.PAUSED) {
                            failureSnapshotSaved = true
                            saveSnapshot(
                                img, message + " | АВТОБОЙ: " + autoCombat.status,
                                now, failure = true
                            )
                        }
                        if (!snapshotSaved && now - startedAt >= 12000L &&
                            (decision.action == TargetAction.ATTACK ||
                                now - startedAt >= 25000L)) {
                            snapshotSaved = true
                            saveSnapshot(img, message, now)
                        }
                        getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                            .putString("last",message)
                            .putLong("timestamp",now)
                            .putInt("skippedUiFrames",skippedUiFrames)
                            .putInt("rejectedFrames",rejectedFrames)
                            .apply()
                        if (stateChanged) {
                            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                                .putString("transitionHistory",targetHistory.asText())
                                .apply()
                        }
                        }
                    }
                } catch (e: Exception) {
                    getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                        .putString("last","Ошибка анализа: ${e.javaClass.simpleName}: ${e.message}").apply()
                } finally { img.close() }
            },Handler(mainLooper))
            display = p.createVirtualDisplay("L2Observer",w,h,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,r.surface,null,null)
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                .putBoolean("captureActive",true).apply()
        } catch(e:Exception) {
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                .putString("last","Ошибка запуска: ${e.javaClass.simpleName}: ${e.message}").apply()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun stopAutoCombat(reason: String) {
        autoCombat.stop(reason)
        persistAutoStatus(SystemClock.elapsedRealtime())
    }

    private fun persistAutoStatus(now: Long) {
        val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
        val current = "${autoCombat.mode}: ${autoCombat.status}"
        if (autoCombat.running) {
            val shown = stopOverlay.show()
            if (!shown) prefs.edit().putString("overlayStatus",
                "Поверх игры кнопка недоступна; СТОП есть в уведомлении").apply()
        } else stopOverlay.hide()
        if (current != lastAutoStatus) {
            lastAutoStatus = current
            val entry = "t+${(now - startedAtElapsed).coerceAtLeast(0L)/1000}s " +
                current
            autoHistory.addLast(entry)
            while (autoHistory.size > 24) autoHistory.removeFirst()
        }
        val stats = autoCombat.statistics(now)
        prefs.edit()
            .putLong("autoElapsedSeconds",stats.elapsedSeconds)
            .putInt("autoTargetTaps",stats.targetTaps)
            .putInt("autoAttackTaps",stats.attackTaps)
            .putInt("autoSelections",stats.confirmedSelections)
            .putInt("autoDamageObservations",stats.damageObservations)
            .putInt("autoGestureLimit",stats.gestureLimit)
            .putBoolean("autoRunning",autoCombat.running)
            .putString("autoMode",autoCombat.mode.name)
            .putString("autoStatus",current)
            .putInt("autoGestures",autoCombat.sentGestures)
            .putInt("autoTargetsLost",autoCombat.targetsLost)
            .putString("autoHistory",autoHistory.joinToString("\n"))
            .apply()
    }

    private var startedAtElapsed = 0L

    /**
     * Auto gestures only run after a user-confirmed three-minute session.
     * Uses the same proven physical-display mapping as the manual tests.
     */
    private fun dispatchAutoTap(
        action: CombatTap, vision: VisionResult,
        capturedWidth: Int, capturedHeight: Int, decision: TargetDecision
    ) {
        if (!autoCombat.running) return
        val controls = vision.controls ?: run {
            autoCombat.pause("координаты игровых кнопок отсутствуют")
            return
        }
        val point = if (action == CombatTap.TARGET) controls.target else controls.attack
        val real = android.graphics.Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.getRealSize(real)
        val mapped = TouchCoordinateMapper.map(
            point,capturedWidth,capturedHeight,real.x,real.y
        ) ?: run {
            autoCombat.pause("невозможно пересчитать координаты кнопки $action")
            return
        }
        val service = TestAccessibilityService.instance ?: run {
            autoCombat.pause("служба специальных возможностей отключена")
            return
        }
        val hpBefore = decision.targetHp
        val gestureAt = SystemClock.elapsedRealtime()
        getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
            .putString("autoLastGesture",
                "$action image=(${point.x},${point.y}) " +
                    "touch=(${mapped.point.x},${mapped.point.y}) " +
                    "${mapped.describe()}")
            .apply()
        service.tap(mapped.point.x.toFloat(),mapped.point.y.toFloat()) { ok ->
            Handler(mainLooper).post {
                autoCombat.onGestureResult(
                    SystemClock.elapsedRealtime(),action,ok,hpBefore
                )
                persistAutoStatus(SystemClock.elapsedRealtime())
            }
        }
    }

    /** Timeout must be handled even when the UI is not recognized as the game. */
    private fun expireArmedTapIfNeeded() {
        val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
        val action = prefs.getString("testTapAction",null) ?: return
        val deadline = prefs.getLong("testTapDeadline",0L)
        if (SystemClock.elapsedRealtime() < deadline) return
        val reason = prefs.getString(
            "testTapBlockedReason","не удалось подтвердить игровой экран и кнопки"
        )
        prefs.edit().remove("testTapAction").remove("testTapDeadline")
            .putString("testTapStatus",
                "Тест $action завершён БЕЗ КАСАНИЯ: 30 секунд истекли; $reason")
            .apply()
    }

    /**
     * One-shot, explicitly armed by the user in MainActivity.
     * No autonomous tapping, coordinate fallback or repeat loop is allowed.
     * Requires a recognized game HUD AND both control-button shapes.
     */
    private fun tryArmedSingleTap(vision: VisionResult, decision: TargetDecision,
        capturedWidth: Int, capturedHeight: Int) {
        if (autoCombat.mode != CombatMode.OFF) return
        val prefs = getSharedPreferences("diagnostics",MODE_PRIVATE)
        val action = prefs.getString("testTapAction",null) ?: return
        val elapsed = SystemClock.elapsedRealtime()
        val deadline = prefs.getLong("testTapDeadline",0L)
        if (deadline <= elapsed) {
            val lastReason = prefs.getString("testTapBlockedReason","Не выполнены условия") 
            prefs.edit().remove("testTapAction").remove("testTapDeadline")
                .putString("testTapStatus","Тест не выполнен: время истекло. " + lastReason).apply()
            return
        }
        val eligibility = ManualTapPolicy.check(
            action, vision.gameHud, vision.controls, decision
        )
        if (!eligibility.allowed) {
            // Always show why no touch was sent, including a missing control detector.
            val lastReason = prefs.getString("testTapBlockedReason",null)
            if (lastReason != eligibility.reason) prefs.edit()
                .putString("testTapBlockedReason",eligibility.reason).apply()
            return
        }
        if (elapsed - lastManualTapAt < 5000L) {
            prefs.edit().putString("testTapBlockedReason",
                "Защитная задержка между ручными тестами").apply()
            return
        }
        val controls = vision.controls ?: return
        val service = TestAccessibilityService.instance ?: run {
            prefs.edit().remove("testTapAction").remove("testTapDeadline")
                .putString("testTapStatus","AccessibilityService выключена; касаний не было")
                .putString("testTapBlockedReason","Служба специальных возможностей выключена")
                .apply()
            return
        }
        val coordinate = if (action == "TARGET") controls.target else controls.attack

        // GestureDescription coordinates are in PHYSICAL display pixels, but
        // MediaProjection was created with resources.displayMetrics (2000x1110).
        // On this 2000x1200 tablet, projection is pillarboxed by 75px and
        // scaled by 0.925. Map back to the actual display before dispatch.
        val real = android.graphics.Point()
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(real)
        val mapping = TouchCoordinateMapper.map(
            coordinate, capturedWidth, capturedHeight, real.x, real.y
        )
        if (mapping == null) {
            prefs.edit().putString("testTapBlockedReason",
                "Нельзя пересчитать координаты: capture=${capturedWidth}x${capturedHeight}, " +
                    "экран=${real.x}x${real.y}, кнопка=(${coordinate.x},${coordinate.y})")
                .apply()
            return
        }
        val tapPoint = mapping.point
        val mappingInfo = mapping.describe() +
            ", image=(${coordinate.x},${coordinate.y}), " +
            "touch=(${tapPoint.x},${tapPoint.y})"

        // Commit before dispatch: if more frames arrive, the same request cannot tap twice.
        val cleared = prefs.edit()
            .remove("testTapAction").remove("testTapDeadline")
            .remove("testTapBlockedReason")
            .putString("testTapStatus","Однократный жест $action отправлен: " +
                mappingInfo + ", раскладка=${controls.layout}")
            .commit()
        if (!cleared) return
        lastManualTapAt = elapsed
        service.tap(tapPoint.x.toFloat(),tapPoint.y.toFloat()) { ok ->
            if (action == "ATTACK") {
                if (ok) {
                    attackImpact.start(decision.targetHp,System.currentTimeMillis())
                    prefs.edit().putString("attackFeedback",
                        "После жеста «Атака» наблюдаем HP цели: " +
                            "${decision.targetHp ?: "?"}% (до 20 секунд)").apply()
                } else {
                    prefs.edit().putString("attackFeedback",
                        "Android отменил жест «Атака»: наблюдение за уроном не запущено")
                        .apply()
                }
            }
            prefs.edit().putString(
                "testTapStatus",
                if (ok) "Android выполнил жест $action: " + mappingInfo +
                    ", раскладка=${controls.layout}. " +
                    "Реакцию игры необходимо проверить отдельно."
                else "Android отменил жест $action: " + mappingInfo +
                    "; повторного касания не будет"
            ).apply()
        }
    }

    private fun saveSnapshot(
        image: android.media.Image, diagnostic: String,
        capturedAt: Long, failure: Boolean = false
    ) {
        var bitmap: Bitmap? = null
        try {
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val stride = plane.rowStride
            val pixelStride = plane.pixelStride
            val bytes = IntArray(w * h)
            val buffer = plane.buffer
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * stride + x * pixelStride
                    val r = buffer.get(i).toInt() and 255
                    val g = buffer.get(i+1).toInt() and 255
                    val b = buffer.get(i+2).toInt() and 255
                    bytes[y*w+x] = android.graphics.Color.rgb(r,g,b)
                }
            }
            bitmap = Bitmap.createBitmap(bytes,w,h,Bitmap.Config.ARGB_8888)
            val fileName = if (failure) "L2Meta_failure_${capturedAt}.png"
                else "L2Meta_capture_${capturedAt}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,fileName)
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/L2KVADRA")
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                ?: throw IllegalStateException("MediaStore insert failed")
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw IllegalStateException("PNG encode failed")
                } ?: throw IllegalStateException("Cannot open image output")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING,0)
                contentResolver.update(uri,values,null,null)
                getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                    .putString(
                        if (failure) "failureSnapshot" else "snapshot",
                        "Сохранён PNG: Pictures/L2KVADRA/$fileName"
                    )
                    .putString(
                        if (failure) "failureSnapshotFrame" else "snapshotFrame",
                        diagnostic
                    )
                    .putLong(if (failure) "failureTime" else "snapshotTime",capturedAt)
                    .apply()
            } catch(e:Exception) {
                contentResolver.delete(uri,null,null)
                throw e
            }
        } catch(e:Exception) {
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                .putString(
                    if (failure) "failureSnapshot" else "snapshot",
                    "Ошибка PNG: ${e.javaClass.simpleName}: ${e.message}"
                ).apply()
        } finally { bitmap?.recycle() }
    }
    override fun onDestroy() {
        stopAutoCombat("Захват остановлен — автобой выключен")
        if (::stopOverlay.isInitialized) stopOverlay.hide()
        getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
            .remove("testTapAction").remove("testTapDeadline")
            .putBoolean("captureActive",false).apply()
        if (!stopping) {
            stopping = true
            reader?.setOnImageAvailableListener(null,null)
            display?.release()
            reader?.close()
            projection?.stop()
        }
        super.onDestroy()
    }
}
