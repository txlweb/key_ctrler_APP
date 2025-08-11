package com.idlike.kctrl.mgr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class VibrationService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "vibration_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建通知渠道
        createNotificationChannel()

        // 获取震动时长参数，默认500毫秒
        val vibrationTime = intent?.getLongExtra("time", 500L) ?: 500L

        // 创建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("震动服务")
            .setContentText("正在执行震动 ${vibrationTime}ms")
            .setSmallIcon(R.drawable.ic_config)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)

        // 触发震动
        triggerVibration(vibrationTime)

        // 停止服务
        stopSelf()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "震动服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "震动服务通知渠道"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun triggerVibration(duration: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}