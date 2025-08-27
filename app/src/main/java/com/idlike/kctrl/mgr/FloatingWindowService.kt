package com.idlike.kctrl.mgr

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
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
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_CUSTOM_ICON_PATH = "extra_custom_icon_path"
        const val EXTRA_X = "extra_x"
        const val EXTRA_Y = "extra_y"
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
        checkLockScreenPermissions()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 调试日志
        android.util.Log.d("FloatingWindowService", "onStartCommand called with action: ${intent?.action}")
        android.util.Log.d("FloatingWindowService", "extras: ${intent?.extras?.keySet()?.joinToString { "$it=${intent.extras?.get(it)}" }}")
        
        when (intent?.action) {
            ACTION_SHOW -> {
                val text = intent.getStringExtra(EXTRA_TEXT) 
                    ?: intent.getStringExtra("text") 
                    ?: "悬浮窗"
                val iconId = intent.getIntExtra(EXTRA_ICON_ID, -1).takeIf { it != -1 } 
                    ?: when (intent.getStringExtra("icon")) {
                        "ic_config" -> R.drawable.ic_config
                        "ic_status" -> R.drawable.ic_status
                        "ic_module" -> R.drawable.ic_module
                        "ic_play_arrow" -> R.drawable.ic_play_arrow
                        "ic_edit" -> R.drawable.ic_edit
                        "ic_save" -> R.drawable.ic_save
                        "ic_add" -> R.drawable.ic_add
                        "ic_delete" -> R.drawable.ic_delete
                        "ic_download" -> R.drawable.ic_download
                        "ic_upload" -> R.drawable.ic_upload
                        "ic_arrow_back" -> R.drawable.ic_arrow_back
                        "ic_expand_more" -> R.drawable.ic_expand_more
                        "ic_drag_handle" -> R.drawable.ic_drag_handle
                        "ic_error" -> R.drawable.ic_error
                        "ic_empty" -> R.drawable.ic_empty
                        "ic_placeholder" -> R.drawable.ic_placeholder
                        "qr_donate" -> R.drawable.qr_donate
                        "jy" -> R.drawable.jy
                        "xl" -> R.drawable.xl
                        "zd" -> R.drawable.zd
                        else -> R.drawable.ic_config
                    }
                val widthDp = intent.getIntExtra(EXTRA_WIDTH, -1).takeIf { it != -1 } 
                    ?: intent.getIntExtra("width", 160)
                val customIconPath = intent.getStringExtra(EXTRA_CUSTOM_ICON_PATH)
                val x = intent.getIntExtra(EXTRA_X, -1).let { if (it == -1) intent.getIntExtra("x", -1) else it }
                val y = intent.getIntExtra(EXTRA_Y, -1).let { if (it == -1) intent.getIntExtra("y", -1) else it }
                android.util.Log.d("FloatingWindowService", "ACTION_SHOW: text=$text, iconId=$iconId, widthDp=$widthDp, customIconPath=$customIconPath, x=$x, y=$y")
                showFloatingWindow(text, iconId, widthDp, customIconPath, x, y)
            }
            ACTION_HIDE -> {
                hideFloatingWindow()
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_TEXT) 
                    ?: intent.getStringExtra("text") 
                    ?: "悬浮窗"
                val iconId = intent.getIntExtra(EXTRA_ICON_ID, -1).takeIf { it != -1 } 
                    ?: when (intent.getStringExtra("icon")) {
                        "ic_config" -> R.drawable.ic_config
                        "ic_status" -> R.drawable.ic_status
                        "ic_module" -> R.drawable.ic_module
                        "ic_play_arrow" -> R.drawable.ic_play_arrow
                        "ic_edit" -> R.drawable.ic_edit
                        "ic_save" -> R.drawable.ic_save
                        "ic_add" -> R.drawable.ic_add
                        "ic_delete" -> R.drawable.ic_delete
                        "ic_download" -> R.drawable.ic_download
                        "ic_upload" -> R.drawable.ic_upload
                        "ic_arrow_back" -> R.drawable.ic_arrow_back
                        "ic_expand_more" -> R.drawable.ic_expand_more
                        "ic_drag_handle" -> R.drawable.ic_drag_handle
                        "ic_error" -> R.drawable.ic_error
                        "ic_empty" -> R.drawable.ic_empty
                        "ic_placeholder" -> R.drawable.ic_placeholder
                        "qr_donate" -> R.drawable.qr_donate
                        "jy" -> R.drawable.jy
                        "xl" -> R.drawable.xl
                        "zd" -> R.drawable.zd
                        else -> R.drawable.ic_config
                    }
                val widthDp = intent.getIntExtra(EXTRA_WIDTH, -1).takeIf { it != -1 } 
                    ?: intent.getIntExtra("width", 160)
                val customIconPath = intent.getStringExtra(EXTRA_CUSTOM_ICON_PATH)
                val x = intent.getIntExtra(EXTRA_X, -1).let { if (it == -1) intent.getIntExtra("x", -1) else it }
                val y = intent.getIntExtra(EXTRA_Y, -1).let { if (it == -1) intent.getIntExtra("y", -1) else it }
                android.util.Log.d("FloatingWindowService", "ACTION_UPDATE: x=$x, y=$y, rawX=${intent.getIntExtra("x", -1)}, rawY=${intent.getIntExtra("y", -1)}")
                updateFloatingWindow(text, iconId, widthDp, customIconPath, x, y)
            }
            else -> {
                // 处理AM命令格式
                val text = intent?.getStringExtra("text") ?: "悬浮窗"
                val iconName = intent?.getStringExtra("icon") ?: "ic_config"
                val iconId = when (iconName) {
                    "ic_config" -> R.drawable.ic_config
                    "ic_status" -> R.drawable.ic_status
                    "ic_module" -> R.drawable.ic_module
                    "ic_play_arrow" -> R.drawable.ic_play_arrow
                    "ic_edit" -> R.drawable.ic_edit
                    "ic_save" -> R.drawable.ic_save
                    "ic_add" -> R.drawable.ic_add
                    "ic_delete" -> R.drawable.ic_delete
                    "ic_download" -> R.drawable.ic_download
                    "ic_upload" -> R.drawable.ic_upload
                    "ic_arrow_back" -> R.drawable.ic_arrow_back
                    "ic_expand_more" -> R.drawable.ic_expand_more
                    "ic_drag_handle" -> R.drawable.ic_drag_handle
                    "ic_error" -> R.drawable.ic_error
                    "ic_empty" -> R.drawable.ic_empty
                    "ic_placeholder" -> R.drawable.ic_placeholder
                    "qr_donate" -> R.drawable.qr_donate
                    "jy" -> R.drawable.jy
                    "xl" -> R.drawable.xl
                    "zd" -> R.drawable.zd
                    else -> R.drawable.ic_config
                }
                val widthDp = intent?.getIntExtra("width", 160) ?: 160
                val customIconPath = intent?.getStringExtra("customIconPath")
                val x = intent?.getIntExtra(EXTRA_X, -1).takeIf { it != -1 } ?: intent?.getIntExtra("x", -1) ?: -1
                val y = intent?.getIntExtra(EXTRA_Y, -1).takeIf { it != -1 } ?: intent?.getIntExtra("y", -1) ?: -1
                
                android.util.Log.d("FloatingWindowService", "ELSE: text=$text, iconId=$iconId, widthDp=$widthDp, customIconPath=$customIconPath")
                android.util.Log.d("FloatingWindowService", "Screen info: width=${resources.displayMetrics.widthPixels}, height=${resources.displayMetrics.heightPixels}, density=${resources.displayMetrics.density}")
                showFloatingWindow(text, iconId, widthDp, customIconPath, x, y)
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
    
    private fun checkLockScreenPermissions() {
        try {
            // 检查设备策略管理器权限
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(this, FloatingWindowService::class.java)
            
            // 检查是否是设备所有者
            val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)
            
            // 检查是否有锁屏权限
            val hasLockScreenPermission = devicePolicyManager.isAdminActive(adminComponent)
            
            android.util.Log.d("FloatingWindow", "Device Owner: $isDeviceOwner, Admin Active: $hasLockScreenPermission")
            
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindow", "Error checking lock screen permissions", e)
        }
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
    
    private fun showFloatingWindow(text: String, iconId: Int, widthDp: Int = 160, customIconPath: String? = null, x: Int = -1, y: Int = -1) {
        if (!canDrawOverlays()) {
            return
        }

        if (isShowing) {
            updateFloatingWindow(text, iconId, widthDp, customIconPath)
            return
        }

        try {
            // 创建悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window_layout, null)

            // 设置文本和图标
            floatingView?.let { view ->
                view.findViewById<TextView>(R.id.tv_floating_text)?.text = text
                val ivIcon = view.findViewById<ImageView>(R.id.iv_floating_icon)
                
                // 设置图标：优先使用自定义图标，其次使用资源图标
                when {
                    customIconPath != null && customIconPath.isNotEmpty() -> {
                        android.util.Log.d("FloatingWindowService", "showFloatingWindow: Loading icon from path: $customIconPath")
                        try {
                            val bitmap = BitmapFactory.decodeFile(customIconPath)
                            if (bitmap != null) {
                                android.util.Log.d("FloatingWindowService", "showFloatingWindow: Bitmap loaded successfully: ${bitmap.width}x${bitmap.height}")
                                ivIcon?.setImageBitmap(bitmap)
                            } else {
                                android.util.Log.e("FloatingWindowService", "showFloatingWindow: Failed to load bitmap from file path")
                                ivIcon?.setImageResource(iconId)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FloatingWindowService", "showFloatingWindow: Error loading icon from file", e)
                            ivIcon?.setImageResource(iconId)
                        }
                    }
                    else -> {
                        android.util.Log.d("FloatingWindowService", "showFloatingWindow: No custom icon provided, using resource icon: $iconId")
                        ivIcon?.setImageResource(iconId)
                    }
                }
                
                // 设置触摸监听
                setupTouchListener()
                
                // 设置窗口参数 - 优化锁屏显示
                val params = WindowManager.LayoutParams(
                    (widthDp * resources.displayMetrics.density).toInt(),
                    (32 * resources.displayMetrics.density).toInt(),
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
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                    PixelFormat.TRANSLUCENT
                )
                
                // 确保在锁屏上显示
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    params.token = null // 确保使用系统窗口
                }
                
                // 设置位置 - 处理自定义坐标
                val useCustomX = x != -1
                val useCustomY = y != -1
                
                if (useCustomX || useCustomY) {
                    // 使用自定义坐标（x=-1时居中，y=-1时使用默认值）
                    params.gravity = if (useCustomX) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    params.x = if (useCustomX) x else 0
                    params.y = if (useCustomY) y else (8 * resources.displayMetrics.density).toInt()
                    android.util.Log.d("FloatingWindowService", "Setting position: x=${params.x}, y=${params.y} (customX=$useCustomX, customY=$useCustomY)")
                } else {
                    // 使用默认位置（顶部居中）
                    params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    params.x = 0
                    params.y = (8 * resources.displayMetrics.density).toInt()
                    android.util.Log.d("FloatingWindowService", "Setting default position: x=0, y=${(8 * resources.displayMetrics.density).toInt()}")
                }
                
                // 设置更高的窗口层级
                params.windowAnimations = android.R.style.Animation_Toast
                
                // 初始化动画
                view.alpha = 0f
                view.scaleX = 0f
                view.scaleY = 1f
                
                windowManager?.addView(view, params)
                isShowing = true
                
                // 确保在锁屏状态下唤醒屏幕并显示
                wakeUpScreen()
                ensureLockScreenDisplay()
                
                // 执行动画
                startShowAnimation()
                scheduleAutoHide()
                
                // 强制显示在最上层
                forceShowOnTop()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun forceShowOnTop() {
        try {
            floatingView?.let { view ->
                val params = view.layoutParams as WindowManager.LayoutParams
                
                // 尝试使用最高优先级
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                
                // 添加所有可能的锁屏相关标志
                params.flags = params.flags or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                
                windowManager?.updateViewLayout(view, params)
                
                // 再次强制刷新
                view.post {
                    view.visibility = View.VISIBLE
                    view.alpha = 1.0f
                    view.bringToFront()
                    view.invalidate()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindow", "Error forcing show on top", e)
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
            animatorSet.duration = 250 // 稍微增加动画时间，使消失更平滑
            animatorSet.interpolator = AccelerateInterpolator() // 使用加速插值器，让消失更自然
            
            animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 延迟移除视图，避免闪烁
                    Handler(Looper.getMainLooper()).postDelayed({
                        removeFloatingView()
                    }, 50)
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
    
    private fun updateFloatingWindow(text: String, iconId: Int, widthDp: Int = 160, customIconPath: String? = null, x: Int = -1, y: Int = -1) {
        if (isShowing && floatingView != null && !isAnimating) {
            floatingView?.findViewById<TextView>(R.id.tv_floating_text)?.text = text
            val ivIcon = floatingView?.findViewById<ImageView>(R.id.iv_floating_icon)
            if (customIconPath != null && customIconPath.isNotEmpty()) {
                android.util.Log.d("FloatingWindowService", "Loading icon from path: $customIconPath")
                try {
                    val bitmap = BitmapFactory.decodeFile(customIconPath)
                    if (bitmap != null) {
                        android.util.Log.d("FloatingWindowService", "Bitmap loaded successfully: ${bitmap.width}x${bitmap.height}")
                        ivIcon?.setImageBitmap(bitmap)
                    } else {
                        android.util.Log.e("FloatingWindowService", "Failed to load bitmap from file path")
                        ivIcon?.setImageResource(iconId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FloatingWindowService", "Error loading icon from file", e)
                    ivIcon?.setImageResource(iconId)
                }
            } else {
                android.util.Log.d("FloatingWindowService", "No custom icon provided, using resource icon: $iconId")
                ivIcon?.setImageResource(iconId)
            }

            // 更新宽度和位置
            val params = floatingView?.layoutParams as WindowManager.LayoutParams
            params.width = (widthDp * resources.displayMetrics.density).toInt()
            
            // 更新位置（如果提供了新坐标）
            val useCustomX = x != -1
            val useCustomY = y != -1
            
            if (useCustomX || useCustomY) {
                // 更新坐标（x=-1时保持当前x或居中，y=-1时保持当前y或使用默认值）
                params.gravity = if (useCustomX) Gravity.TOP or Gravity.START else params.gravity
                params.x = if (useCustomX) x else params.x
                params.y = if (useCustomY) y else params.y
                android.util.Log.d("FloatingWindowService", "Updating position: x=${params.x}, y=${params.y} (customX=$useCustomX, customY=$useCustomY)")
            }
            
            windowManager?.updateViewLayout(floatingView, params)

            // 重新设置自动关闭计时器
            scheduleAutoHide()
        }
    }
    
    private fun setupTouchListener() {
        floatingView?.setOnTouchListener { v, event ->
            if (v == null || event == null) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAutoHide() // 触摸时取消自动隐藏
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scheduleAutoHide() // 触摸结束后重新计时
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
    
    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "FloatingWindow:WakeLock"
            )
            wakeLock.acquire(3000) // 延长唤醒时间到3秒
            
            // 注意：在服务中无法直接调用requestDismissKeyguard，
            // 但窗口标志FLAG_DISMISS_KEYGUARD和FLAG_SHOW_WHEN_LOCKED已经足够
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun ensureLockScreenDisplay() {
        try {
            // 确保窗口在锁屏上可见
            floatingView?.let { view ->
                view.visibility = View.VISIBLE
                view.alpha = 1.0f
                view.bringToFront()
                
                // 强制刷新布局
                view.requestLayout()
                view.invalidate()
            }
            
            // 确保窗口管理器更新
            floatingView?.let { view ->
                val params = view.layoutParams as WindowManager.LayoutParams
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                windowManager?.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}