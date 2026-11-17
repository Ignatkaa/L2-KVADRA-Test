package com.l2farm.kvadratest

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    companion object {
        const val RESULT_CODE = "result_code"
        const val RESULT_DATA = "result_data"
        const val CHANNEL = "capture"
        const val ACTION_RESULT = "com.l2farm.kvadratest.CAPTURE_RESULT"
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val resultCode = intent?.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(RESULT_DATA, Intent::class.java)
                   else @Suppress("DEPRECATION") (intent?.getParcelableExtra(RESULT_DATA) as? Intent)
        if (data == null || resultCode != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        val channel = NotificationChannel(CHANNEL, "Screen capture test", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification = Notification.Builder(this, CHANNEL).setContentTitle("L2 KVADRA Test")
            .setContentText("Проверка захвата экрана").setSmallIcon(android.R.drawable.ic_menu_camera).build()
        startForeground(1, notification)
        try {
            val projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(2)
            val height = metrics.heightPixels.coerceAtLeast(2)
            val reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            val finished = AtomicBoolean(false)
            var display: android.hardware.display.VirtualDisplay? = null
            projection.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() { display?.release(); reader.close(); stopSelf() }
            }, android.os.Handler(mainLooper))
            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.close()
                if (finished.compareAndSet(false, true)) {
                    sendBroadcast(Intent(ACTION_RESULT).setPackage(packageName).putExtra("message", "Захват экрана: кадр получен"))
                    projection.stop()
                }
            }, android.os.Handler(mainLooper))
            display = projection.createVirtualDisplay("L2Test", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        } catch (e: Exception) {
            sendBroadcast(Intent(ACTION_RESULT).setPackage(packageName).putExtra("message", "Ошибка захвата: " + e.javaClass.simpleName))
            stopSelf()
        }
        return START_NOT_STICKY
    }
}