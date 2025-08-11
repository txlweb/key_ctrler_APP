package com.idlike.kctrl.mgr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.DynamicColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.DataOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var isServiceRunning = false
    private var hasSuPermission = false
    private var hasCameraPermission = false

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
                } else {
                    Toast.makeText(this, "悬浮窗权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show()
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
            val status = checkKctrlStatus()
            isServiceRunning = status != null
            runOnUiThread {
                updateTabsAccessibility()
            }
        }.start()
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
    fun startFloatingWindow(text: String = "悬浮窗", iconId: Int = R.drawable.ic_config) {
        FloatingWindowManager.startFloatingWindow(this, text, iconId)
    }
    
    // 公共方法，隐藏悬浮窗
    fun hideFloatingWindow() {
        FloatingWindowManager.hideFloatingWindow(this)
    }
    
    // 公共方法，更新悬浮窗
    fun updateFloatingWindow(text: String, iconId: Int = R.drawable.ic_config) {
        FloatingWindowManager.updateFloatingWindow(this, text, iconId)
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
                1 -> {
                    tab.text = "配置"
                    tab.icon = resources.getDrawable(R.drawable.ic_config, null)
                }
                2 -> {
                    tab.text = "系统"
                    tab.icon = resources.getDrawable(R.drawable.ic_module, null)
                }
                3 -> {
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
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> StatusFragment()
                1 -> ConfigFragment()
                2 -> ModuleFragment()
                3 -> AboutFragment()
                else -> StatusFragment()
            }
        }
    }

    // Root Shell 执行方法
    fun executeRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)

            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()

            result.trim()
        } catch (e: IOException) {
            null
        } catch (e: InterruptedException) {
            null
        }
    }

    // 异步执行root命令
    fun executeRootCommandAsync(command: String, callback: (String?) -> Unit) {
        Thread {
            val result = executeRootCommand(command)
            runOnUiThread {
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
        val result = executeRootCommand("test -f $SERVICE_SCRIPT && sh $SERVICE_SCRIPT")
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
}