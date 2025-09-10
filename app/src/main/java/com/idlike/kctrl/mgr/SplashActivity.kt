package com.idlike.kctrl.mgr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 设置沉浸式状态栏
        setupEdgeToEdge()
        
        // 延迟启动动画，确保布局完成
        val rootLayout = findViewById<View>(R.id.rootLayout)
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                startSplashAnimation()
            }
        })
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    private fun startSplashAnimation() {
        val expandCircle = findViewById<View>(R.id.expandCircle)
        val ivLogo = findViewById<View>(R.id.ivLogo)

        // 设置初始状态
        ivLogo.alpha = 0f
        ivLogo.scaleX = 0.8f
        ivLogo.scaleY = 0.8f
        
        // Logo淡入动画
        ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 红色圆形扩散动画
        expandCircle.animate()
            .scaleX(50f)
            .scaleY(50f)
            .setDuration(1500)
            .setStartDelay(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 延迟跳转到MainActivity
        expandCircle.postDelayed({
            navigateToMainActivity()
        }, 1000)
    }

    private fun navigateToMainActivity() {
        // 先检查Root权限，再检查模块是否安装
        checkRootPermission()
    }
    
    private fun checkRootPermission() {
        Thread {
            val hasRoot = hasSuPermission()
            runOnUiThread {
                if (!hasRoot) {
                    showNoRootDialog()
                } else {
                    checkModuleInstalled()
                }
            }
        }.start()
    }
    
    private fun checkModuleInstalled() {
        Thread {
            val isModuleInstalled = isModuleInstalled()
            runOnUiThread {
                if (!isModuleInstalled) {
                    showModuleInstallDialog()
                } else {
                    startMainActivity()
                }
            }
        }.start()
    }
    
    private fun isModuleInstalled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = java.io.DataOutputStream(process.outputStream)
            val moduleDir = "/data/adb/modules/kctrl"
            
            outputStream.writeBytes("test -d $moduleDir && echo 'installed' || echo ''\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            
            output == "installed"
        } catch (e: Exception) {
            false
        }
    }
    
    private fun showNoRootDialog() {
        // 使用MaterialAlertDialogBuilder创建莫奈风格的对话框
        MaterialAlertDialogBuilder(this)
            .setTitle("无Root权限")
            .setMessage("检测到设备未获取Root权限，KCtrl控制器需要Root权限才能正常工作。")
            .setNegativeButton("退出") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showModuleInstallDialog() {
        // 使用MaterialAlertDialogBuilder创建莫奈风格的对话框
        MaterialAlertDialogBuilder(this)
            .setTitle("模块未安装")
            .setMessage("检测到KCtrl模块未安装，需要安装模块才能使用完整功能。")
            .setPositiveButton("立即安装") { dialog, _ ->
                dialog.dismiss()
                hotUpdateModuleFromAssets()
            }
            .setNegativeButton("退出") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        
        // 处理从通知或其他地方启动的意图
        intent.putExtras(intent)
        
        startActivity(intent)
        finish()
        
        // 添加过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java",
        ReplaceWith("super.onBackPressed()", "androidx.appcompat.app.AppCompatActivity")
    )
    override fun onBackPressed() {
        super.onBackPressed()
        // 开屏期间禁止返回键
    }
    
    private fun hotUpdateModuleFromAssets() {
        if (!hasSuPermission()) {
            android.widget.Toast.makeText(this, "无Root权限，无法热更新模块", android.widget.Toast.LENGTH_SHORT).show()
            startMainActivity()
            return
        }
        
        android.widget.Toast.makeText(this, "正在从内置资源安装模块...", android.widget.Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                val tempDir = getDir("temp_module", Context.MODE_PRIVATE)
                val zipFile = File(tempDir, "module.zip")
                val extractDir = File(tempDir, "extracted")
                
                // 清理临时目录
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                extractDir.mkdirs()
                
                // 从assets复制ZIP到临时目录
                assets.open("module.zip").use { input ->
                    FileOutputStream(zipFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 解压ZIP文件
                extractZipFile(zipFile, extractDir)
                
                val success = extractAndCopyModule(Uri.fromFile(zipFile))
                runOnUiThread {
                    if (success) {
                        android.widget.Toast.makeText(this, "模块安装成功，正在启动应用...", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // 延迟启动主界面，让用户看到提示
                        Handler(Looper.getMainLooper()).postDelayed({
                            startMainActivity()
                        }, 2000)
                    } else {
                        android.widget.Toast.makeText(this, "模块安装失败", android.widget.Toast.LENGTH_SHORT).show()
                        startMainActivity()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    android.widget.Toast.makeText(this, "安装出错: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    startMainActivity()
                }
            }
        }.start()
    }
    
    private fun hasSuPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            exitCode == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun extractZipFile(zipFile: File, destDir: File): Boolean {
        return try {
            val zipInputStream = java.util.zip.ZipInputStream(zipFile.inputStream())
            var zipEntry = zipInputStream.nextEntry
            
            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)
                
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    // 创建父目录
                    newFile.parentFile?.mkdirs()
                    
                    // 解压文件
                    newFile.outputStream().use { fileOutputStream ->
                        zipInputStream.copyTo(fileOutputStream)
                    }
                }
                
                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }
            
            zipInputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun extractAndCopyModule(zipUri: Uri): Boolean {
        return try {
            val tempDir = getDir("temp_module", Context.MODE_PRIVATE)
            val extractDir = File(tempDir, "extracted")
            val moduleDir = "/data/adb/modules/kctrl"
            
            // 确保临时目录存在
            tempDir.mkdirs()
            extractDir.mkdirs()
            
            // 复制ZIP到临时目录
            val zipFile = File(tempDir, "module.zip")
            contentResolver.openInputStream(zipUri)?.use { input ->
                FileOutputStream(zipFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // 解压ZIP文件
            if (!extractZipFile(zipFile, extractDir)) {
                return false
            }
            
            // 创建模块目录
            executeRootCommand("mkdir -p $moduleDir")
            
            // 备份配置文件和脚本目录
            executeRootCommand("cp -f $moduleDir/config.txt $tempDir/config.txt.bak 2>/dev/null || true")
            executeRootCommand("cp -rf $moduleDir/scripts $tempDir/scripts.bak 2>/dev/null || true")
            
            // 清理目标目录
            executeRootCommand("rm -rf $moduleDir/*")
            
            // 复制文件到模块目录
            executeRootCommand("cp -rf $extractDir/* $moduleDir/")
            
            // 恢复配置文件和脚本目录
            executeRootCommand("cp -f $tempDir/config.txt.bak $moduleDir/config.txt 2>/dev/null || true")
            executeRootCommand("cp -rf $tempDir/scripts.bak/* $moduleDir/scripts/ 2>/dev/null || true")
            
            // 设置权限
            executeRootCommand("chmod -R 755 $moduleDir")
            executeRootCommand("chmod 644 $moduleDir/module.prop")
            
            // 清理临时文件
            tempDir.deleteRecursively()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun executeRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = java.io.DataOutputStream(process.outputStream)
            val errorStream = process.errorStream

            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = process.inputStream.bufferedReader().readText()
            val error = errorStream.bufferedReader().readText()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                output.trim()
            } else {
                "ERROR: $error"
            }
        } catch (e: java.io.IOException) {
            null
        } catch (e: InterruptedException) {
            null
        }
    }
}