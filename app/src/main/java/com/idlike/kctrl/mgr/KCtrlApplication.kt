package com.idlike.kctrl.mgr

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class KCtrlApplication : Application() {
    
    companion object {
        const val CRASH_LOG_DIR = "crash_logs"
        const val LOGCAT_MAX_LINES = 5000
    }
    
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        Log.d("KCtrlApplication", "崩溃处理器已设置完成")
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.d("CrashHandler", "开始捕获崩溃信息")
                
                // 捕获异常信息
                val crashInfo = StringBuilder()
                crashInfo.append("=".repeat(50))
                crashInfo.append("\nKCtrl 应用崩溃报告")
                crashInfo.append("\n=".repeat(50))
                crashInfo.append("\n\n基本信息:")
                crashInfo.append("\n时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                crashInfo.append("\n应用版本: ${packageManager.getPackageInfo(packageName, 0).versionName}")
                crashInfo.append("\nAndroid版本: ${android.os.Build.VERSION.RELEASE}")
                crashInfo.append("\n设备型号: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                crashInfo.append("\n进程ID: ${Process.myPid()}")
                crashInfo.append("\n线程名: ${thread.name}")
                crashInfo.append("\n线程ID: ${thread.id}")
                crashInfo.append("\n\n异常信息:")
                crashInfo.append("\n异常类型: ${throwable.javaClass.name}")
                crashInfo.append("\n异常消息: ${throwable.message ?: "无消息"}")
                crashInfo.append("\n\n完整堆栈跟踪:")
                crashInfo.append("\n" + "-".repeat(30))
                
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                crashInfo.append(sw.toString())
                crashInfo.append("\n" + "-".repeat(30))
                
                // 收集系统信息
                crashInfo.append("\n\n系统信息:")
                crashInfo.append("\n可用内存: ${Runtime.getRuntime().freeMemory() / 1024 / 1024}MB")
                crashInfo.append("\n总内存: ${Runtime.getRuntime().totalMemory() / 1024 / 1024}MB")
                crashInfo.append("\n最大内存: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
                crashInfo.append("\nCPU核心数: ${Runtime.getRuntime().availableProcessors()}")
                
                // 收集logcat信息
                crashInfo.append("\n\n应用日志 (最近500行):")
                crashInfo.append("\n" + "-".repeat(30))
                crashInfo.append("\n")
                crashInfo.append(getLogcatInfo())
                crashInfo.append("\n" + "=".repeat(50))
                
                // 保存崩溃日志
                saveCrashLog(crashInfo.toString())
                Log.d("CrashHandler", "崩溃日志保存完成")
                
            } catch (e: Exception) {
                // 如果崩溃处理也失败，记录错误
                Log.e("CrashHandler", "处理崩溃时出错", e)
            } finally {
                // 调用默认处理程序
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    private fun getLogcatInfo(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logBuilder = StringBuilder()
            var line: String?
            var lineCount = 0
            
            while (reader.readLine().also { line = it } != null && lineCount < LOGCAT_MAX_LINES) {
                logBuilder.append(line).append("\n")
                lineCount++
            }
            
            reader.close()
            process.destroy()
            
            if (logBuilder.isEmpty()) {
                "无法获取logcat信息"
            } else {
                logBuilder.toString()
            }
        } catch (e: Exception) {
            "获取logcat失败: ${e.message}"
        }
    }
    
    private fun saveCrashLog(crashLog: String) {
        try {
            // 确保内部存储目录存在
            val crashDir = File(filesDir, CRASH_LOG_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")
            
            FileWriter(crashFile).use { writer ->
                writer.write(crashLog)
            }
            
            Log.d("CrashHandler", "崩溃日志已保存到: ${crashFile.absolutePath}")
            
            // 尝试保存到外部存储（兼容Android 10+）
            try {
                val externalFilesDir = getExternalFilesDir(null)
                if (externalFilesDir != null) {
                    val externalCrashDir = File(externalFilesDir, CRASH_LOG_DIR)
                    if (!externalCrashDir.exists()) {
                        externalCrashDir.mkdirs()
                    }
                    val externalCrashFile = File(externalCrashDir, "crash_$timestamp.txt")
                    FileWriter(externalCrashFile).use { writer ->
                        writer.write(crashLog)
                    }
                    Log.d("CrashHandler", "崩溃日志已保存到外部存储: ${externalCrashFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w("CrashHandler", "外部存储保存失败: ${e.message}")
            }
            
            // 尝试保存到共享存储（Android 10+兼容）
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "crash_$timestamp.txt")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/KCtrl/CrashLogs")
                    }
                    
                    val uri = contentResolver.insert(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        values
                    )
                    
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(crashLog.toByteArray())
                        }
                        Log.d("CrashHandler", "崩溃日志已保存到共享存储")
                    }
                }
            } catch (e: Exception) {
                Log.w("CrashHandler", "共享存储保存失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e("CrashHandler", "保存崩溃日志失败", e)
        }
    }
    
    fun getCrashLogs(): List<File> {
        val crashDir = File(filesDir, CRASH_LOG_DIR)
        return if (crashDir.exists()) {
            crashDir.listFiles()?.filter { it.name.startsWith("crash_") && it.extension == "txt" }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }
}