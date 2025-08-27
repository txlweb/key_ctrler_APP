package com.idlike.kctrl.mgr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class StatusFragment : Fragment() {
    private lateinit var tvStatus: TextView
    private lateinit var tvPid: TextView
    private lateinit var statusIndicator: View
    private lateinit var statusCard: MaterialCardView
    private lateinit var tvModuleInfo: TextView
    private lateinit var tvModuleVersion: TextView
    private lateinit var tvSystemArch: TextView
    private lateinit var tvModuleArch: TextView
    private lateinit var btnRefresh: com.google.android.material.button.MaterialButton
    private lateinit var btnStart: com.google.android.material.button.MaterialButton
    private lateinit var btnStop: com.google.android.material.button.MaterialButton
    private lateinit var btnRestart: com.google.android.material.button.MaterialButton
    private lateinit var btnShowFloating: com.google.android.material.button.MaterialButton
    private lateinit var btnHideFloating: com.google.android.material.button.MaterialButton
    private lateinit var btnFloatingDemo: com.google.android.material.button.MaterialButton
    private lateinit var btnHotUpdate: com.google.android.material.button.MaterialButton
    private lateinit var btnComponentExplorer: com.google.android.material.button.MaterialButton
    
    private val PICK_MODULE_FILE = 1001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        updateStatus()
    }

    private fun initViews(view: View) {
        tvStatus = view.findViewById(R.id.tv_status)
        tvPid = view.findViewById(R.id.tv_pid)
        statusIndicator = view.findViewById(R.id.status_indicator)
        statusCard = view.findViewById(R.id.status_card)
        tvModuleInfo = view.findViewById(R.id.tv_module_info)
        tvModuleVersion = view.findViewById(R.id.tv_module_version)
        tvSystemArch = view.findViewById(R.id.tv_system_arch)
        tvModuleArch = view.findViewById(R.id.tv_module_arch)
        btnRefresh = view.findViewById(R.id.btn_refresh)
        btnStart = view.findViewById(R.id.btn_start)
        btnStop = view.findViewById(R.id.btn_stop)
        btnRestart = view.findViewById(R.id.btn_restart)
        btnShowFloating = view.findViewById(R.id.btn_show_floating)
        btnHideFloating = view.findViewById(R.id.btn_hide_floating)
        btnFloatingDemo = view.findViewById(R.id.btn_floating_demo)
        btnHotUpdate = view.findViewById(R.id.btn_hot_update)
        btnComponentExplorer = view.findViewById(R.id.btn_component_explorer)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        btnRefresh.setOnClickListener {
            updateStatus()
        }
        
        btnStart.setOnClickListener {
            startKctrl()
        }
        
        btnStop.setOnClickListener {
            stopKctrl()
        }
        
        btnRestart.setOnClickListener {
            restartKctrl()
        }
        
        btnShowFloating.setOnClickListener {
            showFloatingWindow()
        }
        
        btnHideFloating.setOnClickListener {
            hideFloatingWindow()
        }
        
        btnFloatingDemo.setOnClickListener {
            openFloatingDemo()
        }
        
        btnHotUpdate.setOnClickListener {
            hotUpdateModule()
        }

        btnComponentExplorer.setOnClickListener {
            startActivity(Intent(context, ComponentExplorerActivity::class.java))
        }
    }

    private fun updateStatus() {
        val mainActivity = activity as? MainActivity ?: return
        
        // 异步检查权限和状态，避免阻塞UI
        Thread {
            // 等待权限检查完成，最多等待3秒
            var permissionCheckRetries = 0
            val maxRetries = 30 // 3秒，每次等待100ms
            
            while (!mainActivity.hasSuPermission() && permissionCheckRetries < maxRetries) {
                Thread.sleep(100)
                permissionCheckRetries++
            }
            
            val hasSu = mainActivity.hasSuPermission()
            val isModuleInstalled = if (hasSu) mainActivity.isModuleInstalled() else false
            val moduleInfo = if (hasSu && isModuleInstalled) mainActivity.readModuleInfo() else emptyMap()
            val status = if (hasSu && isModuleInstalled) mainActivity.checkKctrlStatus() else null
            
            // 获取系统架构和模块架构信息
            val systemArch = getSystemArchitecture()
            val moduleArch = if (hasSu && isModuleInstalled) getModuleArchitecture(mainActivity) else "需要Root权限"
            
            // 在主线程更新UI
            activity?.runOnUiThread {
                if (!hasSu) {
                    // 无su权限
                    tvStatus.text = "无法获取"
                    tvPid.text = "需要Root权限"
                    tvModuleInfo.text = "模块状态: 需要Root权限"
                    tvModuleVersion.text = "版本信息: 不可用"
                    tvSystemArch.text = "系统架构: $systemArch"
                    tvModuleArch.text = "模块架构: 需要Root权限"
                    statusIndicator.setBackgroundResource(R.drawable.status_stopped)
                    statusCard.setCardBackgroundColor(
                        resources.getColor(android.R.color.holo_red_light, null)
                    )
                    // 禁用所有操作按钮
                    btnStart.isEnabled = false
                    btnStop.isEnabled = false
                    btnRestart.isEnabled = false
                    btnHotUpdate.isEnabled = false
                } else if (!isModuleInstalled) {
                    // 模块未安装
                    tvStatus.text = "模块未安装"
                    tvPid.text = "请安装KCtrl模块"
                    tvModuleInfo.text = "模块状态: 未检测到模块"
                    tvModuleVersion.text = "版本信息: 模块未安装"
                    tvSystemArch.text = "系统架构: $systemArch"
                    tvModuleArch.text = "模块架构: 未安装"
                    statusIndicator.setBackgroundResource(R.drawable.status_stopped)
                    statusCard.setCardBackgroundColor(
                        resources.getColor(android.R.color.holo_red_light, null)
                    )
                    // 禁用重启按钮
                    btnRestart.isEnabled = false
                    btnRestart.alpha = 0.5f
                } else {
                    // 模块已安装
                    val moduleName = moduleInfo["name"] ?: "KeyCtrler 按键控制器"
                    val moduleVersion = moduleInfo["version"] ?: "未知"
                    val moduleVersionCode = moduleInfo["versionCode"] ?: "未知"
                    val moduleAuthor = moduleInfo["author"] ?: "未知"
                    
                    tvModuleInfo.text = "模块: 已安装"
                    tvModuleVersion.text = "版本: v$moduleVersion ($moduleVersionCode)"
                    tvSystemArch.text = "系统架构: $systemArch"
                    tvModuleArch.text = "模块架构: $moduleArch"
                    
                    // 检查架构一致性
                    val isArchConsistent = when {
                        moduleArch == "需要更新" -> false
                        systemArch == moduleArch -> true
                        else -> false
                    }
                    
                    // 设置颜色
                    val moduleArchColor = if (moduleArch == "需要更新" || !isArchConsistent) {
                        resources.getColor(android.R.color.holo_red_light, null)
                    } else {
                        resources.getColor(android.R.color.darker_gray, null)
                    }
                    tvModuleArch.setTextColor(moduleArchColor)
                    
                    // 如果架构不一致，提示使用热更新
                    if (!isArchConsistent && moduleArch != "需要更新") {
                        tvModuleArch.text = "模块架构: $moduleArch (与系统架构不一致，请使用热更新刷入对应版本)"
                    } else if (moduleArch == "需要更新") {
                        tvModuleArch.text = "模块架构: $moduleArch (请使用热更新刷入对应版本)"
                    }
                    
                    val isRunning = status != null && status.isNotEmpty()
                    // 更新按钮状态
                    btnStart.isEnabled = hasSu && isModuleInstalled && !isRunning
                    btnStop.isEnabled = hasSu && isModuleInstalled && isRunning
                    btnRestart.isEnabled = hasSu && isModuleInstalled && isRunning
                    btnHotUpdate.isEnabled = hasSu && isModuleInstalled
                    
                    if (status != null && status.isNotEmpty()) {
                        // KCTRL 正在运行
                        tvStatus.text = "服务运行中"
                        tvPid.text = "PID: $status"
                        statusIndicator.setBackgroundResource(R.drawable.status_running)
                        statusCard.setCardBackgroundColor(
                            resources.getColor(android.R.color.holo_green_light, null)
                        )
                    } else {
                        // KCTRL 未运行
                        tvStatus.text = "服务未运行"
                        tvPid.text = "点击重启服务按钮启动"
                        statusIndicator.setBackgroundResource(R.drawable.status_stopped)
                        statusCard.setCardBackgroundColor(
                            resources.getColor(android.R.color.holo_orange_light, null)
                        )
                    }
                    
                    // 更新MainActivity的服务状态
                    mainActivity.updateServiceStatus()
                }
            }
        }.start()
    }

    private fun startKctrl() {
        val mainActivity = activity as? MainActivity ?: return
        
        if (!mainActivity.hasSuPermission()) {
            context?.let {
                android.widget.Toast.makeText(it, "无Root权限，无法启动服务", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        context?.let {
            android.widget.Toast.makeText(it, "正在启动服务...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        Thread {
            val success = mainActivity.startKctrlService()
            activity?.runOnUiThread {
                if (success) {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务启动成功", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务启动失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                updateStatus()
                mainActivity.updateServiceStatus()
            }
        }.start()
    }
    
    private fun stopKctrl() {
        val mainActivity = activity as? MainActivity ?: return
        
        if (!mainActivity.hasSuPermission()) {
            context?.let {
                android.widget.Toast.makeText(it, "无Root权限，无法停止服务", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        context?.let {
            android.widget.Toast.makeText(it, "正在停止服务...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        Thread {
            val success = mainActivity.stopKctrlService()
            activity?.runOnUiThread {
                if (success) {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务停止成功", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务停止失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                updateStatus()
                mainActivity.updateServiceStatus()
            }
        }.start()
    }
    
    private fun restartKctrl() {
        val mainActivity = activity as? MainActivity ?: return
        
        // 检查su权限
        if (!mainActivity.hasSuPermission()) {
            context?.let {
                    android.widget.Toast.makeText(it, "无Root权限，无法重启服务", android.widget.Toast.LENGTH_SHORT).show()
                }
            return
        }
        
        // 显示重启中的提示
        context?.let {
                    android.widget.Toast.makeText(it, "正在重启服务...", android.widget.Toast.LENGTH_SHORT).show()
                }
        
        // 禁用重启按钮，防止重复点击
        btnRestart.isEnabled = false
        btnRestart.text = "重启中..."
        
        // 异步执行重启操作
        Thread {
            val success = mainActivity.restartKctrlService()
            
            // 在主线程更新UI
            activity?.runOnUiThread {
                btnRestart.isEnabled = true
                btnRestart.text = "重启服务"
                
                if (success) {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务重启成功", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务重启失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                // 重启后更新状态显示
                updateStatus()
                // 更新MainActivity的服务状态
                mainActivity.updateServiceStatus()
            }
        }.start()
    }
    
    private fun hotUpdateModule() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, PICK_MODULE_FILE)
    }
    
    private fun showFloatingWindow() {
        val mainActivity = activity as? MainActivity ?: return
        
        if (!mainActivity.hasOverlayPermission()) {
            FloatingWindowManager.requestOverlayPermission(mainActivity)
            return
        }
        
        // 显示悬浮窗，可以自定义文本和图标
        mainActivity.startFloatingWindow("KCtrl 控制器", R.drawable.ic_config)
        
        context?.let {
            android.widget.Toast.makeText(it, "悬浮窗已显示", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideFloatingWindow() {
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.hideFloatingWindow()
        
        context?.let {
            android.widget.Toast.makeText(it, "悬浮窗已隐藏", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFloatingDemo() {
        val intent = Intent(context, FloatingWindowDemoActivity::class.java)
        startActivity(intent)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_MODULE_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                hotUpdateModuleWithFile(uri)
            }
        }
    }
    
    private fun hotUpdateModuleWithFile(zipUri: Uri) {
        val mainActivity = activity as? MainActivity ?: return
        
        if (!mainActivity.hasSuPermission()) {
            context?.let {
                android.widget.Toast.makeText(it, "无Root权限，无法热更新模块", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        context?.let {
            android.widget.Toast.makeText(it, "开始热更新模块...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        Thread {
            val success = extractAndCopyModule(zipUri)
            activity?.runOnUiThread {
                if (success) {
                    android.widget.Toast.makeText(context, "模块热更新成功，正在重启服务...", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // 重启服务
                    mainActivity.restartKctrlService()
                    Handler(Looper.getMainLooper()).postDelayed({
                        updateStatus()
                    }, 3000)
                } else {
                    android.widget.Toast.makeText(context, "模块热更新失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun extractAndCopyModule(zipUri: Uri): Boolean {
        val context = context ?: return false
        
        try {
            val inputStream = context.contentResolver.openInputStream(zipUri) ?: return false
            val tempDir = context.getDir("temp_module", Context.MODE_PRIVATE)
            val zipFile = File(tempDir, "module.zip")
            
            // 复制ZIP到临时目录
            FileOutputStream(zipFile).use { output ->
                inputStream.copyTo(output)
            }
            
            // 解压ZIP
            val extractDir = File(tempDir, "extracted")
            extractDir.mkdirs()
            extractZipFile(zipFile, extractDir)

            // 复制到模块目录
            val moduleDir = File("/data/adb/modules/kctrl")
            
            // 使用su权限复制文件，排除config.txt和scripts文件夹
            val mainActivity = activity as? MainActivity ?: return false
            
            // 创建目标目录
            val createDirResult = mainActivity.executeRootCommand("su -c 'mkdir -p ${moduleDir.absolutePath}'")
            if (createDirResult == null) {
                return false
            }
            
            // 先清理目标目录中除config.txt和scripts外的文件
            val cleanCommands = listOf(
                "find ${moduleDir.absolutePath} -type f -not -name \"config.txt\" -not -path \"*/scripts/*\" -delete",
                "find ${moduleDir.absolutePath} -type d -empty -not -path \"*/scripts*\" -delete"
            )
            
            for (command in cleanCommands) {
                mainActivity.executeRootCommand("su -c '$command'")
            }
            
            // 复制文件（排除config.txt和scripts文件夹）
            val commands = listOf(
                "find ${extractDir.absolutePath} -type f -not -name \"config.txt\" -not -path \"*/scripts/*\" -exec cp \"{}\" ${moduleDir.absolutePath}/ \\;",
                "chmod 755 ${moduleDir.absolutePath}",
                "chmod 644 ${moduleDir.absolutePath}/module.prop"
            )
            
            for (command in commands) {
                val result = mainActivity.executeRootCommand("su -c '$command'")
                if (result == null) {
                    return false
                }
            }
            
            // 清理临时文件
            tempDir.deleteRecursively()
            return true
            
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    private fun extractZipFile(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val filePath = File(destDir, entry.name)
                if (!entry.isDirectory) {
                    filePath.parentFile?.mkdirs()
                    FileOutputStream(filePath).use { output ->
                        zipIn.copyTo(output)
                    }
                } else {
                    filePath.mkdirs()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
    
    private fun getSystemArchitecture(): String {
        return try {
            val arch = System.getProperty("os.arch") ?: "未知"
            when {
                arch.contains("arm64") || arch.contains("aarch64") -> "arm64_v8a"
                arch.contains("arm") && !arch.contains("64") -> "arm32_v7a"
                arch.contains("x86_64") -> "x86_64"
                arch.contains("x86") -> "x86"
                else -> arch
            }
        } catch (e: Exception) {
            "未知"
        }
    }
    
    private fun getModuleArchitecture(mainActivity: MainActivity): String {
        return try {
            val moduleDir = "/data/adb/modules/kctrl"
            
            // 检查v8a.lock文件
            val v8aLockResult = mainActivity.executeRootCommand("ls $moduleDir/v8a.lock 2>/dev/null")
            if (v8aLockResult != null && v8aLockResult.isNotEmpty()) {
                return "arm64_v8a"
            }
            
            // 检查v7a.lock文件
            val v7aLockResult = mainActivity.executeRootCommand("ls $moduleDir/v7a.lock 2>/dev/null")
            if (v7aLockResult != null && v7aLockResult.isNotEmpty()) {
                return "arm32_v7a"
            }
            
            // 如果没有找到任何锁文件，提示需要更新
            "需要更新"
        } catch (e: Exception) {
            "检测失败"
        }
    }
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
    
