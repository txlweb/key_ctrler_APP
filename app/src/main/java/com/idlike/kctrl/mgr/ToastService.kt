package com.idlike.kctrl.mgr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat

class ToastService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "toast_service_channel"
        private const val NOTIFICATION_ID = 3
        
        // Intent extras
        const val EXTRA_TEXT = "text"
        const val EXTRA_DURATION = "duration"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 获取text参数
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Hello from ToastService!"
        val duration = intent?.getIntExtra(EXTRA_DURATION, Toast.LENGTH_SHORT) ?: Toast.LENGTH_SHORT
        
        // 显示Toast
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, text, duration).show()
        }
        
        // 创建前台通知（短暂显示后停止服务）
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Toast服务")
            .setContentText("显示消息: $text")
            .setSmallIcon(R.drawable.ic_config)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        
        // 延迟1秒后停止服务
        Handler(Looper.getMainLooper()).postDelayed({
            stopForeground(true)
            stopSelf()
        }, 1000)
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Toast服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Toast服务通知渠道"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}