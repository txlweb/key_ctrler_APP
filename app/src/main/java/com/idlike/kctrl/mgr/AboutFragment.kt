package com.idlike.kctrl.mgr

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class AboutFragment : Fragment() {
    
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressDialog: AlertDialog? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null
    private var downloadStartTime: Long = 0
    private var lastProgressTime: Long = 0
    private var lastDownloadedBytes: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners(view)
        loadVersionInfo(view)
    }

    private fun setupClickListeners(view: View) {
        // QQ群链接
        view.findViewById<MaterialCardView>(R.id.cardGithub)?.setOnClickListener {
            openUrl("https://qm.qq.com/q/50K8RrHlXq")
        }
        
        // 许可证链接
        view.findViewById<MaterialCardView>(R.id.cardLicense)?.setOnClickListener {
            openUrl("http://idlike.134.w21.net/kctrl")
        }
        
        // 检查更新按钮
        view.findViewById<MaterialButton>(R.id.btnCheckUpdate)?.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun loadVersionInfo(view: View) {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 0
            )
            val versionName = packageInfo.versionName
            view.findViewById<TextView>(R.id.tvAppVersion)?.text = "版本: $versionName"
        } catch (e: Exception) {
            view.findViewById<TextView>(R.id.tvAppVersion)?.text = "版本: 未知"
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // 处理无法打开链接的情况
            Toast.makeText(
                requireContext(),
                "无法打开链接",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show()
                
                val currentVersionCode = getCurrentVersionCode()
                val latestVersionCode = getLatestVersionCode()
                
                Toast.makeText(
                    requireContext(),
                    "当前版本: $currentVersionCode, 最新版本: $latestVersionCode",
                    Toast.LENGTH_LONG
                ).show()
                
                if (latestVersionCode > currentVersionCode) {
                    showUpdateDialog()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "当前已是最新版本",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "检查更新失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 0
            )
            packageInfo.longVersionCode
        } catch (e: Exception) {
            0L
        }
    }
    
    private suspend fun getLatestVersionCode(): Long = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://idlike.134.w21.net/kctrl/v.txt")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP错误: $responseCode")
            }
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val versionText = reader.readText().trim()
            reader.close()
            connection.disconnect()
            
            val versionCode = versionText.toLongOrNull()
            if (versionCode == null) {
                throw Exception("版本号格式错误: $versionText")
            }
            
            versionCode
        } catch (e: Exception) {
            throw Exception("获取最新版本失败: ${e.message}")
        }
    }
    
    private fun showUpdateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("发现新版本")
            .setMessage("检测到新版本可用，是否立即下载更新？")
            .setPositiveButton("立即更新") { _, _ ->
                downloadUpdate()
            }
            .setNegativeButton("稍后更新", null)
            .show()
    }
    
    private fun downloadUpdate() {
        Log.d("AboutFragment", "开始下载更新")
        
        // 预检查
        if (!performPreDownloadChecks()) {
            return
        }
        
        // 清理旧文件
        cleanupOldFiles()
        
        try {
            // 创建下载请求
            val request = createDownloadRequest()
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            if (downloadId == -1L) {
                throw Exception("无法创建下载任务")
            }
            
            Log.d("AboutFragment", "下载任务创建成功，ID: $downloadId")
            
            // 初始化下载状态
            initializeDownloadState()
            
            // 显示进度对话框
            showProgressDialog()
            
            // 注册下载完成监听器
            registerDownloadReceiver()
            
            // 开始监控下载进度
            startProgressMonitoring()
            
            Toast.makeText(requireContext(), "开始下载更新包", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("AboutFragment", "下载启动失败", e)
            showDownloadError("下载启动失败", "无法启动下载：${e.message}\n\n请稍后重试。")
        }
    }
    
    private fun performPreDownloadChecks(): Boolean {
        // 检查网络连接
        if (!isNetworkAvailable()) {
            showDownloadError("网络连接错误", "请检查网络连接后重试。")
            return false
        }
        
        // 检查存储空间
        val availableSpace = Environment.getExternalStorageDirectory().freeSpace
        val requiredSpace = 50 * 1024 * 1024L // 预估需要50MB空间
        if (availableSpace < requiredSpace) {
            showDownloadError(
                "存储空间不足",
                "可用存储空间不足，请清理存储空间后重试。\n\n可用空间：${formatFileSize(availableSpace)}\n需要空间：${formatFileSize(requiredSpace)}"
            )
            return false
        }
        
        return true
    }
    
    private fun cleanupOldFiles() {
        try {
            // 删除旧的下载文件
            val oldDownloadFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "kctrl_update.zip")
            if (oldDownloadFile.exists()) {
                oldDownloadFile.delete()
                Log.d("AboutFragment", "删除旧下载文件: ${oldDownloadFile.absolutePath}")
            }
            
            // 删除旧的解压目录
            val oldExtractDir = File(requireContext().filesDir, "kctrl_extract")
            if (oldExtractDir.exists()) {
                oldExtractDir.deleteRecursively()
                Log.d("AboutFragment", "删除旧解压目录: ${oldExtractDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w("AboutFragment", "清理旧文件时出现异常", e)
        }
    }
    
    private fun createDownloadRequest(): DownloadManager.Request {
        return DownloadManager.Request(Uri.parse("http://idlike.134.w21.net/kctrl/kctrl.zip"))
            .setTitle("KCtrl Manager 更新")
            .setDescription("正在下载更新包...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "kctrl_update.zip")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
    }
    
    private fun initializeDownloadState() {
        downloadStartTime = System.currentTimeMillis()
        lastProgressTime = downloadStartTime
        lastDownloadedBytes = 0
    }
    
    private fun showDownloadError(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("重试") { _, _ -> downloadUpdate() }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showProgressDialog() {
        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal)
        val textView = TextView(requireContext())
        
        progressBar.max = 100
        progressBar.progress = 0
        progressBar.id = android.R.id.progress
        textView.text = "准备下载..."
        textView.setPadding(0, 20, 0, 20)
        textView.id = android.R.id.text1
        
        val layout = android.widget.LinearLayout(requireContext())
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        layout.addView(textView)
        layout.addView(progressBar)
        
        progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("下载更新包")
            .setView(layout)
            .setNegativeButton("取消") { _, _ ->
                cancelDownload()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startProgressMonitoring() {
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = object : Runnable {
            override fun run() {
                updateProgress()
                progressHandler?.postDelayed(this, 500) // 每500ms更新一次
            }
        }
        progressHandler?.post(progressRunnable!!)
    }
    
    private fun updateProgress() {
        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor? = downloadManager.query(query)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                
                // 检查下载超时
                val currentTime = System.currentTimeMillis()
                val hasProgress = bytesDownloaded > lastDownloadedBytes
                
                if (hasProgress) {
                    lastProgressTime = currentTime
                    lastDownloadedBytes = bytesDownloaded
                } else if (status == DownloadManager.STATUS_RUNNING) {
                    val noProgressTime = currentTime - lastProgressTime
                    if (noProgressTime > 60000) { // 60秒无进展
                        stopProgressMonitoring()
                        progressDialog?.dismiss()
                        
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("下载超时")
                            .setMessage("下载长时间无响应，可能是网络连接不稳定。\n\n是否继续等待或重新开始下载？")
                            .setPositiveButton("重新下载") { _, _ ->
                                cancelDownload()
                                downloadUpdate()
                            }
                            .setNeutralButton("继续等待") { _, _ ->
                                lastProgressTime = currentTime // 重置超时计时
                                startProgressMonitoring()
                            }
                            .setNegativeButton("取消") { _, _ ->
                                cancelDownload()
                            }
                            .show()
                        return@use
                    }
                }
                
                if (bytesTotal > 0) {
                    val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                    val progressBar = progressDialog?.findViewById<ProgressBar>(android.R.id.progress)
                    val textView = progressDialog?.findViewById<TextView>(android.R.id.text1)
                    
                    progressBar?.progress = progress
                    textView?.text = "下载进度: $progress% (${formatFileSize(bytesDownloaded)}/${formatFileSize(bytesTotal)})"
                }
                
                when (status) {
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val errorMessage = getDownloadErrorMessage(reason)
                        stopProgressMonitoring()
                        progressDialog?.dismiss()
                        
                        // 根据错误类型提供不同的建议
                        val suggestion = when (reason) {
                            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "请清理存储空间后重试。"
                            DownloadManager.ERROR_HTTP_DATA_ERROR,
                            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "请检查网络连接和服务器状态后重试。"
                            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "请检查存储设备是否可用。"
                            else -> "请检查网络连接后重试。"
                        }
                        
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("下载失败")
                            .setMessage("下载更新包失败：$errorMessage\n\n$suggestion")
                            .setPositiveButton("重试") { _, _ ->
                                downloadUpdate()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        stopProgressMonitoring()
                        progressDialog?.dismiss()
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        val pauseReason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val textView = progressDialog?.findViewById<TextView>(android.R.id.text1)
                        textView?.text = "下载已暂停：${getPauseReasonMessage(pauseReason)}"
                    }
                    DownloadManager.STATUS_PENDING -> {
                        val textView = progressDialog?.findViewById<TextView>(android.R.id.text1)
                        textView?.text = "等待下载..."
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        // 正常运行状态，进度已在上面更新
                    }
                }
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    private fun getPauseReasonMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待WiFi连接"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络连接"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "等待重试"
            DownloadManager.PAUSED_UNKNOWN -> "未知原因"
            else -> "暂停 (原因代码: $reason)"
        }
    }
    
    private fun cancelDownload() {
        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
        stopProgressMonitoring()
        progressDialog?.dismiss()
        Toast.makeText(requireContext(), "下载已取消", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopProgressMonitoring() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
        // 重置超时相关变量
        downloadStartTime = 0
        lastProgressTime = 0
        lastDownloadedBytes = 0
    }
    
    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                Log.d("AboutFragment", "下载完成广播接收: downloadId=$downloadId, receivedId=$id")
                
                if (id == downloadId) {
                    Log.d("AboutFragment", "匹配的下载完成，开始后处理")
                    handleDownloadComplete()
                } else {
                    Log.d("AboutFragment", "接收到其他下载的完成通知，忽略")
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        Log.d("AboutFragment", "注册下载完成接收器")
        ContextCompat.registerReceiver(
            requireContext(),
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d("AboutFragment", "下载接收器注册完成")
    }
    
    private fun handleDownloadComplete() {
        try {
            // 停止进度监控
            stopProgressMonitoring()
            
            // 关闭进度对话框
            progressDialog?.dismiss()
            
            // 检查下载状态
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d("AboutFragment", "下载成功，开始后处理")
                        Toast.makeText(requireContext(), "下载完成，开始处理文件...", Toast.LENGTH_SHORT).show()
                        
                        // 启动后处理
                        lifecycleScope.launch {
                            performPostDownloadProcessing()
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Log.e("AboutFragment", "下载失败，原因: $reason")
                        val errorMessage = getDownloadErrorMessage(reason)
                        showPostProcessingError("下载失败", errorMessage)
                    }
                    else -> {
                        Log.w("AboutFragment", "下载状态异常: $status")
                        showPostProcessingError("下载异常", "下载状态异常，请重试")
                    }
                }
            } else {
                Log.e("AboutFragment", "无法查询下载状态")
                showPostProcessingError("下载异常", "无法查询下载状态，请重试")
            }
            
            cursor.close()
            
        } catch (e: Exception) {
            Log.e("AboutFragment", "处理下载完成事件时出错", e)
            showPostProcessingError("处理异常", "处理下载完成事件时出错: ${e.message}")
        }
    }
    
    private suspend fun performPostDownloadProcessing() = withContext(Dispatchers.IO) {
        try {
            Log.d("AboutFragment", "开始后处理流程")
            
            // 步骤1: 验证下载文件
            val downloadedFile = validateDownloadedFile()
            
            // 步骤2: 解压文件
            val extractDir = extractDownloadedFile(downloadedFile)
            
            // 步骤3: 安装更新
            installUpdate(extractDir)
            
            // 步骤4: 清理临时文件
            cleanupTempFiles(downloadedFile, extractDir)
            
            // 步骤5: 显示完成信息
            withContext(Dispatchers.Main) {
                showUpdateCompleteDialog()
            }
            
            Log.d("AboutFragment", "后处理流程完成")
            
        } catch (e: Exception) {
            Log.e("AboutFragment", "后处理流程失败", e)
            withContext(Dispatchers.Main) {
                showPostProcessingError("更新安装失败", "处理更新文件时出错: ${e.message}\n\n请检查设备是否已获取root权限。")
            }
        }
    }
    
    private suspend fun validateDownloadedFile(): File = withContext(Dispatchers.IO) {
        Log.d("AboutFragment", "验证下载文件")
        
        val downloadedFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "kctrl_update.zip")
        Log.d("AboutFragment", "检查下载文件: ${downloadedFile.absolutePath}")
        
        if (!downloadedFile.exists()) {
            throw Exception("下载文件不存在: ${downloadedFile.absolutePath}")
        }
        
        if (downloadedFile.length() == 0L) {
            throw Exception("下载文件为空")
        }
        
        Log.d("AboutFragment", "下载文件验证通过，大小: ${downloadedFile.length()} 字节")
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "文件验证通过，大小: ${formatFileSize(downloadedFile.length())}", Toast.LENGTH_SHORT).show()
        }
        
        return@withContext downloadedFile
    }
    
    private suspend fun extractDownloadedFile(downloadedFile: File): File = withContext(Dispatchers.IO) {
        Log.d("AboutFragment", "开始解压文件")
        
        val extractDir = File(requireContext().filesDir, "kctrl_extract")
        Log.d("AboutFragment", "解压目标目录: ${extractDir.absolutePath}")
        
        // 确保解压目录干净
        if (extractDir.exists()) {
            extractDir.deleteRecursively()
        }
        extractDir.mkdirs()
        
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "开始解压文件...", Toast.LENGTH_SHORT).show()
        }
        
        extractZipFile(downloadedFile, extractDir)
        
        // 验证解压结果
        val extractedFiles = extractDir.listFiles()
        if (extractedFiles == null || extractedFiles.isEmpty()) {
            throw Exception("解压失败，没有提取到任何文件")
        }
        
        Log.d("AboutFragment", "解压完成，提取了 ${extractedFiles.size} 个文件/目录")
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "解压完成，提取了 ${extractedFiles.size} 个文件", Toast.LENGTH_SHORT).show()
        }
        
        return@withContext extractDir
    }
    
    private suspend fun installUpdate(extractDir: File) = withContext(Dispatchers.IO) {
        Log.d("AboutFragment", "开始安装更新")
        
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "开始安装更新...", Toast.LENGTH_SHORT).show()
        }
        
        copyToModuleDirectory(extractDir)
        
        Log.d("AboutFragment", "更新安装完成")
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "更新安装完成", Toast.LENGTH_SHORT).show()
        }
    }
    

    private suspend fun cleanupTempFiles(downloadedFile: File, extractDir: File) = withContext(Dispatchers.IO) {
        try {
            Log.d("AboutFragment", "清理临时文件")

            // 删除下载文件
            if (downloadedFile.exists()) {
                downloadedFile.delete()
                Log.d("AboutFragment", "删除下载文件: ${downloadedFile.absolutePath}")
            }

            // 删除解压目录
            if (extractDir.exists()) {
                extractDir.deleteRecursively()
                Log.d("AboutFragment", "删除解压目录: ${extractDir.absolutePath}")
            } else {
                null
            }

        } catch (e: Exception) {
            Log.w("AboutFragment", "清理临时文件时出现异常", e)
        }
    }
    private fun showUpdateCompleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("更新完成")
            .setMessage("KCtrl Manager 更新已成功安装！\n\n更新内容已复制到模块目录，重启设备后生效。")
            .setPositiveButton("确定", null)
            .setNeutralButton("重启设备") { _, _ ->
                // 可以添加重启设备的逻辑
                Toast.makeText(requireContext(), "请手动重启设备以应用更新", Toast.LENGTH_LONG).show()
            }
            .show()
    }
    
    private fun showPostProcessingError(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("重试") { _, _ -> downloadUpdate() }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun formatFileSize(bytes: Long): String {
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024
        
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes.toDouble() / gb)
            bytes >= mb -> String.format("%.2f MB", bytes.toDouble() / mb)
            bytes >= kb -> String.format("%.2f KB", bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }
    
    private fun getDownloadErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "未找到存储设备"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
            DownloadManager.ERROR_FILE_ERROR -> "文件错误"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向次数过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "未处理的HTTP状态码"
            DownloadManager.ERROR_UNKNOWN -> "未知错误"
            else -> "下载失败，错误代码: $reason"
        }
    }
    
    private fun extractZipFile(zipFile: File, extractDir: File) {
        var extractedCount = 0
        var skippedCount = 0
        
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val entryName = entry.name
                
                // 跳过config.txt和scripts目录
                if (entryName == "config.txt" || entryName.startsWith("scripts/")) {
                    skippedCount++
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }
                
                val file = File(extractDir, entryName)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zipIn.copyTo(output)
                    }
                    extractedCount++
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        
        if (extractedCount == 0) {
            throw Exception("没有提取到任何文件 (跳过了 $skippedCount 个文件/目录)")
        }
    }
    
    private suspend fun copyToModuleDirectory(sourceDir: File) = withContext(Dispatchers.IO) {
        try {
            // 检查源目录是否存在文件
            val files = sourceDir.listFiles()
            if (files == null || files.isEmpty()) {
                throw Exception("解压目录为空或不存在")
            }
            
            // 创建目标目录
            val createDirProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p /data/adb/modules/kctrl"))
            createDirProcess.waitFor()
            
            // 复制文件
            val copyCommand = "cp -r ${sourceDir.absolutePath}/* /data/adb/modules/kctrl/"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", copyCommand))
            process.waitFor()
            
            val exitValue = process.exitValue()
            if (exitValue != 0) {
                // 读取错误输出
                val errorStream = process.errorStream
                val errorMessage = errorStream.bufferedReader().readText()
                throw Exception("Root命令执行失败 (退出码: $exitValue): $errorMessage")
            }
            
            // 验证文件是否复制成功
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -la /data/adb/modules/kctrl/"))
            verifyProcess.waitFor()
            val verifyOutput = verifyProcess.inputStream.bufferedReader().readText()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "目标目录内容: $verifyOutput", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            throw Exception("复制文件到模块目录失败: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停止进度监控
        stopProgressMonitoring()
        
        // 关闭进度对话框
        progressDialog?.dismiss()
        
        // 取消注册下载接收器
        downloadReceiver?.let {
            try {
                Log.d("AboutFragment", "注销下载接收器")
                requireContext().unregisterReceiver(it)
                Log.d("AboutFragment", "下载接收器注销完成")
            } catch (e: Exception) {
                Log.w("AboutFragment", "注销下载接收器时出现异常", e)
            }
        }
    }
}