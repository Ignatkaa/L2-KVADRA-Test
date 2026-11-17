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
    }
    private var projection: MediaProjection? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastFrame = 0L
    private var frames = 0
    private var startedAt = 0L
    private var snapshotSaved = false
    private var stopping = false
    private var skippedUiFrames = 0
    private var rejectedFrames = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL,"L2 screen diagnostics",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(RESULT_DATA,Intent::class.java)
        else intent?.getParcelableExtra(RESULT_DATA) as? Intent
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        startedAt = System.currentTimeMillis()
        snapshotSaved = false
        val notification = Notification.Builder(this,CHANNEL)
            .setContentTitle("L2 KVADRA Test v1.2")
            .setContentText("Анализ кадров активен; вернитесь в L2Meta")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
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
                            skippedUiFrames++
                        } else {
                        val vision = ScreenVision.inspect(img)
                        if (!vision.diagnostics.startsWith("gameHud=true")) {
                            rejectedFrames++
                            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                                .putInt("rejectedFrames",rejectedFrames).apply()
                            return@setOnImageAvailableListener
                        }
                        if (!snapshotSaved && now - startedAt >= 12000L) {
                            snapshotSaved = true
                            saveSnapshot(img)
                        }
                        val o = vision.observation
                        frames++
                        val message = "Кадр #$frames ${img.width}x${img.height} | HP=${o.hp ?: "?"}% MP=${o.mp ?: "?"}% | Цель=${o.targetVisible} | ${ArcherDecisionEngine.advise(o)} | ${vision.diagnostics}"
                        getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                            .putString("last",message)
                            .putLong("timestamp",now)
                            .putInt("skippedUiFrames",skippedUiFrames)
                            .putInt("rejectedFrames",rejectedFrames)
                            .apply()
                        }
                    }
                } catch (e: Exception) {
                    getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                        .putString("last","Ошибка анализа: ${e.javaClass.simpleName}: ${e.message}").apply()
                } finally { img.close() }
            },Handler(mainLooper))
            display = p.createVirtualDisplay("L2Observer",w,h,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,r.surface,null,null)
        } catch(e:Exception) {
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                .putString("last","Ошибка запуска: ${e.javaClass.simpleName}: ${e.message}").apply()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun saveSnapshot(image: android.media.Image) {
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
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"L2Meta_capture_${System.currentTimeMillis()}.png")
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
                    .putString("snapshot","Сохранён PNG: Pictures/L2KVADRA").apply()
            } catch(e:Exception) {
                contentResolver.delete(uri,null,null)
                throw e
            }
        } catch(e:Exception) {
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
                .putString("snapshot","Ошибка PNG: ${e.javaClass.simpleName}: ${e.message}").apply()
        } finally { bitmap?.recycle() }
    }
    override fun onDestroy() {
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
