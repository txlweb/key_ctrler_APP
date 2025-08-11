package com.idlike.kctrl.mgr

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 2
        
        // Intent extras
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ICON_ID = "extra_icon_id"
        const val ACTION_SHOW = "action_show"
        const val ACTION_HIDE = "action_hide"
        const val ACTION_UPDATE = "action_update"
    }
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isShowing = false
    private var autoHideHandler: Handler? = null
    private var autoHideRunnable: Runnable? = null
    private var isAnimating = false
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        autoHideHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "悬浮窗"
                val iconId = intent.getIntExtra(EXTRA_ICON_ID, R.drawable.ic_config)
                showFloatingWindow(text, iconId)
            }
            ACTION_HIDE -> {
                hideFloatingWindow()
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "悬浮窗"
                val iconId = intent.getIntExtra(EXTRA_ICON_ID, R.drawable.ic_config)
                updateFloatingWindow(text, iconId)
            }
            else -> {
                // 默认显示悬浮窗
                val text = intent?.getStringExtra(EXTRA_TEXT) ?: "悬浮窗"
                val iconId = intent?.getIntExtra(EXTRA_ICON_ID, R.drawable.ic_config) ?: R.drawable.ic_config
                showFloatingWindow(text, iconId)
            }
        }
        
        // 创建前台通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮窗服务")
            .setContentText("悬浮窗正在运行")
            .setSmallIcon(R.drawable.ic_config)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        cancelAutoHide()
        if (isShowing) {
            removeFloatingView()
        }
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗服务通知渠道"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showFloatingWindow(text: String, iconId: Int) {
        if (!canDrawOverlays() || isAnimating) {
            return
        }
        
        if (isShowing) {
            updateFloatingWindow(text, iconId)
            return
        }
        
        try {
            // 创建悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window_layout, null)
            
            // 设置文本和图标
            floatingView?.findViewById<TextView>(R.id.tv_floating_text)?.text = text
            floatingView?.findViewById<ImageView>(R.id.iv_floating_icon)?.setImageResource(iconId)
            
            // 设置触摸监听
            setupTouchListener()
            
            // 设置窗口参数 - 模仿灵动岛显示在状态栏区域
            val params = WindowManager.LayoutParams(
                (160 * resources.displayMetrics.density).toInt(), // 固定宽度160dp
                (32 * resources.displayMetrics.density).toInt(),  // 固定高度32dp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            // 设置位置居中显示，隐藏状态栏后从顶部开始
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0 // 居中显示
            params.y = (8 * resources.displayMetrics.density).toInt() // 距离顶部8dp
            
            // 初始化动画状态 - 左右展开效果
            floatingView?.alpha = 0f
            floatingView?.scaleX = 0f
            floatingView?.scaleY = 1f
            
            windowManager?.addView(floatingView, params)
            isShowing = true
            
            // 执行展开动画
            startShowAnimation()
            
            // 设置2秒后自动关闭
            scheduleAutoHide()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideFloatingWindow() {
        if (!isShowing || isAnimating) {
            return
        }
        
        // 取消自动关闭
        cancelAutoHide()
        
        // 执行关闭动画
        startHideAnimation()
    }
    
    private fun removeFloatingView() {
        try {
            windowManager?.removeView(floatingView)
            floatingView = null
            isShowing = false
            isAnimating = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startShowAnimation() {
        floatingView?.let { view ->
            isAnimating = true
            
            val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
            val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f)
            // Y轴保持不变，实现左右展开效果
            
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(alphaAnimator, scaleXAnimator)
            animatorSet.duration = 300
            animatorSet.interpolator = AccelerateDecelerateInterpolator()
            
            animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    isAnimating = false
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            animatorSet.start()
        }
    }
    
    private fun startHideAnimation() {
        floatingView?.let { view ->
            isAnimating = true
            
            val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
            val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f)
            // Y轴保持不变，实现左右收缩效果
            
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(alphaAnimator, scaleXAnimator)
            animatorSet.duration = 200
            animatorSet.interpolator = AccelerateDecelerateInterpolator()
            
            animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeFloatingView()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    removeFloatingView()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            animatorSet.start()
        }
    }
    
    private fun scheduleAutoHide() {
        cancelAutoHide()
        autoHideRunnable = Runnable {
            if (isShowing && !isAnimating) {
                hideFloatingWindow()
            }
        }
        autoHideHandler?.postDelayed(autoHideRunnable!!, 2000) // 2秒后自动关闭
    }
    
    private fun cancelAutoHide() {
        autoHideRunnable?.let {
            autoHideHandler?.removeCallbacks(it)
            autoHideRunnable = null
        }
    }
    
    private fun updateFloatingWindow(text: String, iconId: Int) {
        if (isShowing && floatingView != null && !isAnimating) {
            floatingView?.findViewById<TextView>(R.id.tv_floating_text)?.text = text
            floatingView?.findViewById<ImageView>(R.id.iv_floating_icon)?.setImageResource(iconId)
            
            // 重新设置自动关闭计时器
            scheduleAutoHide()
        }
    }
    
    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = floatingView?.layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = floatingView?.layoutParams as WindowManager.LayoutParams
                    // 只允许水平移动，保持在顶部区域，模仿灵动岛行为
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val screenWidth = resources.displayMetrics.widthPixels
                    val viewWidth = floatingView?.width ?: 0
                    
                    // 限制X坐标范围，防止移出屏幕，但保持居中对称移动
                    val maxOffset = (screenWidth - viewWidth) / 2
                    params.x = when {
                        newX < -maxOffset -> -maxOffset
                        newX > maxOffset -> maxOffset
                        else -> newX
                    }
                    
                    // Y坐标保持固定在屏幕顶部
                    params.y = (8 * resources.displayMetrics.density).toInt()
                    
                    // 重新设置自动关闭计时器（用户交互时）
                    scheduleAutoHide()
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            // 默认状态栏高度 24dp
            (24 * resources.displayMetrics.density).toInt()
        }
    }
}