package com.idlike.kctrl.mgr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.IOException

class AudioService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "audio_channel"
        private const val NOTIFICATION_ID = 2
    }
    
    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建通知渠道
        createNotificationChannel()

        // 创建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音频服务")
            .setContentText("正在播放音效")
            .setSmallIcon(R.drawable.ic_config)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)

        // 播放音频
        playAudio()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "音频服务通知"
            val descriptionText = "用于播放音效的服务通知"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun playAudio() {
        try {
            // 释放之前的MediaPlayer实例
            mediaPlayer?.release()
            
            // 创建新的MediaPlayer实例
            mediaPlayer = MediaPlayer()
            
            // 从assets目录加载音频文件
            val assetFileDescriptor: AssetFileDescriptor = assets.openFd("tip.mp3")
            mediaPlayer?.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
            assetFileDescriptor.close()
            
            // 设置播放完成监听器
            mediaPlayer?.setOnCompletionListener {
                // 播放完成后停止服务
                stopSelf()
            }
            
            // 设置错误监听器
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                android.util.Log.e("AudioService", "MediaPlayer error: what=$what, extra=$extra")
                stopSelf()
                true
            }
            
            // 准备并播放
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            
        } catch (e: IOException) {
            android.util.Log.e("AudioService", "Error playing audio", e)
            stopSelf()
        } catch (e: Exception) {
            android.util.Log.e("AudioService", "Unexpected error", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放MediaPlayer资源
        mediaPlayer?.release()
        mediaPlayer = null
    }
}