package com.idlike.kctrl.mgr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.DynamicColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var isServiceRunning = false
    private var hasSuPermission = false
    private var hasCameraPermission = false
    private var pendingFloatingText: String? = null
    private var pendingFloatingIcon: Int? = null
    private var pendingFloatingWidth: Int? = null
    private var pendingCustomIcon: String? = null

    companion object {
        const val KCTRL_MODULE_PATH = "/data/adb/modules/kctrl"
        const val PID_FILE = "$KCTRL_MODULE_PATH/mpid.txt"
        const val CONFIG_FILE = "$KCTRL_MODULE_PATH/config.txt"
        const val SERVICE_SCRIPT = "$KCTRL_MODULE_PATH/service.sh"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启用动态颜色（莫奈取色）
        DynamicColors.applyToActivityIfAvailable(this)

        setContentView(R.layout.activity_main)

        setupStatusBar()
        initViews()
        checkCameraPermission()
        checkSuPermission()
        checkServiceStatus()
        setupViewPager()

        // 检查悬浮窗权限
        FloatingWindowManager.requestOverlayPermission(this)
        
        // 处理AM命令启动悬浮窗的意图
        handleFloatingWindowIntent(intent)
        
        // 自动检查更新
        checkForUpdatesOnStartup()
        
        // 检查并显示崩溃提示（仅一次）
        checkAndShowCrashNotification()
    }

    private fun setupStatusBar() {
        val statusBarPlaceholder = findViewById<View>(R.id.statusBarPlaceholder)
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            val statusBarHeight = resources.getDimensionPixelSize(resourceId)
            val layoutParams = statusBarPlaceholder.layoutParams
            layoutParams.height = statusBarHeight
            statusBarPlaceholder.layoutParams = layoutParams
        }
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
    }
    
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            requestCameraPermission()
        }
    }
    
    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // 显示权限说明对话框
            AlertDialog.Builder(this)
                .setTitle("摄像头权限")
                .setMessage("应用需要摄像头权限来控制手电筒功能。如果不授权，手电筒相关功能将不可用，但您仍可以使用应用的其他功能。")
                .setPositiveButton("授权") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton("拒绝") { _, _ ->
                    hasCameraPermission = false
                    showCameraPermissionDeniedMessage()
                }
                .setCancelable(false)
                .show()
        } else {
            // 直接请求权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun showCameraPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "摄像头权限被拒绝，手电筒功能将不可用",
            Toast.LENGTH_LONG
        ).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    hasCameraPermission = true
                    Toast.makeText(this, "摄像头权限已授权，手电筒功能可用", Toast.LENGTH_SHORT).show()
                } else {
                    hasCameraPermission = false
                    showCameraPermissionDeniedMessage()
                }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            FloatingWindowManager.REQUEST_CODE_OVERLAY_PERMISSION -> {
                if (FloatingWindowManager.hasOverlayPermission(this)) {
                    Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show()
                    pendingFloatingText?.let { text ->
                        pendingFloatingIcon?.let { icon ->
                            val width = pendingFloatingWidth ?: 160
                            FloatingWindowManager.startFloatingWindow(this, text, icon, width, -1, -1, pendingCustomIcon)
                            pendingFloatingText = null
                            pendingFloatingIcon = null
                            pendingFloatingWidth = null
                            pendingCustomIcon = null
                        }
                    }
                } else {
                    Toast.makeText(this, "悬浮窗权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleFloatingWindowIntent(intent)
    }
    
    private fun handleFloatingWindowIntent(intent: Intent?) {
        if (intent == null) return
        
        val isFloating = intent.getBooleanExtra("floating", false)
        if (isFloating) {
            val text = intent.getStringExtra("text") ?: "KCtrl 控制器"
            val iconName = intent.getStringExtra("icon") ?: "ic_config"
            val widthDp = intent.getIntExtra("width", 160)
            val customIconPath = intent.getStringExtra("extra_custom_icon_path")
            
            // 映射图标名称到资源ID
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
            
            // 延迟启动悬浮窗，确保Activity已完全初始化
            window.decorView.post {
                if (FloatingWindowManager.hasOverlayPermission(this)) {
                    FloatingWindowManager.startFloatingWindow(this, text, iconId, widthDp, -1, -1, customIconPath)
                    Toast.makeText(this, "悬浮窗已通过命令启动", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
                    pendingFloatingText = text
                    pendingFloatingIcon = iconId
                    pendingFloatingWidth = widthDp
                    pendingCustomIcon = customIconPath
                    FloatingWindowManager.requestOverlayPermission(this)
                }
            }
        }
    }
    
    private fun checkSuPermission() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)
                outputStream.writeBytes("id\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()
                outputStream.close()
                
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText()
                
                // 检查是否成功获取到root权限（uid=0表示root）
                hasSuPermission = exitCode == 0 && output.contains("uid=0")
                
                runOnUiThread {
                    if (!hasSuPermission) {
                        android.widget.Toast.makeText(
                            this,
                            "无法获取Root权限，应用功能将受限",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                hasSuPermission = false
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this,
                        "无法获取Root权限，应用功能将受限",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun checkServiceStatus() {
        Thread {
            val status = this@MainActivity.checkKctrlStatus()
            isServiceRunning = status != null
            this@MainActivity.runOnUiThread {
                updateTabsAccessibility()
            }
        }.start()
    }
    
    private fun checkForUpdatesOnStartup() {
        // 延迟执行，确保UI完全加载
        window.decorView.postDelayed({
            // 先显示一个前置对话框，解决焦点问题
            val loadingDialog = AlertDialog.Builder(this@MainActivity)
                .setTitle("检查更新")
                .setMessage("正在检查新版本...")
                .setCancelable(false)
                .setNegativeButton("后台检查") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                
            loadingDialog.show()
            
            lifecycleScope.launch {
                try {
                    val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
                    val latestVersionCode = getLatestVersionCode()
                    val updateLog = getUpdateLog()
                    
                    Log.d("MainActivity", "当前版本: $currentVersionCode, 最新版本: $latestVersionCode")
                    
                    // 延迟关闭加载对话框并显示结果
                    kotlinx.coroutines.delay(100) // 确保前置对话框有足够显示时间
                    loadingDialog.dismiss()
                    
                    if (latestVersionCode > currentVersionCode) {
                        Log.d("MainActivity", "发现新版本，显示更新对话框")
                        showUpdateDialog(updateLog)
                    } else {
                        Log.d("MainActivity", "当前已是最新版本")
                    }
                } catch (e: Exception) {
                    loadingDialog.dismiss()
                    Log.e("MainActivity", "更新检查失败", e)
                }
            }
        }, 100) // 延迟0.1秒执行
    }
    
    private fun checkAndShowCrashNotification() {
        try {
            val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
            val lastShownTime = prefs.getLong("last_crash_notification", 0)
            val currentTime = System.currentTimeMillis()
            
            // 如果距离上次提示超过1小时，或者从未提示过
            if (currentTime - lastShownTime > 60 * 60 * 1000) {
                val crashLogs = (applicationContext as? KCtrlApplication)?.getCrashLogs()
                if (!crashLogs.isNullOrEmpty()) {
                    val lastCrashFile = crashLogs.first()
                    val lastModified = java.util.Date(lastCrashFile.lastModified())
                    val timeAgo = getTimeAgo(lastModified)
                    
                    // 检查是否是最近24小时内的崩溃
                    val oneDayAgo = java.util.Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
                    if (lastModified.after(oneDayAgo)) {
                        // 记录提示时间
                        prefs.edit().putLong("last_crash_notification", currentTime).apply()
                        
                        // 延迟显示提示，确保界面完全加载
                        window.decorView.postDelayed({
                            showCrashNotification(crashLogs.size, timeAgo)
                        }, 1000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "检查崩溃提示失败", e)
        }
    }
    
    private fun showCrashNotification(count: Int, timeAgo: String) {
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("检测到应用崩溃")
                .setMessage("发现 $count 个崩溃日志，最近一次发生在$timeAgo。\n\n是否立即查看崩溃详情？")
                .setPositiveButton("立即查看") { _, _ ->
                    // 跳转到关于页面
                    viewPager.currentItem = 4 // 关于页面索引
                }
                .setNegativeButton("稍后", null)
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "显示崩溃提示失败", e)
        }
    }
    
    private fun getTimeAgo(date: java.util.Date): String {
        val now = java.util.Date()
        val diff = now.time - date.time
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}天前"
            hours > 0 -> "${hours}小时前"
            minutes > 0 -> "${minutes}分钟前"
            else -> "刚刚"
        }
    }
    
    private suspend fun getLatestVersionCode(): Int {
        return withContext(Dispatchers.IO) {
            try {
                // 实际网络检查（注释掉用于测试）
                val url = URL("http://idlike.134.w21.net/kctrl/v.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val scanner = Scanner(inputStream)
                    if (scanner.hasNextInt()) {
                        scanner.nextInt()
                    } else {
                        0
                    }
                } else {
                    0
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "获取版本号失败", e)
                0
            }
        }
    }
    
    private suspend fun getUpdateLog(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://idlike.134.w21.net/kctrl/vup.txt")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext "暂无更新日志"
            }
            
            val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
            val logText = reader.readText().trim()
            reader.close()
            connection.disconnect()
            
            if (logText.isEmpty()) {
                return@withContext "暂无更新日志"
            }
            
            logText
        } catch (e: Exception) {
            return@withContext "获取更新日志失败"
        }
    }
    
    private fun showUpdateDialog(updateLog: String = "") {
        lifecycleScope.launch {
            val log = if (updateLog.isEmpty()) getUpdateLog() else updateLog
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("发现新版本")
                    .setMessage("检测到新版本可用，是否立即下载更新？\n\n更新日志：\n$log")
                    .setPositiveButton("立即更新") { dialog, _ ->
                        dialog.dismiss()
                        // 跳转到关于页面并自动触发检查更新
                        viewPager.currentItem = 4 // 关于页面索引
                        // 设置标志，在AboutFragment中自动触发检查更新
                        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        sharedPref.edit().putBoolean("auto_check_update", true).apply()
                    }
                    .setNegativeButton("稍后提醒") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false) // 防止点击外部区域关闭对话框
                    .create()
                    .show()
            } catch (e: Exception) {
                Log.e("MainActivity", "显示更新对话框失败: ${e.message}")
            }
        }
    }

    private fun updateTabsAccessibility() {
        // 更新Tab的可访问性
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            when (i) {
                1, 2 -> { // 按键配置和系统配置页面
                    val isAccessible = isServiceRunning && hasSuPermission
                    tab?.view?.isEnabled = isAccessible
                    tab?.view?.alpha = if (isAccessible) 1.0f else 0.5f
                }
                else -> {
                    tab?.view?.isEnabled = true
                    tab?.view?.alpha = 1.0f
                }
            }
        }
    }
    
    // 公共方法，供其他Fragment调用以更新服务状态
    fun updateServiceStatus() {
        checkServiceStatus()
    }
    
    // 公共方法，供其他Fragment获取su权限状态
    fun hasSuPermission(): Boolean {
        return hasSuPermission
    }
    
    // 公共方法，供其他Fragment获取摄像头权限状态
    fun hasCameraPermission(): Boolean {
        return hasCameraPermission
    }
    
    // 公共方法，供其他Fragment获取悬浮窗权限状态
    fun hasOverlayPermission(): Boolean {
        return FloatingWindowManager.hasOverlayPermission(this)
    }
    
    // 公共方法，启动悬浮窗
    fun startFloatingWindow(text: String = "悬浮窗", iconId: Int = R.drawable.ic_config, x: Int = -1, y: Int = -1) {
        FloatingWindowManager.startFloatingWindow(this, text, iconId, 160, x, y)
    }
    
    // 公共方法，隐藏悬浮窗
    fun hideFloatingWindow() {
        FloatingWindowManager.hideFloatingWindow(this)
    }
    
    // 公共方法，更新悬浮窗
    fun updateFloatingWindow(text: String, iconId: Int = R.drawable.ic_config, x: Int = -1, y: Int = -1) {
        FloatingWindowManager.updateFloatingWindow(this, text, iconId, 160, x, y)
    }
    
    // 公共方法，停止悬浮窗服务
    fun stopFloatingWindow() {
        FloatingWindowManager.stopFloatingWindow(this)
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                    0 -> {
                        tab.text = "状态"
                        tab.icon = resources.getDrawable(R.drawable.ic_status, null)
                    }
                    2 -> {
                        tab.text = "系统"
                        tab.icon = resources.getDrawable(R.drawable.ic_module, null)
                    }
                    3 -> {
                        tab.text = "社区"
                        tab.icon = resources.getDrawable(android.R.drawable.ic_menu_share, null)
                    }
                    1 -> {
                        tab.text = "配置"
                        tab.icon = resources.getDrawable(R.drawable.ic_config, null)
                    }
                    4 -> {
                        tab.text = "关于"
                        tab.icon = resources.getDrawable(android.R.drawable.ic_menu_info_details, null)
                    }
                    else -> tab.text = "未知"
                }
        }.attach()
        
        // 添加页面切换监听器，阻止访问被禁用的页面
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 检查是否尝试访问被禁用的页面
                if ((position == 1 || position == 2) && (!isServiceRunning || !hasSuPermission)) {
                    // 显示提示并切换回运行状态页面
                    val message = if (!hasSuPermission) {
                        "无Root权限，无法访问此功能"
                    } else {
                        "KCtrl服务未运行，无法访问此功能"
                    }
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        message,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    viewPager.setCurrentItem(0, true)
                }
            }
        })
    }

    // ViewPager 适配器
    private class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                    0 -> StatusFragment()
                    2 -> ModuleFragment()
                    3 -> CommunityFragment()
                    1 -> ConfigFragment()
                    4 -> AboutFragment()
                    else -> StatusFragment()
                }
        }
    }

    // Root Shell 执行方法
    fun executeRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val errorStream = process.errorStream

            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = process.inputStream.bufferedReader().readText()
            val error = errorStream.bufferedReader().readText()
            
            val exitCode = process.waitFor()
            
            android.util.Log.d("MainActivity", "执行命令: $command")
            android.util.Log.d("MainActivity", "返回码: $exitCode")
            android.util.Log.d("MainActivity", "输出: $output")
            if (error.isNotEmpty()) {
                android.util.Log.d("MainActivity", "错误: $error")
            }
            
            if (exitCode == 0) {
                output.trim()
            } else {
                "ERROR: $error"
            }
        } catch (e: IOException) {
            android.util.Log.e("MainActivity", "命令执行IO异常", e)
            null
        } catch (e: InterruptedException) {
            android.util.Log.e("MainActivity", "命令执行中断异常", e)
            null
        }
    }

    // 异步执行root命令
    fun executeRootCommandAsync(command: String, callback: (String?) -> Unit) {
        Thread {
            val result = executeRootCommand(command)
            this@MainActivity.runOnUiThread {
                callback(result)
            }
        }.start()
    }

    // 检查 KCTRL 运行状态
    fun checkKctrlStatus(): String? {
        val result = executeRootCommand("test -f $PID_FILE && cat $PID_FILE || echo ''")
        val pid = if (result?.isNotEmpty() == true) result.trim() else null
        
        // 如果有PID，进一步检查进程是否真的在运行
        if (pid != null && pid.isNotEmpty()) {
            val processCheck = executeRootCommand("ps -p $pid -o pid= 2>/dev/null || echo ''")
            return if (processCheck?.trim()?.isNotEmpty() == true) pid else null
        }
        
        return null
    }
    
    // 读取模块信息
    fun readModuleInfo(): Map<String, String> {
        val moduleInfo = mutableMapOf<String, String>()
        
        try {
            val modulePropPath = "$KCTRL_MODULE_PATH/module.prop"
            val result = executeRootCommand("cat $modulePropPath 2>/dev/null")
            
            if (!result.isNullOrEmpty()) {
                result.lines().forEach { line ->
                    if (line.contains('=')) {
                        val parts = line.split('=', limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            moduleInfo[key] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略错误，返回空map
        }
        
        return moduleInfo
    }
    
    // 检查模块是否安装
    fun isModuleInstalled(): Boolean {
        val result = executeRootCommand("test -d $KCTRL_MODULE_PATH && echo 'installed' || echo ''")
        return result?.trim() == "installed"
    }
    
    // 停止 KCTRL 服务
    fun stopKctrlService(): Boolean {
        var stopped = false
        
        // 首先通过PID文件结束进程
        val pid = checkKctrlStatus()
        if (pid != null) {
            executeRootCommand("kill -TERM $pid 2>/dev/null || kill -KILL $pid 2>/dev/null")
            stopped = true
        }
        
        // 按进程名称查找并结束kctrl服务进程，避免留下多个进程
        // 使用更精确的匹配，避免误杀包含kctrl的其他进程（如本app）
        val killByName = executeRootCommand("pkill -x kctrl 2>/dev/null || killall -x kctrl 2>/dev/null || true")
        if (killByName != null) {
            stopped = true
        }
        
        // 等待进程完全停止
        Thread.sleep(1000)
        
        // 再次检查是否还有kctrl服务进程残留（精确匹配进程名）
        val remainingProcesses = executeRootCommand("pgrep -x kctrl 2>/dev/null || true")
        if (remainingProcesses?.trim()?.isNotEmpty() == true) {
            // 如果还有残留进程，强制结束（仅精确匹配的kctrl进程）
            executeRootCommand("pkill -9 -x kctrl 2>/dev/null || killall -9 -x kctrl 2>/dev/null || true")
        }
        
        // 清理PID文件
        executeRootCommand("rm -f $PID_FILE")
        
        return stopped
    }
    
    // 启动 KCTRL 服务
    fun startKctrlService(): Boolean {

        val result = executeRootCommand("cd $KCTRL_MODULE_PATH && chmod 777 $SERVICE_SCRIPT && sh $SERVICE_SCRIPT")
        result?.let { Log.i("start_service", it) }
        return result != null
    }
    
    // 重启 KCTRL 服务
    fun restartKctrlService(): Boolean {
        stopKctrlService()
        Thread.sleep(2000) // 等待2秒确保完全停止
        return startKctrlService()
    }

    // 读取配置文件
    fun readConfigFile(): String? {
        val result = executeRootCommand("cat $CONFIG_FILE 2>/dev/null")
        return if (result.isNullOrEmpty()) {
            null
        } else {
            try {
                // 先尝试base64解码（兼容旧格式）
                val decoded = android.util.Base64.decode(result.trim(), android.util.Base64.DEFAULT)
                String(decoded, Charsets.UTF_8)
            } catch (e: Exception) {
                // 如果不是base64编码，直接返回原内容（新格式UTF-8）
                result
            }
        }
    }

    // 写入配置文件
    fun writeConfigFile(content: String): Boolean {
        return try {
            // 使用base64编码确保中文字符正确保存
            val encoded = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val command = "echo '$encoded' | base64 -d > $CONFIG_FILE"
            val result = executeRootCommand(command)
            result != null
        } catch (e: Exception) {
            false
        }
    }

    // 异步读取配置文件
    fun readConfigFileAsync(callback: (String?) -> Unit) {
        executeRootCommandAsync("cat $CONFIG_FILE 2>/dev/null") { result ->
            val configContent = if (result.isNullOrEmpty()) {
                null
            } else {
                try {
                    // 先尝试base64解码（兼容旧格式）
                    val decoded = android.util.Base64.decode(result.trim(), android.util.Base64.DEFAULT)
                    String(decoded, Charsets.UTF_8)
                } catch (e: Exception) {
                    // 如果不是base64编码，直接返回原内容（新格式UTF-8）
                    result
                }
            }
            callback(configContent)
        }
    }

    // 异步写入配置文件
    fun writeConfigFileAsync(content: String, callback: (Boolean) -> Unit) {
        try {
            // 使用base64编码确保中文字符正确保存
            val encoded = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val command = "echo '$encoded' | base64 -d > $CONFIG_FILE"
            executeRootCommandAsync(command) { result ->
                callback(result != null)
            }
        } catch (e: Exception) {
            callback(false)
        }
    }
    
    /**
     * 启动音频服务播放启动音效
     */
    private fun startAudioService() {
        try {
            val intent = Intent(this, AudioService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // 忽略启动音频服务的错误，不影响主要功能
        }
    }
}