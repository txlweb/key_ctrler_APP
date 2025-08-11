package com.idlike.kctrl.mgr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class ScriptEditActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvTemplates: RecyclerView
    private lateinit var rvScriptItems: RecyclerView
    private lateinit var btnRunScript: MaterialButton
    private lateinit var btnSaveScript: MaterialButton
    private lateinit var btnClear: MaterialButton

    private lateinit var btnAddItem: MaterialButton
    
    private lateinit var templateAdapter: TemplateAdapter
    private lateinit var scriptAdapter: ScriptItemAdapter
    private val scriptItems = mutableListOf<ScriptItem>()
    private val templateGroups = mutableListOf<TemplateGroup>()
    private val commandToLabelMap = mutableMapOf<String, String>()
    
    private var scriptFilePath: String = ""
    
    companion object {
        const val EXTRA_SCRIPT_PATH = "script_path"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_edit)
        
        scriptFilePath = intent.getStringExtra(EXTRA_SCRIPT_PATH) ?: ""
        
        initViews()
        setupRecyclerViews()
        loadTemplates()
        loadScript()
        setupClickListeners()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        rvTemplates = findViewById(R.id.rvTemplates)
        rvScriptItems = findViewById(R.id.rvScriptItems)
        btnRunScript = findViewById(R.id.btnRunScript)
        btnSaveScript = findViewById(R.id.btnSaveScript)
        btnClear = findViewById(R.id.btnClear)

        btnAddItem = findViewById(R.id.btnAddItem)
        
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun setupRecyclerViews() {
        // 设置模板列表
        templateAdapter = TemplateAdapter(templateGroups) { command ->
            addScriptItem(command)
        }
        rvTemplates.layoutManager = LinearLayoutManager(this)
        rvTemplates.adapter = templateAdapter
        
        // 设置脚本条目列表
        scriptAdapter = ScriptItemAdapter(scriptItems, { position ->
            removeScriptItem(position)
        }, commandToLabelMap) {
            // 内容变化时的回调
        }
        rvScriptItems.layoutManager = LinearLayoutManager(this)
        rvScriptItems.adapter = scriptAdapter
        
        // 设置拖动排序
        val itemTouchHelper = ItemTouchHelper(ScriptItemTouchCallback())
        itemTouchHelper.attachToRecyclerView(rvScriptItems)
    }
    
    private fun setupClickListeners() {
        btnRunScript.setOnClickListener {
            runScript()
        }
        
        btnSaveScript.setOnClickListener {
            saveScript()
        }
        
        btnClear.setOnClickListener {
            clearScript()
        }
        

        
        btnAddItem.setOnClickListener {
            addEmptyScriptItem()
        }
    }
    
    private fun loadTemplates() {
        try {
            val inputStream = assets.open("funcs.json")
            val jsonString = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            
            templateGroups.clear()
            commandToLabelMap.clear()
            var currentGroup: TemplateGroup? = null
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val label = item.getString("label")
                val command = item.getString("command")
                
                if (command.startsWith("# ") && command.contains("无特殊功能，可以直接删除本行")) {
                    // 这是一个分组标题（通过command中的特殊标记识别）
                    currentGroup = TemplateGroup(label, mutableListOf(), false) // 默认折叠
                    templateGroups.add(currentGroup)
                } else {
                    // 这是一个模板项
                    val templateItem = TemplateItem(label, command)
                    if (currentGroup != null) {
                        currentGroup.items.add(templateItem)
                    } else {
                        // 如果没有分组，创建默认分组
                        if (templateGroups.isEmpty()) {
                            templateGroups.add(TemplateGroup("默认", mutableListOf(), false))
                        }
                        templateGroups.last().items.add(templateItem)
                    }
                    
                    // 添加到命令映射表
                    commandToLabelMap[command] = label
                }
            }
            
            // 调试信息
            println("ScriptEditActivity: 加载了 ${templateGroups.size} 个模板组")
            templateGroups.forEach { group ->
                println("ScriptEditActivity: 组 '${group.title}' 包含 ${group.items.size} 个项目")
            }
            
            templateAdapter.refreshData()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载模板失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadScript() {
        if (scriptFilePath.isEmpty()) return
        
        try {
            // 使用root shell命令读取文件
            val command = "cat '$scriptFilePath' 2>/dev/null || echo '#!/bin/bash\n\n# 脚本内容请在此处添加\n'"
            Log.d("ScriptLoad", "读取脚本文件: $scriptFilePath")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val content = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            Log.d("ScriptLoad", "读取完成，退出码: $exitCode")
            
            if (exitCode == 0 && content.isNotEmpty()) {
                // 对读取的脚本进行if拆分处理
                val processedContent = splitIfStatements(content)
                parseScriptContent(processedContent)
                Log.d("ScriptLoad", "脚本加载成功")
            } else if (exitCode != 0) {
                Log.e("ScriptLoad", "读取脚本失败，退出码: $exitCode")
                Toast.makeText(this, "读取脚本文件失败 (权限或文件不存在)", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("ScriptLoad", "脚本文件为空，创建默认内容")
                parseScriptContent("#!/bin/bash\n\n# 脚本内容请在此处添加\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载脚本失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("ScriptLoad", "加载脚本异常: ${e.message}")
        }
    }
    
    private fun splitIfStatements(script: String): String {
        Log.d("ScriptSplit", "开始拆分if语句")
        
        val lines = script.split("\n")
        val processedLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // 检查是否为单行if-then语句，需要拆分
            if (trimmed.matches(Regex("^if\\s+.*\\s*;\\s*then\\s*$"))) {
                // 拆分if; then为两行
                val ifPart = trimmed.replace(Regex("\\s*;\\s*then\\s*$"), "")
                processedLines.add(ifPart)
                processedLines.add("then")
                Log.d("ScriptSplit", "拆分if语句: $trimmed -> [$ifPart, then]")
            } else if (trimmed.matches(Regex("^elif\\s+.*\\s*;\\s*then\\s*$"))) {
                // 拆分elif; then为两行
                val elifPart = trimmed.replace(Regex("\\s*;\\s*then\\s*$"), "")
                processedLines.add(elifPart)
                processedLines.add("then")
                Log.d("ScriptSplit", "拆分elif语句: $trimmed -> [$elifPart, then]")
            } else {
                // 保持原行不变
                processedLines.add(line)
            }
        }
        
        val result = processedLines.joinToString("\n")
        Log.d("ScriptSplit", "拆分完成，原始行数: ${lines.size}, 处理后行数: ${processedLines.size}")
        return result
    }
    
    private fun parseScriptContent(content: String) {
        scriptItems.clear()
        val lines = content.split("\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                // 检查是否为shebang行
                if (trimmed.startsWith("#!/")) {
                    scriptItems.add(ScriptItem(
                        content = trimmed,
                        templateLabel = "sh头",
                        isComment = true
                    ))
                }
                // 检查是否为注释行
                else if (trimmed.startsWith("#")) {
                    scriptItems.add(ScriptItem(
                        content = trimmed,
                        isComment = true
                    ))
                }
                // 普通命令行
                else {
                    // 尝试从模板中找到匹配的标签
                    val matchedLabel = commandToLabelMap[trimmed]
                    scriptItems.add(ScriptItem(
                        content = trimmed,
                        templateLabel = matchedLabel
                    ))
                }
            }
        }
        
        Log.d("ScriptParse", "解析完成，共 ${scriptItems.size} 个脚本项")
        scriptAdapter.notifyDataSetChanged()
    }
    
    private fun addScriptItem(command: String) {
        // 检查是否为手电筒相关命令
        if (command.contains("TorchToggleService") || command.contains("手电") || command.contains("keyevent 224")) {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasCameraPermission) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("权限不足")
                    .setMessage("手电筒功能需要摄像头权限，请在应用设置中授权后再试。")
                    .setPositiveButton("确定", null)
                    .show()
                return
            }
        }
        
        // 查找匹配的模板标签
        val matchedLabel = commandToLabelMap[command]
        val scriptItem = if (matchedLabel != null) {
            // 如果找到匹配的模板
            ScriptItem(
                content = command,
                templateLabel = matchedLabel
            )
        } else {
            // 如果没有找到匹配的模板，作为自定义命令
            ScriptItem(
                content = command
            )
        }
        
        scriptItems.add(scriptItem)
        scriptAdapter.notifyItemInserted(scriptItems.size - 1)
        rvScriptItems.scrollToPosition(scriptItems.size - 1)
    }
    
    private fun addEmptyScriptItem() {
        scriptItems.add(ScriptItem(
            content = ""
        ))
        scriptAdapter.notifyItemInserted(scriptItems.size - 1)
        rvScriptItems.scrollToPosition(scriptItems.size - 1)
    }
    
    private fun removeScriptItem(position: Int) {
        if (position >= 0 && position < scriptItems.size) {
            scriptItems.removeAt(position)
            scriptAdapter.notifyItemRemoved(position)
        }
    }
    
    private fun runScript() {
        val script = generateScript()
        if (script.isEmpty()) {
            Toast.makeText(this, "脚本为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查脚本是否包含手电筒功能
        if (script.contains("TorchToggleService") || script.contains("手电") || script.contains("keyevent 224")) {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasCameraPermission) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("权限不足")
                    .setMessage("手电筒功能需要摄像头权限，请在应用设置中授权后再试。")
                    .setPositiveButton("确定", null)
                    .show()
                return
            }
        }
        
        try {
            // 先保存脚本到临时文件，然后使用root权限执行
            if (scriptFilePath.isNotEmpty()) {
                // 如果有指定路径，先保存脚本
                val parentDir = scriptFilePath.substringBeforeLast("/")
                Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p '$parentDir'")).waitFor()
                
                val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val saveCommand = "echo '$encoded' | base64 -d > '$scriptFilePath' && chmod +x '$scriptFilePath'"
                Runtime.getRuntime().exec(arrayOf("su", "-c", saveCommand)).waitFor()
                
                // 执行脚本文件 - 使用更可靠的shell路径
                val shellCommand = "cd '${parentDir}' && /system/bin/sh '$scriptFilePath'"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", shellCommand))
                val result = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                
                val message = if (error.isNotEmpty()) {
                    "执行出错:\n$error"
                } else {
                    "执行成功:\n$result"
                }
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("脚本执行结果")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show()
            } else {
                // 如果没有指定路径，直接执行脚本内容 - 使用更可靠的shell路径
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/sh -c '$script'"))
                val result = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                
                val message = if (error.isNotEmpty()) {
                    "执行出错:\n$error"
                } else {
                    "执行成功:\n$result"
                }
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("脚本执行结果")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "执行失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveScript() {
        if (scriptFilePath.isEmpty()) {
            Toast.makeText(this, "未指定保存路径", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val script = generateScript()
            Log.d("ScriptSave", "开始保存脚本")
            
            // 使用root shell命令写入文件
            // 确保目录存在
            val parentDir = scriptFilePath.substringBeforeLast("/")
            Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p '$parentDir'")).waitFor()
            
            // 使用base64编码确保中文字符正确保存
            val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val command = "echo '$encoded' | base64 -d > '$scriptFilePath' && chmod +x '$scriptFilePath'"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
                Log.d("ScriptSave", "脚本保存成功")
                // 保存成功后重新加载脚本
                loadScript()
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
                Log.e("ScriptSave", "脚本保存失败，退出码: $exitCode")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("ScriptSave", "脚本保存异常: ${e.message}")
        }
    }
    

    
    private fun clearScript() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空脚本")
            .setMessage("确定要清空所有脚本内容吗？")
            .setPositiveButton("确定") { _, _ ->
                scriptItems.clear()
                scriptAdapter.notifyDataSetChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    

    


    


    
    private fun generateScript(): String {
        Log.d("ScriptGenerate", "开始生成脚本")
        val lines = mutableListOf<String>()
        
        // 检查第一行是否已经是shebang，如果不是则添加
        var hasShebang = false
        if (scriptItems.isNotEmpty()) {
            val firstItem = scriptItems[0].content.trim()
            if (firstItem.startsWith("#!/")) {
                hasShebang = true
            }
        }
        
        // 如果没有shebang，添加默认的
        if (!hasShebang) {
            lines.add("#!/bin/bash")
        }
        
        // 处理脚本项，合并if语句
        var i = 0
        while (i < scriptItems.size) {
            val currentItem = scriptItems[i]
            val currentContent = currentItem.content.trim()
            
            if (currentContent.isEmpty()) {
                i++
                continue
            }
            
            // 检查是否为if或elif语句开始（支持无空格情况）
            if (currentContent.startsWith("if") || currentContent.startsWith("elif")) {
                val conditionParts = mutableListOf<String>()
                conditionParts.add(currentContent)
                var j = i + 1
                
                // 收集所有条件行，直到遇到then或; then
                while (j < scriptItems.size) {
                    val nextContent = scriptItems[j].content.trim()
                    if (nextContent == "then" || nextContent == "; then") {
                        break
                    } else if (nextContent.isNotEmpty()) {
                        conditionParts.add(nextContent)
                    }
                    j++
                }
                
                // 如果找到了then或; then，合并所有条件和then为一行
                if (j < scriptItems.size && (scriptItems[j].content.trim() == "then" || scriptItems[j].content.trim() == "; then")) {
                    val mergedCondition = conditionParts.joinToString(" ")
                    // 确保if/elif后面有空格
                    val normalizedCondition = when {
                        mergedCondition.startsWith("if ") -> mergedCondition
                        mergedCondition.startsWith("if") -> mergedCondition.replaceFirst("if", "if ")
                        mergedCondition.startsWith("elif ") -> mergedCondition
                        mergedCondition.startsWith("elif") -> mergedCondition.replaceFirst("elif", "elif ")
                        else -> mergedCondition
                    }
                    val mergedLine = "$normalizedCondition; then"
                    lines.add(mergedLine)
                    Log.d("ScriptGenerate", "合并if语句: [${conditionParts.joinToString(", ")}, then] -> $mergedLine")
                    i = j + 1 // 跳过所有已处理的行
                } else {
                    // 没有找到then，按原样添加
                    lines.add(currentContent)
                    i++
                }
            } else {
                // 普通行，直接添加
                lines.add(currentContent)
                i++
            }
        }
        
        val result = lines.joinToString("\n")
        Log.d("ScriptGenerate", "脚本生成完成，共 ${lines.size} 行")
        return result
    }
    
    // 拖动排序回调
    private inner class ScriptItemTouchCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            
            if (fromPosition < scriptItems.size && toPosition < scriptItems.size) {
                val item = scriptItems.removeAt(fromPosition)
                scriptItems.add(toPosition, item)
                scriptAdapter.notifyItemMoved(fromPosition, toPosition)
            }
            
            return true
        }
        
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            removeScriptItem(position)
        }
    }
    
    // 数据类
    data class ScriptItem(
        var content: String,
        var isComment: Boolean = false,
        var templateLabel: String? = null
    )
    data class TemplateItem(val label: String, val command: String)
    data class TemplateGroup(val title: String, val items: MutableList<TemplateItem>, var isExpanded: Boolean)
}