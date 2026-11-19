package com.bacthay.cotuong

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqCapture = 1001
    private val reqNotif = 1002
    private lateinit var group: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Bậc Thầy Cờ Tướng"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Ai đi trước?"
            textSize = 18f
            setPadding(0, pad, 0, 0)
        })
        group = RadioGroup(this)
        group.addView(RadioButton(this).apply { text = "Tôi đi trước"; id = 1 })
        group.addView(RadioButton(this).apply { text = "Đối thủ đi trước"; id = 2 })
        val saved = getSharedPreferences("cfg", MODE_PRIVATE).getInt("first", 1)
        group.check(saved)
        root.addView(group)
        root.addView(Button(this).apply {
            text = "Bắt đầu"
            setOnClickListener { start() }
        })
        setContentView(root)
    }

    private fun start() {
        val first = if (group.checkedRadioButtonId == 2) 2 else 1
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putInt("first", first).apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy bật quyền hiển thị trên ứng dụng khác, rồi bấm Bắt đầu lại", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), reqNotif)
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqNotif) start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture && resultCode == RESULT_OK && data != null) {
            val first = getSharedPreferences("cfg", MODE_PRIVATE).getInt("first", 1)
            val i = Intent(this, CaptureService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
                .putExtra("first", first)
            startForegroundService(i)
            finish()
        }
    }
}
