package com.bacthay.cotuong

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var wm: WindowManager? = null
    private var button: Button? = null
    private var first = 1
    private var w = 0
    private var h = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || button != null) return START_NOT_STICKY
        startAsForeground()

        val code = intent.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data")
        first = intent.getIntExtra("first", 1)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(dm)
        w = dm.widthPixels
        h = dm.heightPixels

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay(
            "cap", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, null
        )

        showButton()
        val mode = if (first == 1) "Tôi đi trước" else "Đối thủ đi trước"
        toast("Đã bật. Chế độ: $mode")
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("cap", "Bậc Thầy Cờ Tướng", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "cap")
            .setContentTitle("Bậc Thầy Cờ Tướng đang chạy")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    private fun showButton() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.y = 300
        val b = Button(this).apply {
            text = "Chụp"
            setOnClickListener { capture() }
            setOnLongClickListener {
                stopSelf()
                true
            }
        }
        wm?.addView(b, lp)
        button = b
    }

    private fun capture() {
        val img = reader?.acquireLatestImage()
        if (img == null) {
            toast("Chưa có ảnh, thử lại")
            return
        }
        val plane = img.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * w
        val bmp = Bitmap.createBitmap(
            w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(plane.buffer)
        img.close()
        toast("Đã chụp màn hình ${bmp.width}x${bmp.height}")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        button?.let { wm?.removeView(it) }
        button = null
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
