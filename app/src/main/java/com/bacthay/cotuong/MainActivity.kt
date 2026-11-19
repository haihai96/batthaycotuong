package com.bacthay.cotuong

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : Activity() {

    private val reqCapture = 1001
    private val reqNotif = 1002
    private lateinit var group: RadioGroup
    private lateinit var status: TextView

    private fun prefs() = getSharedPreferences("cfg", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        val heading = TextView(this)
        heading.text = "Bậc Thầy Cờ Tướng"
        heading.textSize = 26f
        root.addView(heading)

        val ask = TextView(this)
        ask.text = "Ai đi trước?"
        ask.textSize = 18f
        ask.setPadding(0, pad, 0, 0)
        root.addView(ask)

        group = RadioGroup(this)
        val r1 = RadioButton(this)
        r1.text = "Tôi đi trước"
        r1.id = 1
        val r2 = RadioButton(this)
        r2.text = "Đối thủ đi trước"
        r2.id = 2
        group.addView(r1)
        group.addView(r2)
        group.check(prefs().getInt("first", 1))
        root.addView(group)

        root.addView(makeButton("Bắt đầu") { start() })
        root.addView(makeButton("Dừng") {
            stopService(Intent(this, CaptureService::class.java))
            refreshLater()
        })
        root.addView(makeButton("Mở cài đặt quyền của ứng dụng") { openAppSettings() })
        root.addView(makeButton("Xóa nhật ký lỗi") {
            prefs().edit().remove("crash").apply()
            refresh()
        })

        status = TextView(this)
        status.textSize = 13f
        status.setPadding(0, pad, 0, 0)
        status.setTextIsSelectable(true)
        root.addView(status)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { action() }
        return b
    }

    private fun installCrashLogger() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            saveError("Ứng dụng bị sập", e)
            old?.uncaughtException(t, e)
        }
    }

    private fun saveError(where: String, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val text = where + ": " + sw.toString().take(1200)
            prefs().edit().putString("crash", text).commit()
        } catch (ex: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    private fun serviceRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (s in am.getRunningServices(50)) {
            if (s.service.className == CaptureService::class.java.name) return true
        }
        return false
    }

    private fun refresh() {
        val sb = StringBuilder()
        sb.append("Máy: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
        sb.append(", Android ").append(Build.VERSION.RELEASE)
        sb.append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Hiển thị trên ứng dụng khác: ")
            .append(if (Settings.canDrawOverlays(this)) "BẬT" else "TẮT").append("\n")
        val notifOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        sb.append("Thông báo: ").append(if (notifOk) "BẬT" else "TẮT").append("\n")
        sb.append("Dịch vụ chụp màn hình: ")
            .append(if (serviceRunning()) "ĐANG CHẠY" else "KHÔNG CHẠY").append("\n")
        val crash = prefs().getString("crash", null)
        if (crash != null) {
            sb.append("\nLỖI GẦN NHẤT:\n").append(crash)
        }
        status.text = sb.toString()
    }

    private fun refreshLater() {
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 1500)
    }

    private fun openAppSettings() {
        try {
            val i = Intent("miui.intent.action.APP_PERM_EDITOR")
            i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            i.putExtra("extra_pkgname", packageName)
            startActivity(i)
        } catch (e: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun start() {
        val first = if (group.checkedRadioButtonId == 2) 2 else 1
        prefs().edit().putInt("first", first).remove("crash").apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Hãy bật quyền hiển thị trên ứng dụng khác, rồi bấm Bắt đầu lại",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), reqNotif)
            return
        }
        askCapture()
    }

    private fun askCapture() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqNotif) askCapture()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqCapture) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Chưa được cho phép quay màn hình", Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        val i = Intent(this, CaptureService::class.java)
        i.putExtra("code", resultCode)
        i.putExtra("data", data)
        i.putExtra("first", prefs().getInt("first", 1))
        try {
            startForegroundService(i)
        } catch (e: Exception) {
            saveError("Không khởi động được dịch vụ", e)
        }
        refreshLater()
    }
}
