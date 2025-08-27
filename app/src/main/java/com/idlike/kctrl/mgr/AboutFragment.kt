package com.idlike.kctrl.mgr

import android.annotation.SuppressLint
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
import androidx.activity.result.contract.ActivityResultContracts
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        
        // 检查是否显示测试崩溃日志按钮
        checkTestCrashLogsVisibility()
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

        // 开发者信息
        view.findViewById<MaterialCardView>(R.id.cardDeveloper)?.setOnClickListener {
            val intent = Intent(requireContext(), DeveloperInfoActivity::class.java)
            startActivity(intent)
        }

        // 捐赠支持
        view.findViewById<MaterialCardView>(R.id.cardDonate)?.setOnClickListener {
            showDonateDialog()
        }

        // 检查更新按钮
        view.findViewById<MaterialButton>(R.id.btnCheckUpdate)?.setOnClickListener {
            checkForUpdates()
        }

        // 崩溃日志按钮
        view.findViewById<MaterialButton>(R.id.btnViewCrashLogs)?.setOnClickListener {
            viewCrashLogs()
        }
        
        view.findViewById<MaterialButton>(R.id.btnExportCrashLogs)?.setOnClickListener {
            exportCrashLogs()
        }
        
        // 测试崩溃按钮
        view.findViewById<MaterialButton>(R.id.btnTestCrash)?.setOnClickListener {
            triggerTestCrash()
        }
        
        // 测试崩溃日志按钮
        view.findViewById<MaterialButton>(R.id.btnTestCrashLogs)?.setOnClickListener {
            viewTestCrashLogs()
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

    private fun showDonateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("感谢您的支持！")
            .setMessage("您的支持将帮助开发者持续改进和维护这个应用。\n\n您可以通过微信扫码进行捐赠，感谢您的慷慨支持！")
            .setPositiveButton("知道了") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show()

                val currentVersionCode = getCurrentVersionCode()
                val latestVersionCode = getLatestVersionCode()
                val updateLog = getUpdateLog()

                Toast.makeText(
                    requireContext(),
                    "当前版本: $currentVersionCode, 最新版本: $latestVersionCode",
                    Toast.LENGTH_LONG
                ).show()

                if (latestVersionCode > currentVersionCode) {
                    showUpdateDialog(updateLog)
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

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
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

    private fun showUpdateDialog(updateLog: String) {
        val message =
            if (updateLog.isNotEmpty() && updateLog != "暂无更新日志" && updateLog != "获取更新日志失败") {
                "检测到新版本，更新内容：\n\n$updateLog"
            } else {
                "检测到新版本，是否立即更新？"
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("发现新版本")
            .setMessage(message)
            .setPositiveButton("立即更新") { _, _ ->
                downloadUpdate()
            }
            .setNegativeButton("稍后更新", null)
            .setCancelable(false)
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
            val downloadManager =
                requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            if (downloadId == -1L) {
                throw Exception("无法创建下载任务")
            }

            Log.d("AboutFragment", "下载任务创建成功，ID: $downloadId")

            // 初始化下载状态
            initializeDownloadState()

            // 显示进度对话框
            showProgressDialog()

            // 注册下载完成监听器（确保自动处理）
            registerDownloadReceiver()

            // 开始监控下载进度
            startProgressMonitoring()

            Toast.makeText(requireContext(), "开始自动下载并安装更新", Toast.LENGTH_SHORT).show()

            // 添加调试信息
            Log.d(
                "AboutFragment",
                "当前下载ID: $downloadId, 接收器状态: ${if (downloadReceiver != null) "已注册" else "未注册"}"
            )

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
                "可用存储空间不足，请清理存储空间后重试。\n\n可用空间：${formatFileSize(availableSpace)}\n需要空间：${
                    formatFileSize(
                        requiredSpace
                    )
                }"
            )
            return false
        }

        return true
    }

    private fun cleanupOldFiles() {
        try {
            // 删除旧的下载文件
            val oldDownloadFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "kctrl_update.zip"
            )
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
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
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
        val progressBar =
            ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal)
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
        val downloadManager =
            requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor? = downloadManager.query(query)

        cursor?.use {
            if (it.moveToFirst()) {
                val bytesDownloaded =
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal =
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
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
                    val progressBar =
                        progressDialog?.findViewById<ProgressBar>(android.R.id.progress)
                    val textView = progressDialog?.findViewById<TextView>(android.R.id.text1)

                    progressBar?.progress = progress
                    textView?.text = "下载进度: $progress% (${formatFileSize(bytesDownloaded)}/${
                        formatFileSize(bytesTotal)
                    })"
                }

                when (status) {
                    DownloadManager.STATUS_FAILED -> {
                        val reason =
                            it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
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
                        Log.d("AboutFragment", "下载成功，开始处理")
                        stopProgressMonitoring()
                        progressDialog?.dismiss()
                        handleDownloadComplete()
                    }

                    DownloadManager.STATUS_PAUSED -> {
                        val pauseReason =
                            it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
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
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
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
        val downloadManager =
            requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
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
        downloadReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w("AboutFragment", e)
            }
        }

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("AboutFragment", "接收到广播: ${intent?.action}")
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                Log.d("AboutFragment", "下载完成广播接收: downloadId=$downloadId, receivedId=$id")

                if (id == downloadId) {
                    Log.d("AboutFragment", "匹配的下载完成，开始后处理")
                    // 确保停止进度监控和关闭对话框
                    stopProgressMonitoring()
                    progressDialog?.dismiss()
                    handleDownloadComplete()
                } else {
                    Log.d("AboutFragment", "接收到其他下载的完成通知，忽略")
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        Log.d("AboutFragment", "注册下载完成接收器")

        try {
            // Android 14以降ではContextCompatを使用
            ContextCompat.registerReceiver(
                requireContext(),
                downloadReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d("AboutFragment", "下载接收器注册完成")
        } catch (e: Exception) {
            Log.e("AboutFragment", "注册接收器失败", e)
            // 最終手段：直接登録を試みる
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requireContext().registerReceiver(
                        downloadReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    requireContext().registerReceiver(downloadReceiver, filter)
                }
                Log.d("AboutFragment", "下载接收器注册完成(直接登録)")
            } catch (e2: Exception) {
                Log.e("AboutFragment", "すべての登録方法が失敗", e2)
            }
        }
    }

    private fun handleDownloadComplete() {
        try {
            // 停止进度监控
            stopProgressMonitoring()

            // 关闭进度对话框
            progressDialog?.dismiss()

            // 检查下载状态
            val downloadManager =
                requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val status =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d("AboutFragment", "下载成功，开始后处理")
                        Toast.makeText(
                            requireContext(),
                            "下载完成，开始处理文件...",
                            Toast.LENGTH_SHORT
                        ).show()

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

            // 步骤5: 显示完成信息（自动处理，不依赖通知点击）
            withContext(Dispatchers.Main) {
                showUpdateCompleteDialog()
            }

            Log.d("AboutFragment", "后处理流程完成")

        } catch (e: Exception) {
            Log.e("AboutFragment", "后处理流程失败", e)
            withContext(Dispatchers.Main) {
                showPostProcessingError(
                    "更新安装失败",
                    "处理更新文件时出错: ${e.message}\n\n请检查设备是否已获取root权限。"
                )
            }
        }
    }

    private suspend fun validateDownloadedFile(): File = withContext(Dispatchers.IO) {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadedFile = File(downloadsDir, "kctrl_update.zip")

        Log.d("AboutFragment", "检查下载文件: ${downloadedFile.absolutePath}")

        if (!downloadedFile.exists()) {
            throw Exception("下载文件不存在")
        }

        if (downloadedFile.length() < 1024) {
            throw Exception("下载文件太小，可能已损坏")
        }

        Log.d("AboutFragment", "使用root shell复制文件到内部存储")
        val internalFile = File(requireContext().filesDir, "kctrl_update.zip")

        try {
            val command = "cp '${downloadedFile.absolutePath}' '${internalFile.absolutePath}'"
            Log.d("AboutFragment", "执行命令: $command")

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                // 如果root失败，尝试普通复制
                Log.w("AboutFragment", "root复制失败，尝试普通复制")
                downloadedFile.inputStream().use { input ->
                    internalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!internalFile.exists() || internalFile.length() == 0L) {
                throw Exception("文件复制失败")
            }

            Log.d("AboutFragment", "文件复制完成，内部路径: ${internalFile.absolutePath}")

            // 设置文件权限
            val chmodCommand = "chmod 644 '${internalFile.absolutePath}'"
            Log.d("AboutFragment", "执行命令: $chmodCommand")
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", chmodCommand))
            chmodProcess.waitFor()

            internalFile

        } catch (e: Exception) {
            Log.e("AboutFragment", "文件复制失败", e)
            throw Exception("无法复制文件: ${e.message}")
        }
    }

    private suspend fun extractDownloadedFile(downloadedFile: File): File =
        withContext(Dispatchers.IO) {
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
                Toast.makeText(
                    requireContext(),
                    "解压完成，提取了 ${extractedFiles.size} 个文件",
                    Toast.LENGTH_SHORT
                ).show()
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


    private suspend fun cleanupTempFiles(downloadedFile: File, extractDir: File) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("AboutFragment", "清理临时文件")

                // 删除内部存储的下载文件
                if (downloadedFile.exists() && downloadedFile.parentFile?.absolutePath == requireContext().filesDir.absolutePath) {
                    downloadedFile.delete()
                    Log.d("AboutFragment", "删除内部下载文件: ${downloadedFile.absolutePath}")
                }

                // 删除外部存储的下载文件
                val externalFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "kctrl_update.zip"
                )
                if (externalFile.exists()) {
                    externalFile.delete()
                    Log.d("AboutFragment", "删除外部下载文件: ${externalFile.absolutePath}")
                }

                // 删除解压目录
                if (extractDir.exists()) {
                    extractDir.deleteRecursively()
                    Log.d("AboutFragment", "删除解压目录: ${extractDir.absolutePath}")
                } else {

                }

            } catch (e: Exception) {
                Log.w("AboutFragment", "清理临时文件时出现异常", e)
            }
        }

    private fun showUpdateCompleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("更新完成")
            .setMessage("KCtrl Manager 更新已成功安装！\n\n更新内容已复制到模块目录，正在尝试安装manager.apk...")
            .setPositiveButton("确定") { _, _ ->
                // 尝试安装manager.apk
                installManagerApk()
            }
            .setCancelable(false)
            .show()
    }

    private fun installManagerApk() {
        lifecycleScope.launch {
            try {
                val mainActivity = activity as? MainActivity
                if (mainActivity == null) {
                    Toast.makeText(
                        requireContext(),
                        "无法获取MainActivity，安装失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                Log.d("AboutFragment", "使用root shell检查并安装manager.apk")

                // 使用root shell检查文件是否存在
                val checkResult =
                    mainActivity.executeRootCommand("ls /data/adb/modules/kctrl/manager.apk")
                if (checkResult == null || !checkResult.contains("manager.apk")) {
                    Log.d("AboutFragment", "未找到manager.apk")
                    Toast.makeText(requireContext(), "未找到manager.apk文件", Toast.LENGTH_SHORT)
                        .show()
                    return@launch
                }

                Log.d("AboutFragment", "找到manager.apk，开始安装")

                // 使用root shell直接安装APK
                val installResult =
                    mainActivity.executeRootCommand("pm install -r /data/adb/modules/kctrl/manager.apk")

                if (installResult != null) {
                    Log.d("AboutFragment", "安装结果: $installResult")
                    if (installResult.contains("Success") || installResult.contains("success")) {
                        Toast.makeText(requireContext(), "manager.apk安装成功！", Toast.LENGTH_LONG)
                            .show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "安装失败: $installResult",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "安装命令执行失败", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("AboutFragment", "安装manager.apk失败", e)
                Toast.makeText(requireContext(), "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPostProcessingError(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("重试") { _, _ -> downloadUpdate() }
            .setNegativeButton("取消", null)
            .show()
    }

    @SuppressLint("DefaultLocale")
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

    private suspend fun extractZipFile(zipFile: File, extractDir: File) =
        withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "AboutFragment",
                    "开始解压文件: ${zipFile.absolutePath} 到 ${extractDir.absolutePath}"
                )

                // 使用root权限创建目录
                val mkdirCommand = "mkdir -p '${extractDir.absolutePath}'"
                Log.d("AboutFragment", "执行命令: $mkdirCommand")
                var process = Runtime.getRuntime().exec(arrayOf("su", "-c", mkdirCommand))
                var exitCode = process.waitFor()

                if (exitCode != 0) {
                    // root失败时尝试普通创建
                    if (!extractDir.exists()) {
                        extractDir.mkdirs()
                    }
                }

                // 使用root权限解压
                val unzipCommand =
                    "unzip -o '${zipFile.absolutePath}' -d '${extractDir.absolutePath}'"
                Log.d("AboutFragment", "执行命令: $unzipCommand")
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", unzipCommand))
                exitCode = process.waitFor()

                if (exitCode != 0) {
                    // root失败时尝试普通解压
                    Log.w("AboutFragment", "root解压失败，尝试普通解压")
                    var extractedCount = 0
                    var skippedCount = 0
                    ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val entryName = entry!!.name

                            // 跳过config.txt和scripts目录
                            if (entryName == "config.txt" || entryName.startsWith("scripts/")) {
                                skippedCount++
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                                continue
                            }

                            val file = File(extractDir, entryName)
                            if (entry!!.isDirectory) {
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

                // 设置文件权限
                val chmodCommand = "chmod -R 755 '${extractDir.absolutePath}'"
                Log.d("AboutFragment", "执行命令: $chmodCommand")
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", chmodCommand))
                process.waitFor()

                Log.d("AboutFragment", "解压完成")

            } catch (e: Exception) {
                Log.e("AboutFragment", "解压失败", e)
                throw Exception("解压失败: ${e.message}")
            }
        }

    private suspend fun copyToModuleDirectory(sourceDir: File) = withContext(Dispatchers.IO) {
        try {
            val mainActivity = activity as? MainActivity ?: throw Exception("无法获取MainActivity")

            val moduleDir = File("/data/adb/modules/kctrl")

            // 创建目标目录
            val createDirResult =
                mainActivity.executeRootCommand("mkdir -p ${moduleDir.absolutePath}")
            if (createDirResult == null) {
                throw Exception("无法创建模块目录")
            }

            // 获取所有需要复制的文件
            val files = sourceDir.walk()
                .filter { it.isFile }
                .filter { !it.name.equals("config.txt", ignoreCase = true) }
                .filter { !it.path.contains("scripts") }
                .toList()

            if (files.isEmpty()) {
                throw Exception("源目录中没有需要复制的文件")
            }

            Log.d("AboutFragment", "找到 ${files.size} 个文件需要复制")

            // 清理目标目录中除config.txt和scripts外的文件
            val cleanCommands = listOf(
                "find ${moduleDir.absolutePath} -type f \\( -not -name \"config.txt\" -and -not -path \"*/scripts/*\" \\) -delete",
                "find ${moduleDir.absolutePath} -type d -empty -not -path \"*/scripts*\" -delete"
            )

            for (command in cleanCommands) {
                mainActivity.executeRootCommand(command)
            }

            // 逐个复制文件，保持目录结构
            for (file in files) {
                val relativePath = file.relativeTo(sourceDir).path
                val targetFile = File(moduleDir, relativePath)

                // 创建目标目录
                targetFile.parentFile?.let { parent ->
                    mainActivity.executeRootCommand("mkdir -p ${parent.absolutePath}")
                }

                // 复制文件
                val copyCommand = "cp '${file.absolutePath}' '${targetFile.absolutePath}'"
                val copyResult = mainActivity.executeRootCommand(copyCommand)
                if (copyResult == null) {
                    throw Exception("复制文件失败: ${file.name}")
                }

                Log.d("AboutFragment", "已复制: ${file.name} -> ${targetFile.absolutePath}")
            }

            // 设置权限
            mainActivity.executeRootCommand("chmod -R 755 ${moduleDir.absolutePath}")
            mainActivity.executeRootCommand("chmod 644 ${moduleDir.absolutePath}/module.prop")

            // 验证文件是否复制成功
            val verifyResult = mainActivity.executeRootCommand("ls -la ${moduleDir.absolutePath}/")
            Log.d("AboutFragment", "模块目录内容: $verifyResult")

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "模块文件复制完成", Toast.LENGTH_SHORT).show()

                // 延迟2秒后重启服务，确保文件复制完成
                Handler(Looper.getMainLooper()).postDelayed({
                    val success = mainActivity.restartKctrlService()
                    if (success) {
                        Toast.makeText(requireContext(), "服务重启成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "服务重启失败，请手动重启",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, 2000)
            }

        } catch (e: Exception) {
            Log.e("AboutFragment", "复制到模块目录失败", e)
            throw Exception("复制到模块目录失败: ${e.message}")
        }
    }


    private fun checkDownloadStatus() {
        try {
            if (downloadId == -1L) {
                Log.d("AboutFragment", "checkDownloadStatus: downloadId为-1，跳过检查")
                return
            }

            val downloadManager =
                requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val status =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                Log.d(
                    "AboutFragment",
                    "主动检查下载状态: downloadId=$downloadId, status=$status, progress=$bytesDownloaded/$bytesTotal"
                )

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d("AboutFragment", "检测到下载成功，开始处理")
                        stopProgressMonitoring()
                        progressDialog?.dismiss()
                        handleDownloadComplete()
                    }

                    DownloadManager.STATUS_FAILED -> {
                        Log.d("AboutFragment", "检测到下载失败")
                        stopProgressMonitoring()
                        progressDialog?.dismiss()
                        handleDownloadComplete()
                    }

                    else -> {
                        Log.d("AboutFragment", "下载仍在进行中...")
                    }
                }
            } else {
                Log.d("AboutFragment", "未找到下载任务: downloadId=$downloadId")
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("AboutFragment", "检查下载状态失败", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("AboutFragment", "onPause: 注销下载接收器")
        // 注销接收器
        downloadReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
                Log.d("AboutFragment", "下载接收器已注销")
                downloadReceiver = null
            } catch (e: Exception) {
                Log.e("AboutFragment", "注销接收器失败", e)
            }
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
                Log.d("AboutFragment", "onDestroy: 注销下载接收器")
                requireContext().unregisterReceiver(it)
                Log.d("AboutFragment", "下载接收器已注销")
                downloadReceiver = null
            } catch (e: Exception) {
                Log.e("AboutFragment", "注销接收器失败", e)
            }
        }
    }

    private fun viewCrashLogs() {
        try {
            val crashLogs =
                (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()

            if (crashLogs.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "没有找到崩溃日志", Toast.LENGTH_SHORT).show()
                return
            }

            val logFiles = crashLogs.map { file ->
                val date = Date(file.lastModified())
                val size = file.length() / 1024
                "${file.name} (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)}, ${size}KB)"
            }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("崩溃日志管理 (共${crashLogs.size}个)")
                .setItems(logFiles) { _, which ->
                    showCrashLogContent(crashLogs[which], crashLogs)
                }
                .setPositiveButton("全部导出") { dialog, _ ->
                    exportCrashLogs()
                    dialog.dismiss()
                }
                .setNegativeButton("全部删除") { _, _ ->
                    deleteAllCrashLogs()
                }
                .setNeutralButton("取消", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "查看崩溃日志失败: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showCrashLogContent(logFile: File, allLogs: List<File>) {
        try {
            val content = logFile.readText()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("崩溃日志: ${logFile.name}")
                .setMessage(content)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制到剪贴板") { _, _ ->
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Crash Log", content)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("删除此日志") { _, _ ->
                    deleteCrashLog(logFile)
                }
                .setCancelable(true)
                .show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "读取崩溃日志失败: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun exportCrashLogs() {
        try {
            val crashLogs =
                (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()

            if (crashLogs.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "没有找到崩溃日志", Toast.LENGTH_SHORT).show()
                return
            }

            val timestamp = System.currentTimeMillis()
            exportCrashLogLauncher.launch("kctrl_crash_logs_$timestamp.zip")

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导出崩溃日志失败: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val exportCrashLogLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { exportCrashLogsToUri(it) }
    }

    private fun exportCrashLogsToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val crashLogs =
                    (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()

                if (crashLogs.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "没有找到崩溃日志", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(outputStream).use { zipOut ->
                            crashLogs.forEach { logFile ->
                                if (logFile.exists()) {
                                    val zipEntry = ZipEntry(logFile.name)
                                    zipOut.putNextEntry(zipEntry)
                                    logFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(zipOut)
                                    }
                                    zipOut.closeEntry()
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "崩溃日志已导出", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "导出崩溃日志失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("AboutFragment", "onResume")

        // 检查是否需要自动触发更新检查
        val sharedPref = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val shouldAutoCheck = sharedPref.getBoolean("auto_check_update", false)

        if (shouldAutoCheck) {
            // 清除标志，避免重复触发
            sharedPref.edit().putBoolean("auto_check_update", false).apply()

            // 延迟执行，确保Fragment完全加载
            view?.postDelayed({
                checkForUpdates()
            }, 500)
        }

        // 重新注册下载接收器（如果下载进行中）
        if (downloadId != -1L) {
            Log.d("AboutFragment", "onResume: 重新注册下载接收器，downloadId=$downloadId")
            registerDownloadReceiver()

            // 主动检查下载状态，以防接收器未收到广播
            checkDownloadStatus()
        }

        // 更新崩溃日志状态
        updateCrashLogStatus()
    }

    private fun updateCrashLogStatus() {
        try {
            val crashLogs = (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()
            val count = crashLogs?.size ?: 0
            
            view?.findViewById<TextView>(R.id.tvCrashLogStatus)?.text = 
                if (count > 0) "发现 $count 个崩溃日志" else "暂无崩溃日志"
                
            // 更新测试崩溃日志按钮可见性
            checkTestCrashLogsVisibility()
                
            // 如果有新的崩溃日志，显示通知
            if (count > 0) {
                val lastCrashFile = crashLogs?.firstOrNull()
                lastCrashFile?.let { file ->
                    val lastModified = Date(file.lastModified())
                    val timeAgo = getTimeAgo(lastModified)
                    
                    // 检查是否是最近5分钟内的崩溃
                    val fiveMinutesAgo = Date(System.currentTimeMillis() - 5 * 60 * 1000)
                    if (lastModified.after(fiveMinutesAgo)) {
                        showNewCrashNotification(count, timeAgo)
                    }
                }
            }
                
        } catch (e: Exception) {
            view?.findViewById<TextView>(R.id.tvCrashLogStatus)?.text = "无法获取崩溃日志信息"
        }
    }
    
    private fun getTimeAgo(date: Date): String {
        val now = Date()
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
    
    private fun showNewCrashNotification(count: Int, timeAgo: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("检测到应用崩溃")
            .setMessage("发现 $count 个崩溃日志，最近一次发生在$timeAgo。\n\n是否立即查看崩溃详情？")
            .setPositiveButton("立即查看") { _, _ ->
                viewCrashLogs()
            }
            .setNegativeButton("稍后", null)
            .setCancelable(true)
            .show()
    }
    
    private fun deleteCrashLog(logFile: File) {
        try {
            if (logFile.exists() && logFile.delete()) {
                Toast.makeText(requireContext(), "已删除: ${logFile.name}", Toast.LENGTH_SHORT).show()
                updateCrashLogStatus()
            } else {
                Toast.makeText(requireContext(), "删除失败: ${logFile.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAllCrashLogs() {
        try {
            val crashLogs =
                (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()

            if (crashLogs.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "没有崩溃日志可删除", Toast.LENGTH_SHORT).show()
                return
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除所有 ${crashLogs.size} 个崩溃日志吗？此操作不可恢复。")
                .setPositiveButton("删除全部") { _, _ ->
                    var deletedCount = 0
                    crashLogs.forEach { logFile ->
                        if (logFile.exists() && logFile.delete()) {
                            deletedCount++
                        }
                    }
                    Toast.makeText(requireContext(), "已删除 $deletedCount 个崩溃日志", Toast.LENGTH_SHORT).show()
                    updateCrashLogStatus()
                }
                .setNegativeButton("取消", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkTestCrashLogsVisibility() {
        try {
            val crashLogs = (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()
            val hasTestCrash = crashLogs?.any { logFile ->
                logFile.name.contains("测试崩溃", ignoreCase = true) || 
                logFile.readText().contains("这是测试崩溃")
            } ?: false
            
//            view?.findViewById<MaterialButton>(R.id.btnTestCrashLogs)?.visibility =
//                if (hasTestCrash) View.VISIBLE else View.GONE
                
        } catch (e: Exception) {
            //view?.findViewById<MaterialButton>(R.id.btnTestCrashLogs)?.visibility = View.GONE
        }
    }

    private fun viewTestCrashLogs() {
        try {
            val crashLogs = (requireContext().applicationContext as? KCtrlApplication)?.getCrashLogs()
            val testCrashLogs = crashLogs?.filter { logFile ->
                logFile.name.contains("测试崩溃", ignoreCase = true) || 
                logFile.readText().contains("这是测试崩溃")
            }

            if (testCrashLogs.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "没有找到测试崩溃日志", Toast.LENGTH_SHORT).show()
                return
            }

            val logFiles = testCrashLogs.map { file ->
                val date = Date(file.lastModified())
                val size = file.length() / 1024
                "${file.name} (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)}, ${size}KB)"
            }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("测试崩溃日志 (共${testCrashLogs.size}个)")
                .setItems(logFiles) { _, which ->
                    showCrashLogContent(testCrashLogs[which], testCrashLogs)
                }
                .setNegativeButton("全部删除") { _, _ ->
                    deleteTestCrashLogs(testCrashLogs)
                }
                .setNeutralButton("取消", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "查看测试崩溃日志失败: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun deleteTestCrashLogs(testCrashLogs: List<File>) {
        try {
            var deletedCount = 0
            testCrashLogs.forEach { logFile ->
                if (logFile.exists() && logFile.delete()) {
                    deletedCount++
                }
            }
            Toast.makeText(requireContext(), "已删除 $deletedCount 个测试崩溃日志", Toast.LENGTH_SHORT).show()
            
            // 更新按钮可见性
            view?.findViewById<MaterialButton>(R.id.btnTestCrashLogs)?.visibility = View.GONE
            updateCrashLogStatus()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerTestCrash() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("测试崩溃")
            .setMessage("这将触发一个测试崩溃来验证崩溃日志捕获功能。应用将会崩溃并重新启动。是否继续？")
            .setPositiveButton("触发崩溃") { _, _ ->
                // 延迟执行崩溃，确保对话框关闭
                Handler(Looper.getMainLooper()).postDelayed({
                    throw RuntimeException("这是测试崩溃 - 用于验证崩溃日志捕获功能")
                }, 500)
            }
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show()
    }
}