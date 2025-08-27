package com.idlike.kctrl.mgr

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.icu.text.Transliterator
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

class CommunityFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CommunityAdapter
    private lateinit var searchView: SearchView
    private lateinit var chipGroup: ChipGroup
    private lateinit var loadingView: View
    private lateinit var emptyView: View
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    
    private var originalItems: List<CommunityItem> = emptyList()
    private var filteredItems: List<CommunityItem> = emptyList()
    private var currentFilter: String = "全部"
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    data class CommunityItem(
        val id: String,
        val title: String,
        val description: String,
        val author: String,
        val category: String,
        val version: String,
        val downloadUrl: String,
        val iconUrl: String,
        val tags: List<String>,
        val size: String,
        val updatedAt: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_community, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        setupSwipeRefresh()
        loadData()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        searchView = view.findViewById(R.id.searchView)
        chipGroup = view.findViewById(R.id.chipGroup)
        loadingView = view.findViewById(R.id.loadingView)
        emptyView = view.findViewById(R.id.emptyView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        
        val uploadButton = view.findViewById<FloatingActionButton>(R.id.uploadButton)
        uploadButton.setOnClickListener {
            scanAndUploadScript()
        }
    }



    private fun setupRecyclerView() {
        adapter = CommunityAdapter(emptyList()) { item ->
            downloadItem(item)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterItems(newText ?: "", currentFilter)
                return true
            }
        })
    }

    private fun setupFilterChips() {
        val categories = listOf("全部", "模块", "主题", "脚本", "工具", "其他")
        
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = category == "全部"
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        currentFilter = category
                        filterItems(searchView.query.toString(), category)
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        
        // 设置刷新指示器颜色
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.dynamic_primary),
            ContextCompat.getColor(requireContext(), R.color.dynamic_secondary)
        )
    }

    private fun refreshData() {
        coroutineScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    fetchCommunityData()
                }
                originalItems = items
                filteredItems = items
                updateUI()
                showToast("数据已更新")
            } catch (e: Exception) {
                showToast("刷新失败: ${e.message}")
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun loadData() {
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        coroutineScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    fetchCommunityData()
                }
                originalItems = items
                filteredItems = items
                updateUI()
            } catch (e: Exception) {
                showError()
            } finally {
                loadingView.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private suspend fun fetchCommunityData(): List<CommunityItem> {
        val url = "http://idlike.134.w21.net/kctrl/config.php"
        val jsonString = URL(url).readText()
        val jsonArray = JSONArray(jsonString)
        
        return (0 until jsonArray.length()).map { i ->
            val json = jsonArray.getJSONObject(i)
            CommunityItem(
                id = json.getString("id"),
                title = json.getString("title"),
                description = json.getString("description"),
                author = json.getString("author"),
                category = json.getString("category"),
                version = json.getString("version"),
                downloadUrl = json.getString("download_url"),
                iconUrl = json.getString("icon_url"),
                tags = json.getJSONArray("tags").toStringList(),
                size = json.getString("size"),
                updatedAt = json.getString("updated_at")
            )
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).map { getString(it) }
    }

    private fun filterItems(searchText: String, category: String) {
        filteredItems = originalItems.filter { item ->
            val matchesSearch = searchText.isEmpty() || 
                item.title.contains(searchText, true) ||
                item.description.contains(searchText, true) ||
                item.author.contains(searchText, true) ||
                item.tags.any { it.contains(searchText, true) }
            
            val matchesCategory = category == "全部" || item.category == category
            
            matchesSearch && matchesCategory
        }
        updateUI()
    }

    private fun updateUI() {
        adapter.updateItems(filteredItems)
        
        if (filteredItems.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showError() {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun scanAndUploadScript() {
        coroutineScope.launch {
            try {
                val scripts = withContext(Dispatchers.IO) {
                    scanScriptsDirectory()
                }
                
                if (scripts.isEmpty()) {
                    showToast("未找到可上传的脚本文件")
                    return@launch
                }
                
                showScriptSelectionDialog(scripts)
            } catch (e: Exception) {
                showToast("扫描脚本失败: ${e.message}")
            }
        }
    }
    
    private fun scanScriptsDirectory(): List<File> {
        val scriptsDir = "/data/adb/modules/kctrl/scripts"
        val files = mutableListOf<File>()
        
        try {
            // 使用root shell列出scripts目录下的文件
            val result = (requireActivity() as MainActivity).executeRootCommand("ls -la $scriptsDir 2>/dev/null")
            if (!result.isNullOrEmpty()) {
                result.lines().forEach { line ->
                    if (line.startsWith("-") && line.endsWith(".sh")) {
                        val fileName = line.substringAfterLast(" ")
                        files.add(File(scriptsDir, fileName))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return files
    }
    
    private fun showScriptSelectionDialog(scripts: List<File>) {
        val scriptNames = scripts.map { it.name }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("选择要上传的脚本")
            .setItems(scriptNames) { _, which ->
                val selectedScript = scripts[which]
                showUploadDialog(selectedScript)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showUploadDialog(scriptFile: File) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_upload_script, null)
        
        val scriptNameInput = dialogView.findViewById<android.widget.EditText>(R.id.scriptNameInput)
        val authorInput = dialogView.findViewById<android.widget.EditText>(R.id.authorInput)
        val descriptionInput = dialogView.findViewById<android.widget.EditText>(R.id.descriptionInput)
        
        // 预设脚本名称
        scriptNameInput.setText(scriptFile.nameWithoutExtension)
        
        AlertDialog.Builder(requireContext())
            .setTitle("上传脚本")
            .setView(dialogView)
            .setPositiveButton("上传") { _, _ ->
                val name = scriptNameInput.text.toString()
                val author = authorInput.text.toString()
                val description = descriptionInput.text.toString()
                
                if (validateInputs(name, author)) {
                    uploadScript(scriptFile, name, author, description)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun validateInputs(name: String, author: String): Boolean {
        if (name.isEmpty()) {
            showToast("请输入脚本名称")
            return false
        }
        if (author.isEmpty()) {
            showToast("请输入作者名称")
            return false
        }
        if (author.contains("idlike", ignoreCase = true)) {
            showToast("作者名称不能包含 'idlike'")
            return false
        }
        return true
    }
    
    private fun uploadScript(scriptFile: File, name: String, author: String, description: String) {
        coroutineScope.launch {
            try {
                val scriptContent = withContext(Dispatchers.IO) {
                    (requireActivity() as MainActivity).executeRootCommand("cat ${scriptFile.absolutePath}")
                }
                
                if (scriptContent.isNullOrEmpty()) {
                    showToast("无法读取脚本内容")
                    return@launch
                }
                
                // 调用实际上传API
                val result = withContext(Dispatchers.IO) {
                    uploadToServer(name, author, description, scriptContent)
                }
                
                if (result.success) {
                    showToast("上传成功")
                    // 刷新数据
                    loadData()
                } else {
                    showToast("上传失败: ${result.message}")
                }
                
            } catch (e: Exception) {
                showToast("上传失败: ${e.message}")
            }
        }
    }
    
    private data class UploadResponse(
        val success: Boolean,
        val message: String,
        val data: CommunityItem? = null
    )
    
    private fun uploadToServer(name: String, author: String, description: String, content: String): UploadResponse {
        val url = "http://idlike.134.w21.net/kctrl/config.php"
        val connection = URL(url).openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            
            val params = mapOf(
                "name" to name,
                "author" to author,
                "description" to description,
                "content" to content,
                "category" to "脚本"
            )
            
            val postData = params.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            
            connection.outputStream.use { os ->
                os.write(postData.toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                return UploadResponse(
                    success = json.getBoolean("success"),
                    message = json.getString("message")
                )
            } else {
                return UploadResponse(false, "服务器错误: $responseCode")
            }
            
        } finally {
            connection.disconnect()
        }
    }
    

    
    private fun downloadItem(item: CommunityItem) {
        coroutineScope.launch {
            try {
                showToast("开始下载: ${item.title}")
                
                // 生成UUID作为文件名
                val uuid = java.util.UUID.randomUUID().toString()
                val fileName = "kctrl_${uuid}"
                
                // 使用系统下载管理器下载文件
                val downloadManager = requireContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(item.downloadUrl))
                    .setTitle(item.title)
                    .setDescription("正在下载 ${item.title}")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(false)

                val downloadId = downloadManager.enqueue(request)
                
                // 监听下载完成
                monitorDownloadProgress(downloadId, item)
                
            } catch (e: Exception) {
                showToast("下载失败: ${e.message}")
            }
        }
    }

    @SuppressLint("Range")
    private fun monitorDownloadProgress(downloadId: Long, item: CommunityItem) {
        coroutineScope.launch {
            try {
                val downloadManager = requireContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                
                var downloading = true
                while (downloading) {
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS))
                        when (status) {
                            android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                                downloading = false
                                val uriString = cursor.getString(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI))
                                val filePath = android.net.Uri.parse(uriString).path
                                
                                if (filePath != null) {
                                    processDownloadedFile(filePath, item)
                                } else {
                                    showToast("下载完成，但无法获取文件路径")
                                }
                            }
                            android.app.DownloadManager.STATUS_FAILED -> {
                                downloading = false
                                showToast("下载失败")
                            }
                            android.app.DownloadManager.STATUS_PAUSED, android.app.DownloadManager.STATUS_PENDING -> {
                                // 等待继续
                            }
                        }
                    }
                    cursor.close()
                    delay(1000) // 每秒检查一次
                }
            } catch (e: Exception) {
                showToast("处理下载文件时出错: ${e.message}")
            }
        }
    }

    private fun processDownloadedFile(filePath: String, item: CommunityItem) {
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when (item.category.lowercase()) {
                        "脚本" -> handleScriptFile(filePath, item)
                        "模块" -> handleModuleFile(filePath, item)
                        "主题" -> handleThemeFile(filePath, item)
                        else -> "不支持的文件类型"
                    }
                }
                
                showToast(result)
                
            } catch (e: Exception) {
                showToast("处理文件时出错: ${e.message}")
            }
        }
    }

    private fun handleScriptFile(filePath: String, item: CommunityItem): String {
        val targetDir = "/data/adb/modules/kctrl/scripts"
        val pinyinTitle = toPinyin(item.title)
        val safeTitle = pinyinTitle
        val targetPath = "$targetDir/${safeTitle}.sh"
        
        val mainActivity = requireActivity() as MainActivity
        
        android.util.Log.d("CommunityFragment", "开始安装脚本: ${item.title}")
        android.util.Log.d("CommunityFragment", "源文件: $filePath")
        android.util.Log.d("CommunityFragment", "目标路径: $targetPath")
        
        try {
            // 检查源文件是否存在
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                // 尝试在下载目录中查找
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "")
                val expectedFile = File(downloadDir, "kctrl_${item.title.replace(" ", "_")}")
                
                android.util.Log.d("CommunityFragment", "尝试查找文件: ${expectedFile.absolutePath}")
                
                if (!expectedFile.exists()) {
                    // 列出下载目录中的相关文件
                    val files = downloadDir.listFiles { file -> 
                        file.name.contains("kctrl", ignoreCase = true) || 
                        file.name.contains(item.title, ignoreCase = true)
                    }
                    
                    val fileList = files?.joinToString { it.name } ?: "无相关文件"
                    android.util.Log.d("CommunityFragment", "找到的文件: $fileList")
                    return "源文件不存在: ${item.title}"
                }
            }
            
            val actualSourcePath = if (File(filePath).exists()) filePath else 
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "kctrl_${item.title.replace(" ", "_")}").absolutePath
            
            // 转义特殊字符
            val escapedSourcePath = actualSourcePath.replace(" ", "\\ ").replace("'", "\\'")
            val escapedTargetPath = targetPath.replace(" ", "\\ ").replace("'", "\\'")
            
            android.util.Log.d("CommunityFragment", "实际源文件: $actualSourcePath")
            
            // 使用shell命令安装
            val installCommand = """
                mkdir -p '$targetDir' && 
                cp "$escapedSourcePath" "$escapedTargetPath" && 
                chmod 755 "$escapedTargetPath" && 
                echo 'INSTALL_SUCCESS'
            """.trimIndent()
            
            val result = mainActivity.executeRootCommand(installCommand)
            android.util.Log.d("CommunityFragment", "安装结果: $result")
            
            if (result?.contains("INSTALL_SUCCESS") == true) {
                // 验证文件是否成功安装
                val verifyCommand = "ls -la \"$escapedTargetPath\" 2>/dev/null"
                val verify = mainActivity.executeRootCommand(verifyCommand)
                android.util.Log.d("CommunityFragment", "最终验证: $verify")
                
                return "脚本已成功安装到模块目录: $safeTitle.sh"
            } else {
                // 获取详细错误信息
                val errorDetail = mainActivity.executeRootCommand("ls -la '$targetDir' 2>&1")
                android.util.Log.e("CommunityFragment", "安装失败详情: $result")
                return "安装失败: ${result ?: "未知错误"}"
            }
            
        } catch (e: Exception) {
            android.util.Log.e("CommunityFragment", "安装脚本异常", e)
            return "安装失败: ${e.message}"
        }
    }

    private fun toPinyin(input: String): String {
        // API < 24 时直接返回原文（或根据需要自定义一个小映射表）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return input

        // 说明：
        // - Han-Latin: 汉字转拉丁（带声调）
        // - NFD; [:Nonspacing Mark:] Remove; NFC: 分解并移除变音符（去声调），再规范化
        // - Lower: 转小写
        val rules = "Han-Latin/Names; NFD; [:Nonspacing Mark:] Remove; NFC; Lower"
        val transliterator = Transliterator.getInstance(rules)

        val latin = transliterator.transliterate(input)

        val sb = StringBuilder(latin.length)
        for (ch in latin) {
            when {
                ch.isLetterOrDigit() -> sb.append(ch)
                ch == '_' || ch == '-' -> sb.append(ch)                 // 保留这两类
                ch == ' ' || ch == '\'' || ch == '·' || ch == '.' ->
                    sb.append('-')                                      // 常见分隔符转 '-'
                else -> { /* 丢弃其它符号 */ }
            }
        }

        // 合并多余连字符，并去除首尾
        return sb.toString()
            .replace(Regex("-+"), "-")
            .trim('-')
    }
    private fun handleModuleFile(filePath: String, item: CommunityItem): String {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return "模块文件不存在"
            }
            
            val success = extractAndCopyModule(file)
            if (success) {
                // 重启服务
                (requireActivity() as MainActivity).restartKctrlService()
                "模块热更新成功，服务已重启"
            } else {
                "模块热更新失败"
            }
        } catch (e: Exception) {
            "模块热更新出错: ${e.message}"
        }
    }

    private fun extractAndCopyModule(zipFile: File): Boolean {
        val context = requireContext()
        
        try {
            val tempDir = context.getDir("temp_module", android.content.Context.MODE_PRIVATE)
            val extractDir = File(tempDir, "extracted")
            
            // 确保解压目录存在且为空
            if (extractDir.exists()) {
                extractDir.deleteRecursively()
            }
            extractDir.mkdirs()
            
            // 解压ZIP
            extractZipFile(zipFile, extractDir)
            
            // 检查解压后的内容
            val extractedFiles = extractDir.listFiles()
            if (extractedFiles.isNullOrEmpty()) {
                return false
            }
            
            // 复制到模块目录
            val moduleDir = "/data/adb/modules/kctrl"
            val mainActivity = requireActivity() as MainActivity
            
            // 创建目标目录
            val createDirResult = mainActivity.executeRootCommand("mkdir -p $moduleDir")
            if (createDirResult == null) {
                return false
            }
            
            // 备份现有配置和脚本
            val backupCommands = listOf(
                "cp $moduleDir/config.txt /data/local/tmp/config_backup.txt 2>/dev/null",
                "mkdir -p /data/local/tmp/scripts_backup",
                "cp -r $moduleDir/scripts/* /data/local/tmp/scripts_backup/ 2>/dev/null || true"
            )
            
            backupCommands.forEach { mainActivity.executeRootCommand(it) }
            
            // 清理整个模块目录（除了scripts目录结构）
            val cleanCommands = listOf(
                "find $moduleDir -mindepth 1 -maxdepth 1 -not -name \"scripts\" -exec rm -rf {} +",
                "rm -rf $moduleDir/scripts/* 2>/dev/null || true"
            )
            
            for (command in cleanCommands) {
                mainActivity.executeRootCommand(command)
            }
            
            // 使用cp -r递归复制所有文件和目录
            val copyResult = mainActivity.executeRootCommand("cp -r ${extractDir.absolutePath}/* $moduleDir/")
            if (copyResult == null) {
                return false
            }
            
            // 恢复配置和脚本
            val restoreCommands = listOf(
                "cp /data/local/tmp/config_backup.txt $moduleDir/config.txt 2>/dev/null",
                "cp -r /data/local/tmp/scripts_backup/* $moduleDir/scripts/ 2>/dev/null || true"
            )
            
            restoreCommands.forEach { mainActivity.executeRootCommand(it) }
            
            // 修复权限
            val fixPermissionCommands = listOf(
                "chmod 755 $moduleDir",
                "chmod 644 $moduleDir/module.prop",
                "chmod 644 $moduleDir/config.txt 2>/dev/null || true",
                "chmod 755 $moduleDir/* 2>/dev/null || true",
                "chmod -R 755 $moduleDir/scripts 2>/dev/null || true"
            )
            
            fixPermissionCommands.forEach { mainActivity.executeRootCommand(it) }
            
            // 清理临时文件和备份
            tempDir.deleteRecursively()
            val cleanupCommands = listOf(
                "rm -f /data/local/tmp/config_backup.txt",
                "rm -rf /data/local/tmp/scripts_backup"
            )
            cleanupCommands.forEach { mainActivity.executeRootCommand(it) }
            return true
            
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun extractZipFile(zipFile: File, destDir: File) {
        java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val filePath = File(destDir, entry.name.replace("\\", "/"))
                if (!entry.isDirectory) {
                    filePath.parentFile?.mkdirs()
                    java.io.FileOutputStream(filePath).use { output ->
                        zipIn.copyTo(output)
                    }
                    // 设置可执行权限（对于shell脚本等）
                    if (entry.name.endsWith(".sh") || entry.name.contains("/scripts/")) {
                        filePath.setExecutable(true, false)
                    }
                } else {
                    filePath.mkdirs()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    private fun handleThemeFile(filePath: String, item: CommunityItem): String {
        // 主题文件：使用pm安装APK
        val installResult = (requireActivity() as MainActivity).executeRootCommand("pm install -r '$filePath'")
        
        return if (installResult?.contains("Success") == true || installResult?.contains("success") == true) {
            "主题已成功安装"
        } else {
            "主题安装失败: ${installResult ?: "未知错误"}"
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    companion object {
        fun newInstance() = CommunityFragment()
    }
}