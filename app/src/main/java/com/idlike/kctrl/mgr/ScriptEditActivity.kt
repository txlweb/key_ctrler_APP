package com.idlike.kctrl.mgr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        
        // 启用动态颜色（莫奈取色）
        DynamicColors.applyToActivityIfAvailable(this)
        
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
        
        // 禁用默认动画器，避免动画冲突和空白问题
        rvTemplates.itemAnimator = null
        
        // 设置模板库的进入动画
        rvTemplates.alpha = 0f
        rvTemplates.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        // 设置默认展开第一个分组
        if (templateGroups.isNotEmpty()) {
            templateGroups[0].isExpanded = true
            templateAdapter.refreshData()
        }
        
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
        
        // 添加全局布局监听器，只在必要时滚动到焦点编辑项
        rvScriptItems.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rvScriptItems.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rvScriptItems.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出，查找当前有焦点的EditText
                val focusedView = currentFocus
                if (focusedView is com.google.android.material.textfield.TextInputEditText) {
                    // 查找包含该EditText的ViewHolder
                    var parent = focusedView.parent
                    while (parent != null && parent !is ViewGroup) {
                        parent = parent.parent
                    }
                    if (parent is ViewGroup) {
                        val holder = rvScriptItems.findContainingViewHolder(parent)
                        if (holder != null) {
                            val position = holder.adapterPosition
                            if (position != RecyclerView.NO_POSITION) {
                                val layoutManager = rvScriptItems.layoutManager as LinearLayoutManager
                                
                                // 检查当前焦点项是否已经在可见区域内
                                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                val lastVisible = layoutManager.findLastVisibleItemPosition()
                                
                                // 只有当焦点项不在可见范围内时才滚动
                                if (position < firstVisible || position > lastVisible) {
                                    val offset = rvScriptItems.height / 3
                                    layoutManager.scrollToPositionWithOffset(position, offset)
                                }
                            }
                        }
                    }
                }
            }
        }
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
        
        isLoadingScript = true
        try {
            // 使用root shell命令读取文件
            val command = "cat '$scriptFilePath' 2>/dev/null || echo '#!/bin/bash\n\n# 脚本内容请在此处添加\n'"
            Log.d("ScriptLoad", "读取脚本文件: $scriptFilePath")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val content = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            Log.d("ScriptLoad", "读取完成，退出码: $exitCode")
            
            if (exitCode == 0 && content.isNotEmpty()) {
                // 直接解析原始内容，不再拆分if语句
                parseScriptContent(content)
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
        } finally {
            isLoadingScript = false
        }
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
                    continue
                }
                // 检查是否为注释行
                else if (trimmed.startsWith("#")) {
                    scriptItems.add(ScriptItem(
                        content = trimmed,
                        isComment = true
                    ))
                    continue
                }
                if(trimmed.startsWith("if") || trimmed.startsWith("elif")){
                    val regex = Regex("""(?<=\b(?:if|elif))\s+|\s*(?=; ?then)""")
                    val prepared = trimmed.replace(regex, "§")

                    val lsif: List<String> = prepared.split("§")
                    for (it in lsif){
                        if(it.trim() == "") continue
                        var matchedLabel = commandToLabelMap[it.trim()]

                        if (matchedLabel == null) matchedLabel = "自定义命令"
                        if (it.startsWith("if")) matchedLabel = "如果("
                        if (it.startsWith("elif")) matchedLabel = "否则"

                        scriptItems.add(
                            ScriptItem(
                                content = it,
                                templateLabel = matchedLabel
                            )
                        )
                    }
                }else {
                    // 直接处理所有行，保持原始格式
                    var matchedLabel = commandToLabelMap[trimmed]
                    if (matchedLabel == null) {
                        matchedLabel = "自定义命令"
                    }


                    scriptItems.add(
                        ScriptItem(
                            content = trimmed,
                            templateLabel = matchedLabel.toString()
                        )
                    )
                }
            }
        }
        
        Log.d("ScriptParse", "解析完成，共 ${scriptItems.size} 个脚本项")
        scriptAdapter.notifyDataSetChanged()
    }
    
    private var isLoadingScript = false

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
        val newPosition = scriptItems.size - 1
        scriptAdapter.notifyItemInserted(newPosition)
        
        // 只在用户主动添加时才滚动到新项目
        if (!isLoadingScript) {
            rvScriptItems.postDelayed({
                val layoutManager = rvScriptItems.layoutManager as LinearLayoutManager
                val offset = rvScriptItems.height / 3
                layoutManager.scrollToPositionWithOffset(newPosition, offset)
                
                // 查找新添加的项目的EditText并请求焦点，选中全部文本便于编辑
                rvScriptItems.postDelayed({
                    val holder = rvScriptItems.findViewHolderForAdapterPosition(newPosition)
                    if (holder != null) {
                        val editText = holder.itemView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etScriptContent)
                        editText?.requestFocus()
                        editText?.setSelection(0, editText.text?.length ?: 0)
                    }
                }, 100)
            }, 100)
        }
    }
    
    private fun addEmptyScriptItem() {
        val newItem = ScriptItem(content = "")
        scriptItems.add(newItem)
        val newPosition = scriptItems.size - 1
        scriptAdapter.notifyItemInserted(newPosition)

        // 只在用户主动添加时才滚动到新项目
        if (!isLoadingScript) {
            rvScriptItems.postDelayed({
                val layoutManager = rvScriptItems.layoutManager as LinearLayoutManager
                val offset = rvScriptItems.height / 3
                layoutManager.scrollToPositionWithOffset(newPosition, offset)

                // 查找新添加的项目的EditText并请求焦点
                rvScriptItems.postDelayed({
                    val holder = rvScriptItems.findViewHolderForAdapterPosition(newPosition)
                    if (holder != null) {
                        val editText = holder.itemView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etScriptContent)
                        editText?.requestFocus()
                    }
                }, 100)
            }, 100)
        }
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
        
        // 显示执行中提示
        Toast.makeText(this, "脚本执行中...", Toast.LENGTH_SHORT).show()
        
        // 异步执行脚本
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = executeScriptAsync(script)
                withContext(Dispatchers.Main) {
                    showScriptResult(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScriptEditActivity, "执行失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun executeScriptAsync(script: String): String = withContext(Dispatchers.IO) {
        try {
            if (scriptFilePath.isNotEmpty()) {
                // 如果有指定路径，先保存脚本
                val parentDir = scriptFilePath.substringBeforeLast("/")
                
                // 确保目录存在
                val mkdirProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p '$parentDir'"))
                mkdirProcess.waitFor()
                
                // 保存脚本文件
                val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val saveCommand = "echo '$encoded' | base64 -d > '$scriptFilePath' && chmod +x '$scriptFilePath'"
                val saveProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", saveCommand))
                saveProcess.waitFor()
                
                // 执行脚本文件
                val shellCommand = "cd '${parentDir}' && /system/bin/sh '$scriptFilePath'"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", shellCommand))
                
                val result = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                
                if (error.isNotEmpty()) {
                    "执行出错:\n$error"
                } else {
                    "执行成功:\n$result"
                }
            } else {
                // 如果没有指定路径，直接执行脚本内容
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/sh -c '$script'"))
                
                val result = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                
                if (error.isNotEmpty()) {
                    "执行出错:\n$error"
                } else {
                    "执行成功:\n$result"
                }
            }
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
    
    private fun showScriptResult(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("脚本执行结果")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
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
        val scriptBuilder = StringBuilder()
        var isInIf = false
        // 直接构建脚本，保持原始格式
        for ((index, item) in scriptItems.withIndex()) {
            val content = item.content.trim()
            if (content.isNotEmpty()) {
                Log.i("sc_spawn",content)
                //判断if结构
                if (content.startsWith("if") || content.startsWith("elif")) {
                    if(!content.contains("then") && !content.contains(";then") && !content.contains("; then")){
                        isInIf = true
                    }
                }


                //处理换行
                if(isInIf) scriptBuilder.append(" ") else scriptBuilder.append("\n")

                scriptBuilder.append(content)

                if(content.startsWith("#!")){
                    scriptBuilder.append("\n")
                }
                if (content.contains("then") || content.contains(";then") || content.contains("; then")){
                    isInIf = false
                }
                if(!isInIf) scriptBuilder.append("\n")
            }
        }

        return "#!/bin/bash\n\n" + scriptBuilder.toString().replace("#!/bin/bash","")
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