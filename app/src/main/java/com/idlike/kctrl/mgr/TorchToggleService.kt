package com.idlike.kctrl.mgr

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.lang.Boolean
import kotlin.Int

class TorchToggleService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(1, buildNotification())

        // 2. 申请唤醒锁，保持CPU运行（防止息屏被系统挂起）
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "com.idlike.kctrl.app:TorchWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L)  // 保持10分钟，或者你传0表示一直持有
    }
    private var flashOn = false

    private fun tryToggleFlashlight() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            try {
                for (cameraId in cameraManager.cameraIdList) {
                    if (cameraManager.getCameraCharacteristics(cameraId)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    ) {
                        val newState = !(flashOn)
                        cameraManager.setTorchMode(cameraId, newState)
                        flashOn = newState
                        break
                    }
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                batteryIntent.setData(Uri.parse("package:$packageName"))
                batteryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(batteryIntent)
            }
        }

        tryToggleFlashlight()
        return START_STICKY
    }

    @SuppressLint("ScheduleExactAlarm")
    override fun onTaskRemoved(rootIntent: Intent) {
        // 设置1秒后自动重启服务
        val restartServiceIntent = Intent(
            applicationContext,
            TorchToggleService::class.java
        )
        val restartServicePendingIntent = PendingIntent.getService(
            this,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 可选：重新启动服务（防杀）
        startService(Intent(this, TorchToggleService::class.java))
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("服务已启动")
            .setContentText("前台服务正在保活中…")
            .setSmallIcon(R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "保活前台服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "keep_alive_channel"
    }
}