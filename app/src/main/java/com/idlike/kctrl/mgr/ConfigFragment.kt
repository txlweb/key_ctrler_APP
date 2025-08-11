package com.idlike.kctrl.mgr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import org.json.JSONArray
class ConfigFragment : Fragment() {
    private lateinit var rvKeys: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var fabAddKey: ExtendedFloatingActionButton
    private lateinit var keyAdapter: KeyAdapter
    private val keyList = mutableListOf<KeyItem>()
    private var currentProgressDialog: androidx.appcompat.app.AlertDialog? = null
    private var currentConsoleOutputTextView: android.widget.TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupSwipeRefresh()
        setupClickListeners()
        checkServiceAndLoadKeys()
    }
    
    private fun checkServiceAndLoadKeys() {
        val mainActivity = activity as? MainActivity ?: return
        
        // 检查服务状态
        Thread {
            val status = mainActivity.checkKctrlStatus()
            
            activity?.runOnUiThread {
                 if (status != null && status.isNotEmpty()) {
                     // 服务运行中，启用功能并加载
                     enableFeatures()
                     loadKeys()
                 } else {
                     // 服务未运行，显示提示并禁用功能
                     showServiceNotRunningMessage()
                     disableFeatures()
                 }
             }
        }.start()
    }
    
    private fun showServiceNotRunningMessage() {
        context?.let {
            Toast.makeText(it, "KCtrl服务未运行，按键配置功能不可用", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun disableFeatures() {
        fabAddKey.isEnabled = false
        fabAddKey.alpha = 0.5f
        swipeRefreshLayout.isEnabled = false
    }
    
    private fun enableFeatures() {
        fabAddKey.isEnabled = true
        fabAddKey.alpha = 1.0f
        swipeRefreshLayout.isEnabled = true
    }
    


    private fun initViews(view: View) {
        rvKeys = view.findViewById<RecyclerView>(R.id.rv_keys)
        swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        fabAddKey = view.findViewById<ExtendedFloatingActionButton>(R.id.fab_add_key)
    }

    private fun setupRecyclerView() {
        keyAdapter = KeyAdapter(keyList) { key, action ->
            when (action) {
                "delete" -> {
                    // 显示确认对话框
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("删除按键")
                        .setMessage("确定要删除按键 ${key.name} (${key.code}) 吗？\n\n此操作将删除该按键的所有脚本文件，且无法撤销。")
                        .setPositiveButton("删除") { _, _ ->
                            // 删除脚本文件
                            deleteScriptFiles(key.code)
                            
                            keyList.remove(key)
                            keyAdapter.notifyDataSetChanged()
                            saveKeys()
                            context?.let {
                            Toast.makeText(it, "已删除按键: ${key.name}", Toast.LENGTH_SHORT).show()
                        }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                "edit" -> {
                    // 编辑按键配置
                    editKey(key)
                }
            }
        }
        
        rvKeys.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = keyAdapter
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshKeys()
        }
        // 设置下拉刷新的颜色
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun refreshKeys() {
        // 清空现有数据
        keyList.clear()
        keyAdapter.notifyDataSetChanged()
        
        // 重新加载按键配置
        loadKeys()
        
        // 停止刷新动画
        swipeRefreshLayout.isRefreshing = false
        
        // 显示刷新完成提示
        context?.let {
            Toast.makeText(it, "按键配置已刷新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        fabAddKey.setOnClickListener {
            addNewKey()
        }
    }

    private fun loadKeys() {
        val mainActivity = activity as? MainActivity
        
        keyList.clear()
        
        val configContent = mainActivity?.readConfigFile()
        if (configContent != null && configContent.isNotEmpty()) {
            parseKeysFromConfig(configContent)
        } else {
            // 添加默认按键配置
            addDefaultKeys()
        }
        
        keyAdapter.notifyDataSetChanged()
    }

    private fun parseKeysFromConfig(content: String) {
        val lines = content.split("\n")
        val keyMap = mutableMapOf<String, MutableMap<String, String>>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("#") || !trimmedLine.contains("=")) continue
            
            val parts = trimmedLine.split("=", limit = 2)
            if (parts.size != 2) continue
            
            val key = parts[0].trim()
            val value = parts[1].trim()
            
            // 解析按键配置，格式如: script_735_click, script_735_double_click 等
            if (key.startsWith("script_")) {
                val keyParts = key.split("_")
                if (keyParts.size >= 3) {
                    val keyCode = keyParts[1]
                    val actionType = keyParts.drop(2).joinToString("_")
                    
                    if (!keyMap.containsKey(keyCode)) {
                        keyMap[keyCode] = mutableMapOf()
                    }
                    keyMap[keyCode]!![actionType] = value
                }
            }
        }
        
        // 转换为 KeyItem 列表
        for ((keyCode, actions) in keyMap) {
            val keyItem = KeyItem(
                code = keyCode.toIntOrNull() ?: 0,
                name = "KEY_$keyCode",
                clickScript = actions["click"] ?: "",
                doubleClickScript = actions["double_click"] ?: "",
                shortPressScript = actions["short_press"] ?: "",
                longPressScript = actions["long_press"] ?: ""
            )
            keyList.add(keyItem)
        }
    }

    private fun addDefaultKeys() {
        // 添加默认的 KEY_735 配置（使用脚本名而非绝对路径）
        val defaultKey = KeyItem(
            code = 735,
            name = "KEY_735",
            clickScript = "click_735.sh",
            doubleClickScript = "double_click_735.sh",
            shortPressScript = "short_press_735.sh",
            longPressScript = "long_press_735.sh"
        )
        
        // 创建默认脚本文件
        createScriptFiles(735)
        
        keyList.add(defaultKey)
    }

    private fun addNewKey() {
        // 直接使用kfind工具识别
        addNewKeyWithKfind()
    }

    
    private fun getKeyNameFromCode(keyCode: Int): String {
        return when (keyCode) {
            1 -> "ESC"
            2 -> "1"
            3 -> "2"
            4 -> "3"
            5 -> "4"
            6 -> "5"
            7 -> "6"
            8 -> "7"
            9 -> "8"
            10 -> "9"
            11 -> "0"
            12 -> "-"
            13 -> "="
            14 -> "BACKSPACE"
            15 -> "TAB"
            16 -> "Q"
            17 -> "W"
            18 -> "E"
            19 -> "R"
            20 -> "T"
            21 -> "Y"
            22 -> "U"
            23 -> "I"
            24 -> "O"
            25 -> "P"
            26 -> "["
            27 -> "]"
            28 -> "ENTER"
            29 -> "LEFT_CTRL"
            30 -> "A"
            31 -> "S"
            32 -> "D"
            33 -> "F"
            34 -> "G"
            35 -> "H"
            36 -> "J"
            37 -> "K"
            38 -> "L"
            39 -> ";"
            40 -> "'"
            41 -> "`"
            42 -> "LEFT_SHIFT"
            43 -> "\\"
            44 -> "Z"
            45 -> "X"
            46 -> "C"
            47 -> "V"
            48 -> "B"
            49 -> "N"
            50 -> "M"
            51 -> ","
            52 -> "."
            53 -> "/"
            54 -> "RIGHT_SHIFT"
            55 -> "KP_ASTERISK"
            56 -> "LEFT_ALT"
            57 -> "SPACE"
            58 -> "CAPS_LOCK"
            59 -> "F1"
            60 -> "F2"
            61 -> "F3"
            62 -> "F4"
            63 -> "F5"
            64 -> "F6"
            65 -> "F7"
            66 -> "F8"
            67 -> "F9"
            68 -> "F10"
            69 -> "NUM_LOCK"
            70 -> "SCROLL_LOCK"
            71 -> "KP_7"
            72 -> "KP_8"
            73 -> "KP_9"
            74 -> "KP_MINUS"
            75 -> "KP_4"
            76 -> "KP_5"
            77 -> "KP_6"
            78 -> "KP_PLUS"
            79 -> "KP_1"
            80 -> "KP_2"
            81 -> "KP_3"
            82 -> "KP_0"
            83 -> "KP_DOT"
            87 -> "F11"
            88 -> "F12"
            96 -> "KP_ENTER"
            97 -> "RIGHT_CTRL"
            98 -> "KP_SLASH"
            99 -> "SYSRQ"
            100 -> "RIGHT_ALT"
            102 -> "HOME"
            103 -> "UP"
            104 -> "PAGE_UP"
            105 -> "LEFT"
            106 -> "RIGHT"
            107 -> "END"
            108 -> "DOWN"
            109 -> "PAGE_DOWN"
            110 -> "INSERT"
            111 -> "DELETE"
            113 -> "MUTE"
            114 -> "VOLUME_DOWN"
            115 -> "VOLUME_UP"
            116 -> "POWER"
            117 -> "KP_EQUAL"
            119 -> "PAUSE"
            125 -> "LEFT_META"
            126 -> "RIGHT_META"
            127 -> "COMPOSE"
            128 -> "STOP"
            129 -> "AGAIN"
            130 -> "PROPS"
            131 -> "UNDO"
            132 -> "FRONT"
            133 -> "COPY"
            134 -> "OPEN"
            135 -> "PASTE"
            136 -> "FIND"
            137 -> "CUT"
            138 -> "HELP"
            139 -> "MENU"
            140 -> "CALC"
            141 -> "SETUP"
            142 -> "SLEEP"
            143 -> "WAKEUP"
            144 -> "FILE"
            145 -> "SEND_FILE"
            146 -> "DELETE_FILE"
            147 -> "XFER"
            148 -> "PROG1"
            149 -> "PROG2"
            150 -> "WWW"
            151 -> "MSDOS"
            152 -> "COFFEE"
            153 -> "ROTATE_DISPLAY"
            154 -> "CYCLE_WINDOWS"
            155 -> "MAIL"
            156 -> "BOOKMARKS"
            157 -> "COMPUTER"
            158 -> "BACK"
            159 -> "FORWARD"
            160 -> "CLOSE_CD"
            161 -> "EJECT_CD"
            162 -> "EJECT_CLOSE_CD"
            163 -> "NEXT_SONG"
            164 -> "PLAY_PAUSE"
            165 -> "PREVIOUS_SONG"
            166 -> "STOP_CD"
            167 -> "RECORD"
            168 -> "REWIND"
            169 -> "PHONE"
            170 -> "ISO"
            171 -> "CONFIG"
            172 -> "HOMEPAGE"
            173 -> "REFRESH"
            174 -> "EXIT"
            175 -> "MOVE"
            176 -> "EDIT"
            177 -> "SCROLL_UP"
            178 -> "SCROLL_DOWN"
            179 -> "KP_LEFT_PAREN"
            180 -> "KP_RIGHT_PAREN"
            181 -> "NEW"
            182 -> "REDO"
            183 -> "F13"
            184 -> "F14"
            185 -> "F15"
            186 -> "F16"
            187 -> "F17"
            188 -> "F18"
            189 -> "F19"
            190 -> "F20"
            191 -> "F21"
            192 -> "F22"
            193 -> "F23"
            194 -> "F24"
            200 -> "PLAY_CD"
            201 -> "PAUSE_CD"
            202 -> "PROG3"
            203 -> "PROG4"
            204 -> "DASHBOARD"
            205 -> "SUSPEND"
            206 -> "CLOSE"
            207 -> "PLAY"
            208 -> "FAST_FORWARD"
            209 -> "BASS_BOOST"
            210 -> "PRINT"
            211 -> "HP"
            212 -> "CAMERA"
            213 -> "SOUND"
            214 -> "QUESTION"
            215 -> "EMAIL"
            216 -> "CHAT"
            217 -> "SEARCH"
            218 -> "CONNECT"
            219 -> "FINANCE"
            220 -> "SPORT"
            221 -> "SHOP"
            222 -> "ALT_ERASE"
            223 -> "CANCEL"
            224 -> "BRIGHTNESS_DOWN"
            225 -> "BRIGHTNESS_UP"
            226 -> "MEDIA"
            227 -> "SWITCH_VIDEO_MODE"
            228 -> "KBDILLUM_TOGGLE"
            229 -> "KBDILLUM_DOWN"
            230 -> "KBDILLUM_UP"
            231 -> "SEND"
            232 -> "REPLY"
            233 -> "FORWARD_MAIL"
            234 -> "SAVE"
            235 -> "DOCUMENTS"
            236 -> "BATTERY"
            237 -> "BLUETOOTH"
            238 -> "WLAN"
            239 -> "UWB"
            240 -> "UNKNOWN"
            241 -> "VIDEO_NEXT"
            242 -> "VIDEO_PREV"
            243 -> "BRIGHTNESS_CYCLE"
            244 -> "BRIGHTNESS_AUTO"
            245 -> "DISPLAY_OFF"
            246 -> "WWAN"
            247 -> "RFKILL"
            248 -> "MICMUTE"
            else -> "KEY_$keyCode"
        }
    }
    

    
    private fun addNewKeyWithKfind() {
         // 不清空现有按键列表，支持添加多个按键
         
         val mainActivity = activity as? MainActivity
         
         // 创建进度对话框
         val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
             .setView(R.layout.dialog_key_scan_progress)
             .setCancelable(false)
             .create()
         
         progressDialog.show()
         currentProgressDialog = progressDialog
         val statusTextView = progressDialog.findViewById<android.widget.TextView>(R.id.tvScanStatus)
         val consoleOutputTextView = progressDialog.findViewById<android.widget.TextView>(R.id.tvConsoleOutput)
         currentConsoleOutputTextView = consoleOutputTextView
         val btnCancelScan = progressDialog.findViewById<android.widget.Button>(R.id.btnCancelScan)
         
         // 设置关闭按钮点击事件
         btnCancelScan?.setOnClickListener {
             progressDialog.dismiss()
             currentProgressDialog = null
             currentConsoleOutputTextView = null
             Toast.makeText(requireContext(), "已关闭按键扫描", Toast.LENGTH_SHORT).show()
         }
         
         // 清空控制台输出
         consoleOutputTextView?.text = "等待脚本输出...\n"
         
         // 检查 kfind 工具是否存在并可执行
         statusTextView?.text = "正在检查扫描工具..."
         mainActivity?.executeRootCommandAsync("ls -la ${MainActivity.KCTRL_MODULE_PATH}/kfind") { checkResult ->
             android.util.Log.d("ConfigFragment", "kfind工具检查结果: $checkResult")
             
             if (checkResult == null || checkResult.contains("No such file")) {
                  requireActivity().runOnUiThread {
                      progressDialog.dismiss()
                      Toast.makeText(requireContext(), "kfind工具不存在: ${MainActivity.KCTRL_MODULE_PATH}/kfind", Toast.LENGTH_LONG).show()
                  }
                 android.util.Log.e("ConfigFragment", "kfind工具不存在: $checkResult")
                 return@executeRootCommandAsync
             }
             
             // 检查执行权限
             if (!checkResult.contains("-rwx") && !checkResult.contains("-r-x")) {
                  requireActivity().runOnUiThread {
                      progressDialog.dismiss()
                      Toast.makeText(requireContext(), "kfind工具没有执行权限", Toast.LENGTH_LONG).show()
                  }
                 android.util.Log.e("ConfigFragment", "kfind工具权限不足: $checkResult")
                 return@executeRootCommandAsync
             }
             
             android.util.Log.d("ConfigFragment", "kfind工具检查通过，开始扫描")
             requireActivity().runOnUiThread {
                   statusTextView?.text = "工具检查通过，请按下要添加的按键...\n(可多次添加不同按键)"
               }
             
             // 先结束所有kfind进程
             mainActivity.executeRootCommandAsync("pkill -f kfind") { killResult ->
                 requireActivity().runOnUiThread {
                      statusTextView?.text = "正在清理旧进程..."
                      currentConsoleOutputTextView?.append("结束所有kfind进程...\n")
                      if (killResult != null && killResult.isNotEmpty()) {
                          currentConsoleOutputTextView?.append("进程清理结果: $killResult\n")
                      }
                  }
                 
                 // 清空之前的结果文件
                 mainActivity.executeRootCommandAsync("rm -f ${MainActivity.KCTRL_MODULE_PATH}/kfind.txt") { _ ->
                     requireActivity().runOnUiThread {
                          statusTextView?.text = "正在启动按键扫描..."
                          currentConsoleOutputTextView?.append("清理旧的结果文件...\n")
                          currentConsoleOutputTextView?.append("清理完成，请按下您要添加的单个按键...\n")
                      }
                 
                 // 异步启动 kfind 扫描，使用绝对路径
                 mainActivity.executeRootCommandAsync("${MainActivity.KCTRL_MODULE_PATH}/kfind") { kfindExecResult ->
                     android.util.Log.d("ConfigFragment", "kfind命令执行完成，结果: $kfindExecResult")
                     
                     requireActivity().runOnUiThread {
                          statusTextView?.text = "扫描完成，正在读取结果..."
                          currentConsoleOutputTextView?.append("kfind扫描完成\n")
                          if (kfindExecResult != null && kfindExecResult.isNotEmpty()) {
                              currentConsoleOutputTextView?.append("执行输出: $kfindExecResult\n")
                          }
                      }
                     
                     // 等待2秒确保文件写入完成
                     android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                         // 读取 kfind 结果
                         mainActivity.executeRootCommandAsync("cat ${MainActivity.KCTRL_MODULE_PATH}/kfind.txt") { kfindResult ->
                             android.util.Log.d("ConfigFragment", "读取kfind.txt内容: '$kfindResult'")
                             
                             requireActivity().runOnUiThread {
                                  currentConsoleOutputTextView?.append("读取结果文件...\n")
                                  
                                  if (kfindResult != null && kfindResult.trim().isNotEmpty()) {
                                      currentConsoleOutputTextView?.append("检测到按键事件:\n$kfindResult\n")
                                      parseKfindResult(kfindResult)
                                  } else {
                                      // 检查文件是否存在
                                      mainActivity.executeRootCommandAsync("ls -la ${MainActivity.KCTRL_MODULE_PATH}/kfind.txt") { fileCheck ->
                                          android.util.Log.d("ConfigFragment", "kfind.txt文件检查: $fileCheck")
                                          requireActivity().runOnUiThread {
                                              if (fileCheck != null && fileCheck.contains("No such file")) {
                                                  currentConsoleOutputTextView?.append("错误: kfind未生成结果文件，请检查工具权限\n")
                                                  Toast.makeText(requireContext(), "kfind未生成结果文件，请检查工具权限", Toast.LENGTH_LONG).show()
                                              } else {
                                                  currentConsoleOutputTextView?.append("未检测到按键事件，请在扫描过程中按下按键\n")
                                                  Toast.makeText(requireContext(), "未检测到按键事件，请在扫描过程中按下按键", Toast.LENGTH_LONG).show()
                                              }
                                          }
                                      }
                                  }
                              }
                         }
                     }, 2000)
                 }
             }
         }
     }
     }

    private fun parseKfindResult(result: String) {
        // 显示完整的kfind结果用于调试
        android.util.Log.d("ConfigFragment", "kfind result: $result")
        
        // 使用保存的控制台输出TextView引用
        currentConsoleOutputTextView?.append("开始解析按键信息...\n")
        
        val lines = result.split("\n")
        val lastLine = lines.lastOrNull { it.trim().isNotEmpty() }
        
        if (lastLine != null) {
            android.util.Log.d("ConfigFragment", "Parsing last line: $lastLine")
            currentConsoleOutputTextView?.append("解析最后一行: $lastLine\n")
            
            // 解析格式: [735] KEY_735 - EV_KEY (1)
            val regex = "\\[(\\d+)\\]\\s+(\\w+)".toRegex()
            val match = regex.find(lastLine)
            
            if (match != null) {
                val keyCode = match.groupValues[1].toInt()
                val keyName = match.groupValues[2]
                
                android.util.Log.d("ConfigFragment", "Parsed key: $keyName ($keyCode)")
                currentConsoleOutputTextView?.append("解析成功: $keyName (代码: $keyCode)\n")
                
                // 检查是否已存在
                val existingKey = keyList.find { it.code == keyCode }
                if (existingKey != null) {
                    currentConsoleOutputTextView?.append("警告: 按键 $keyName 已存在\n")
                    context?.let {
                            Toast.makeText(it, "按键 $keyName 已存在", Toast.LENGTH_SHORT).show()
                        }
                    return
                }
                
                // 创建新的按键配置（使用脚本名而非绝对路径）
                val newKey = KeyItem(
                    code = keyCode,
                    name = keyName,
                    clickScript = "click_$keyCode.sh",
                    doubleClickScript = "double_click_$keyCode.sh",
                    shortPressScript = "short_press_$keyCode.sh",
                    longPressScript = "long_press_$keyCode.sh"
                )
                
                // 创建脚本文件
                createScriptFiles(keyCode)
                
                keyList.add(newKey)
                keyAdapter.notifyDataSetChanged()
                saveKeys()
                
                currentConsoleOutputTextView?.append("成功添加按键: $keyName ($keyCode)\n")
                currentConsoleOutputTextView?.append("可继续添加更多按键...\n")
                context?.let {
                        Toast.makeText(it, "已添加按键: $keyName ($keyCode)\n可继续添加更多按键", Toast.LENGTH_LONG).show()
                    }
            } else {
                currentConsoleOutputTextView?.append("错误: 无法解析按键信息\n格式: $lastLine\n")
                context?.let {
                     Toast.makeText(it, "无法解析按键信息，格式: $lastLine", Toast.LENGTH_LONG).show()
                 }
                android.util.Log.e("ConfigFragment", "Failed to parse: $lastLine")
            }
        } else {
             context?.let {
                 Toast.makeText(it, "kfind结果为空或无有效内容", Toast.LENGTH_SHORT).show()
             }
             android.util.Log.e("ConfigFragment", "No valid lines in kfind result")
         }
     }

    private fun createScriptFiles(keyCode: Int) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptDir = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        
        // 确保scripts目录存在
        mainActivity.executeRootCommandAsync("mkdir -p $scriptDir") { _ ->
            // 创建四个脚本文件，默认只有sh头
            val scriptTypes = listOf("click", "double_click", "short_press", "long_press")
            val scriptHeader = "#!/bin/bash\n\n# 脚本内容请在此处添加\n"
            
            scriptTypes.forEach { type ->
                val scriptPath = "$scriptDir/${type}_$keyCode.sh"
                // 使用base64编码确保中文字符正确保存
                val encoded = android.util.Base64.encodeToString(scriptHeader.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val createCommand = "echo '$encoded' | base64 -d > $scriptPath && chmod +x $scriptPath"
                
                mainActivity.executeRootCommandAsync(createCommand) { result ->
                    if (result != null) {
                        android.util.Log.d("ConfigFragment", "Created script: ${type}_$keyCode.sh")
                    }
                }
            }
        }
    }

    private fun deleteScriptFiles(keyCode: Int) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptDir = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        
        // 删除四个脚本文件
        val scriptTypes = listOf("click", "double_click", "short_press", "long_press")
        
        scriptTypes.forEach { type ->
            val scriptPath = "$scriptDir/${type}_$keyCode.sh"
            
            mainActivity.executeRootCommandAsync("rm -f $scriptPath") { result ->
                android.util.Log.d("ConfigFragment", "Deleted script: ${type}_$keyCode.sh")
            }
        }
    }
    
    private fun deleteScriptFile(keyCode: Int, eventType: String) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptDir = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        val scriptPath = "$scriptDir/${eventType}_$keyCode.sh"
        
        mainActivity.executeRootCommandAsync("rm -f $scriptPath") { result ->
            android.util.Log.d("ConfigFragment", "Deleted script: ${eventType}_$keyCode.sh")
        }
    }
    
    private fun createScriptFile(keyCode: Int, eventType: String) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptDir = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        
        // 确保scripts目录存在
        mainActivity.executeRootCommandAsync("mkdir -p $scriptDir") { _ ->
            val scriptPath = "$scriptDir/${eventType}_$keyCode.sh"
            val scriptHeader = "#!/bin/bash\n\n# 脚本内容请在此处添加\n"
            // 使用base64编码确保中文字符正确保存
            val encoded = android.util.Base64.encodeToString(scriptHeader.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val createCommand = "echo '$encoded' | base64 -d > $scriptPath && chmod +x $scriptPath"
            
            mainActivity.executeRootCommandAsync(createCommand) { result ->
                if (result != null) {
                    android.util.Log.d("ConfigFragment", "Created script: ${eventType}_$keyCode.sh")
                }
            }
        }
    }
    
    private fun syncScriptFiles(key: KeyItem) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptDir = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        
        // 确保scripts目录存在
        mainActivity.executeRootCommandAsync("mkdir -p $scriptDir") { _ ->
            // 处理每个脚本类型
            val scriptConfigs = mapOf(
                "click" to key.clickScript,
                "double_click" to key.doubleClickScript,
                "short_press" to key.shortPressScript,
                "long_press" to key.longPressScript
            )
            
            scriptConfigs.forEach { (type, scriptName) ->
                val scriptPath = "$scriptDir/${type}_${key.code}.sh"
                
                if (scriptName.isNotEmpty()) {
                    // 如果配置了脚本，确保文件存在
                    mainActivity.executeRootCommandAsync("test -f $scriptPath") { result ->
                        if (result == null) {
                            // 文件不存在，创建默认脚本文件
                            val scriptHeader = "#!/bin/bash\n\n# 脚本内容请在此处添加\n"
                            // 使用base64编码确保中文字符正确保存
                            val encoded = android.util.Base64.encodeToString(scriptHeader.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                            val createCommand = "echo '$encoded' | base64 -d > $scriptPath && chmod +x $scriptPath"
                            
                            mainActivity.executeRootCommandAsync(createCommand) { createResult ->
                                if (createResult != null) {
                                    android.util.Log.d("ConfigFragment", "Created script: ${type}_${key.code}.sh")
                                }
                            }
                        }
                    }
                } else {
                    // 如果没有配置脚本，删除对应文件
                    mainActivity.executeRootCommandAsync("rm -f $scriptPath") { result ->
                        android.util.Log.d("ConfigFragment", "Removed unused script: ${type}_${key.code}.sh")
                    }
                }
            }
        }
    }

    private fun editKey(key: KeyItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_key_edit, null)
        
        val tvKeyTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvKeyTitle)
        val layoutClickScript = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutClickScript)
        val layoutDoubleClickScript = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutDoubleClickScript)
        val layoutShortPressScript = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutShortPressScript)
        val layoutLongPressScript = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutLongPressScript)
        val etClickScript = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etClickScript)
        val etDoubleClickScript = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDoubleClickScript)
        val etShortPressScript = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etShortPressScript)
        val etLongPressScript = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLongPressScript)
        val btnEditClick = dialogView.findViewById<android.widget.ImageButton>(R.id.btnEditClick)
        val btnEditDoubleClick = dialogView.findViewById<android.widget.ImageButton>(R.id.btnEditDoubleClick)
        val btnEditShortPress = dialogView.findViewById<android.widget.ImageButton>(R.id.btnEditShortPress)
        val btnEditLongPress = dialogView.findViewById<android.widget.ImageButton>(R.id.btnEditLongPress)
        val btnDeleteClick = dialogView.findViewById<android.widget.ImageButton>(R.id.btnDeleteClick)
        val btnDeleteDoubleClick = dialogView.findViewById<android.widget.ImageButton>(R.id.btnDeleteDoubleClick)
        val btnDeleteShortPress = dialogView.findViewById<android.widget.ImageButton>(R.id.btnDeleteShortPress)
        val btnDeleteLongPress = dialogView.findViewById<android.widget.ImageButton>(R.id.btnDeleteLongPress)
        val btnAddEvent = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddEvent)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        
        // 设置当前值
        tvKeyTitle.text = "编辑按键: ${key.name} (${key.code})"
        etClickScript.setText(key.clickScript)
        etDoubleClickScript.setText(key.doubleClickScript)
        etShortPressScript.setText(key.shortPressScript)
        etLongPressScript.setText(key.longPressScript)
        
        // 初始化UI状态
        updateEventVisibility(layoutClickScript, key.clickScript.isNotEmpty())
        updateEventVisibility(layoutDoubleClickScript, key.doubleClickScript.isNotEmpty())
        updateEventVisibility(layoutShortPressScript, key.shortPressScript.isNotEmpty())
        updateEventVisibility(layoutLongPressScript, key.longPressScript.isNotEmpty())
        
        // 编辑脚本按钮点击事件
        btnEditClick.setOnClickListener {
            showScriptEditDialog(key, "click", etClickScript.text.toString()) { newScript ->
                etClickScript.setText(newScript)
                if (newScript.isNotEmpty()) {
                    updateEventVisibility(layoutClickScript, true)
                }
            }
        }
        
        btnEditDoubleClick.setOnClickListener {
            showScriptEditDialog(key, "double_click", etDoubleClickScript.text.toString()) { newScript ->
                etDoubleClickScript.setText(newScript)
                if (newScript.isNotEmpty()) {
                    updateEventVisibility(layoutDoubleClickScript, true)
                }
            }
        }
        
        btnEditShortPress.setOnClickListener {
            showScriptEditDialog(key, "short_press", etShortPressScript.text.toString()) { newScript ->
                etShortPressScript.setText(newScript)
                if (newScript.isNotEmpty()) {
                    updateEventVisibility(layoutShortPressScript, true)
                }
            }
        }
        
        btnEditLongPress.setOnClickListener {
            showScriptEditDialog(key, "long_press", etLongPressScript.text.toString()) { newScript ->
                etLongPressScript.setText(newScript)
                if (newScript.isNotEmpty()) {
                    updateEventVisibility(layoutLongPressScript, true)
                }
            }
        }
        
        // 删除事件按钮点击事件
        btnDeleteClick.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除事件")
                .setMessage("确定要删除 ${key.name} 的单击事件吗？\n\n此操作将删除对应的脚本文件，且无法撤销。")
                .setPositiveButton("删除") { _, _ ->
                    etClickScript.setText("")
                    updateEventVisibility(layoutClickScript, false)
                    // 同步删除脚本文件
                    deleteScriptFile(key.code, "click")
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        btnDeleteDoubleClick.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除事件")
                .setMessage("确定要删除 ${key.name} 的双击事件吗？\n\n此操作将删除对应的脚本文件，且无法撤销。")
                .setPositiveButton("删除") { _, _ ->
                    etDoubleClickScript.setText("")
                    updateEventVisibility(layoutDoubleClickScript, false)
                    // 同步删除脚本文件
                    deleteScriptFile(key.code, "double_click")
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        btnDeleteShortPress.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除事件")
                .setMessage("确定要删除 ${key.name} 的短按事件吗？\n\n此操作将删除对应的脚本文件，且无法撤销。")
                .setPositiveButton("删除") { _, _ ->
                    etShortPressScript.setText("")
                    updateEventVisibility(layoutShortPressScript, false)
                    // 同步删除脚本文件
                    deleteScriptFile(key.code, "short_press")
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        btnDeleteLongPress.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除事件")
                .setMessage("确定要删除 ${key.name} 的长按事件吗？\n\n此操作将删除对应的脚本文件，且无法撤销。")
                .setPositiveButton("删除") { _, _ ->
                    etLongPressScript.setText("")
                    updateEventVisibility(layoutLongPressScript, false)
                    // 同步删除脚本文件
                    deleteScriptFile(key.code, "long_press")
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        // 添加事件按钮点击事件
        btnAddEvent.setOnClickListener {
            showAddEventDialog(key, etClickScript, etDoubleClickScript, etShortPressScript, etLongPressScript,
                layoutClickScript, layoutDoubleClickScript, layoutShortPressScript, layoutLongPressScript)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSave.setOnClickListener {
            // 更新按键配置
            key.clickScript = etClickScript.text.toString().trim()
            key.doubleClickScript = etDoubleClickScript.text.toString().trim()
            key.shortPressScript = etShortPressScript.text.toString().trim()
            key.longPressScript = etLongPressScript.text.toString().trim()
            
            // 同步创建或更新脚本文件
            syncScriptFiles(key)
            
            // 保存配置
            saveKeys()
            keyAdapter.notifyDataSetChanged()
            
            context?.let {
                Toast.makeText(it, "按键配置已更新", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun loadTemplateGroups(): List<TemplateGroup> {
        return try {
            val inputStream = requireContext().assets.open("funcs.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            
            val groups = mutableMapOf<String, MutableList<TemplateItem>>()
            var currentGroup = "基础模板"
            
            // 确保基础模板分组存在
            groups[currentGroup] = mutableListOf()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val label = jsonObject.getString("label")
                val command = jsonObject.getString("command")
                
                // 检查是否是分组标题（命令为注释）
                if (command.startsWith("# ")) {
                    currentGroup = label
                    if (!groups.containsKey(currentGroup)) {
                        groups[currentGroup] = mutableListOf()
                    }
                } else {
                    // 添加到当前分组
                    if (!groups.containsKey(currentGroup)) {
                        groups[currentGroup] = mutableListOf()
                    }
                    groups[currentGroup]?.add(TemplateItem(label, command))
                }
            }
            
            // 转换为TemplateGroup列表，过滤空分组，并确保至少有一个分组
            val result = groups.filter { it.value.isNotEmpty() }
                .map { (title, items) -> TemplateGroup(title, items) }
                .sortedBy { it.title }
            
            // 如果没有加载到任何模板，返回默认模板
            if (result.isEmpty()) {
                listOf(
                    TemplateGroup("基础模板", listOf(
                        TemplateItem("输入文本", "input text \"Hello World\""),
                        TemplateItem("按键组合", "key ctrl+c"),
                        TemplateItem("鼠标点击", "click 100 200"),
                        TemplateItem("等待", "sleep 1")
                    ))
                )
            } else {
                result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ConfigFragment", "Failed to load templates", e)
            // 返回默认模板
            listOf(
                TemplateGroup("基础模板", listOf(
                    TemplateItem("输入文本", "input text \"Hello World\""),
                    TemplateItem("按键组合", "key ctrl+c"),
                    TemplateItem("鼠标点击", "click 100 200"),
                    TemplateItem("等待", "sleep 1")
                ))
            )
        }
     }
     
     // 模板适配器常量
    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_ITEM = 1
    }
    
    // 模板适配器
    private inner class TemplateAdapter(
        private val groups: List<TemplateGroup>,
        private val onItemClick: (String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
         
         private val displayItems = mutableListOf<TemplateAdapterItem>()
         
         init {
             updateDisplayItems()
         }
         
         private fun updateDisplayItems() {
             displayItems.clear()
             for (group in groups) {
                 displayItems.add(TemplateAdapterItem.GroupItem(group))
                 if (group.isExpanded) {
                     group.items.forEach { item ->
                         displayItems.add(TemplateAdapterItem.ItemItem(item))
                     }
                 }
             }
         }
         
         override fun getItemViewType(position: Int): Int {
             return when (displayItems[position]) {
                 is TemplateAdapterItem.GroupItem -> TYPE_GROUP
                 is TemplateAdapterItem.ItemItem -> TYPE_ITEM
             }
         }
         
         override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
             return when (viewType) {
                 TYPE_GROUP -> {
                     val view = LayoutInflater.from(parent.context).inflate(R.layout.item_template_group, parent, false)
                     GroupViewHolder(view)
                 }
                 TYPE_ITEM -> {
                     val view = LayoutInflater.from(parent.context).inflate(R.layout.item_template, parent, false)
                     ItemViewHolder(view)
                 }
                 else -> throw IllegalArgumentException("Unknown view type: $viewType")
             }
         }
         
         override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
             when (val item = displayItems[position]) {
                 is TemplateAdapterItem.GroupItem -> {
                     (holder as GroupViewHolder).bind(item.group)
                 }
                 is TemplateAdapterItem.ItemItem -> {
                     (holder as ItemViewHolder).bind(item.item)
                 }
             }
         }
         
         override fun getItemCount(): Int = displayItems.size
         
         private inner class GroupViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
             private val ivExpandIcon: ImageView = itemView.findViewById(R.id.ivExpandIcon)
             private val tvGroupTitle: TextView = itemView.findViewById(R.id.tvGroupTitle)
             private val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)
             
             fun bind(group: TemplateGroup) {
                 tvGroupTitle.text = group.title
                 tvItemCount.text = group.items.size.toString()
                 
                 // 设置展开/折叠图标
                 val rotation = if (group.isExpanded) 180f else 0f
                 ivExpandIcon.rotation = rotation
                 
                 itemView.setOnClickListener {
                     group.isExpanded = !group.isExpanded
                     updateDisplayItems()
                     notifyDataSetChanged()
                     
                     // 动画旋转图标
                     val targetRotation = if (group.isExpanded) 180f else 0f
                     ivExpandIcon.animate().rotation(targetRotation).setDuration(200).start()
                 }
             }
         }
         
         private inner class ItemViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
             private val tvTemplateLabel: TextView = itemView.findViewById(R.id.tvTemplateLabel)
             private val tvTemplateCommand: TextView = itemView.findViewById(R.id.tvTemplateCommand)
             
             fun bind(item: TemplateItem) {
                 tvTemplateLabel.text = item.label
                 tvTemplateCommand.text = item.command
                 
                 itemView.setOnClickListener {
                     // 检查是否是手电筒相关功能
                        if (item.command.contains("TorchToggleService") || item.label.contains("手电") || item.command.contains("keyevent 224")) {
                         val mainActivity = activity as? MainActivity
                         if (mainActivity?.hasCameraPermission() != true) {
                             context?.let {
                                 Toast.makeText(it, "手电筒功能需要摄像头权限，请在应用设置中授权", Toast.LENGTH_LONG).show()
                             }
                             return@setOnClickListener
                         }
                     }
                     onItemClick(item.command)
                 }
             }
         }
     }
     
     private fun updateEventVisibility(layout: android.widget.LinearLayout, isVisible: Boolean) {
        layout.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    private fun showAddEventDialog(
        key: KeyItem,
        etClickScript: com.google.android.material.textfield.TextInputEditText,
        etDoubleClickScript: com.google.android.material.textfield.TextInputEditText,
        etShortPressScript: com.google.android.material.textfield.TextInputEditText,
        etLongPressScript: com.google.android.material.textfield.TextInputEditText,
        layoutClickScript: android.widget.LinearLayout,
        layoutDoubleClickScript: android.widget.LinearLayout,
        layoutShortPressScript: android.widget.LinearLayout,
        layoutLongPressScript: android.widget.LinearLayout
    ) {
        val eventTypes = mutableListOf<String>()
        val eventLabels = mutableListOf<String>()
        
        // 检查哪些事件类型还没有配置
        if (etClickScript.text.toString().trim().isEmpty()) {
            eventTypes.add("click")
            eventLabels.add("单击")
        }
        if (etDoubleClickScript.text.toString().trim().isEmpty()) {
            eventTypes.add("double_click")
            eventLabels.add("双击")
        }
        if (etShortPressScript.text.toString().trim().isEmpty()) {
            eventTypes.add("short_press")
            eventLabels.add("短按")
        }
        if (etLongPressScript.text.toString().trim().isEmpty()) {
            eventTypes.add("long_press")
            eventLabels.add("长按")
        }
        
        if (eventTypes.isEmpty()) {
            context?.let {
                    Toast.makeText(it, "所有事件类型都已配置", Toast.LENGTH_SHORT).show()
                }
            return
        }
        
        // 显示选择对话框
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择要添加的事件类型")
            .setItems(eventLabels.toTypedArray()) { _, which ->
                val selectedType = eventTypes[which]
                val scriptName = "${selectedType}_${key.code}.sh"
                
                when (selectedType) {
                    "click" -> {
                        etClickScript.setText(scriptName)
                        updateEventVisibility(layoutClickScript, true)
                    }
                    "double_click" -> {
                        etDoubleClickScript.setText(scriptName)
                        updateEventVisibility(layoutDoubleClickScript, true)
                    }
                    "short_press" -> {
                        etShortPressScript.setText(scriptName)
                        updateEventVisibility(layoutShortPressScript, true)
                    }
                    "long_press" -> {
                        etLongPressScript.setText(scriptName)
                        updateEventVisibility(layoutLongPressScript, true)
                    }
                }
                
                // 同步创建脚本文件
                createScriptFile(key.code, selectedType)
                
                context?.let {
                        Toast.makeText(it, "已添加${eventLabels[which]}事件", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveKeys() {
        val mainActivity = activity as? MainActivity
        
        // 异步读取当前配置
        val currentConfig = mainActivity?.readConfigFile()
        if (currentConfig != null) {
            val configContent = currentConfig ?: ""
            
            // 移除旧的按键配置
            val lines = configContent.split("\n").toMutableList()
            lines.removeAll { line ->
                val trimmed = line.trim()
                trimmed.startsWith("script_") && trimmed.contains("=")
            }
            
            // 添加新的按键配置
            for (key in keyList) {
                if (key.clickScript.isNotEmpty()) {
                    lines.add("script_${key.code}_click=${key.clickScript}")
                }
                if (key.doubleClickScript.isNotEmpty()) {
                    lines.add("script_${key.code}_double_click=${key.doubleClickScript}")
                }
                if (key.shortPressScript.isNotEmpty()) {
                    lines.add("script_${key.code}_short_press=${key.shortPressScript}")
                }
                if (key.longPressScript.isNotEmpty()) {
                    lines.add("script_${key.code}_long_press=${key.longPressScript}")
                }
            }
            
            val newConfig = lines.joinToString("\n")
            
            // 使用优化的配置文件写入方法
            val success = mainActivity.writeConfigFile(newConfig)
            if (success) {
                context?.let {
                Toast.makeText(it, "配置保存成功", Toast.LENGTH_SHORT).show()
            }
            } else {
                context?.let {
                Toast.makeText(it, "配置保存失败", Toast.LENGTH_SHORT).show()
            }
            }
        }
    }
    
    private fun showScriptEditDialog(key: KeyItem, eventType: String, currentScript: String, onSave: (String) -> Unit) {
        // 启动ScriptEditActivity而不是显示弹窗
        val scriptPath = "${MainActivity.KCTRL_MODULE_PATH}/scripts/${eventType}_${key.code}.sh"
        val intent = android.content.Intent(requireContext(), ScriptEditActivity::class.java)
        intent.putExtra(ScriptEditActivity.EXTRA_SCRIPT_PATH, scriptPath)
        startActivity(intent)
    }
    
    private fun loadScriptContent(key: KeyItem, eventType: String, callback: (String) -> Unit) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptPath = "${MainActivity.KCTRL_MODULE_PATH}/scripts/${eventType}_${key.code}.sh"
        
        mainActivity.executeRootCommandAsync("cat $scriptPath 2>/dev/null || echo '#!/bin/bash\n\n# 脚本内容请在此处添加\n'") { result ->
            activity?.runOnUiThread {
                callback(result ?: "#!/bin/bash\n\n# 脚本内容请在此处添加\n")
            }
        }
    }
    
    private fun saveScriptContent(key: KeyItem, eventType: String, content: String, callback: (Boolean) -> Unit) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptDir = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        val scriptPath = "$scriptDir/${eventType}_${key.code}.sh"
        
        // 确保scripts目录存在
        mainActivity.executeRootCommandAsync("mkdir -p $scriptDir") { _ ->
            // 使用base64编码确保中文字符正确保存
            val encoded = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val saveCommand = "echo '$encoded' | base64 -d > $scriptPath && chmod +x $scriptPath"
            
            mainActivity.executeRootCommandAsync(saveCommand) { result ->
                activity?.runOnUiThread {
                    callback(result != null)
                }
            }
        }
    }
    
    private fun runScript(key: KeyItem, eventType: String, content: String) {
        val mainActivity = activity as? MainActivity ?: return
        val scriptPath = "${MainActivity.KCTRL_MODULE_PATH}/scripts/${eventType}_${key.code}.sh"
        
        // 检查脚本内容是否包含手电筒功能
        if (content.contains("TorchToggleService") || content.contains("手电") || content.contains("keyevent 224")) {
            if (mainActivity.hasCameraPermission() != true) {
                context?.let {
                    Toast.makeText(it, "脚本包含手电筒功能，需要摄像头权限才能运行", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        
        // 先保存脚本内容
        saveScriptContent(key, eventType, content) { success ->
            if (success) {
                // 运行脚本
                mainActivity.executeRootCommandAsync("cd ${MainActivity.KCTRL_MODULE_PATH}/scripts && bash ${eventType}_${key.code}.sh") { result ->
                    activity?.runOnUiThread {
                        if (result != null) {
                            showScriptOutput("脚本运行结果", result)
                        } else {
                            context?.let {
                        Toast.makeText(it, "脚本运行失败", Toast.LENGTH_SHORT).show()
                    }
                        }
                    }
                }
            } else {
                context?.let {
                        Toast.makeText(it, "保存脚本失败，无法运行", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    
    // 模板数据类
    data class TemplateItem(val label: String, val command: String)
    data class TemplateGroup(val title: String, val items: List<TemplateItem>, var isExpanded: Boolean = false)
    
    // 适配器项类型
    sealed class TemplateAdapterItem {
        data class GroupItem(val group: TemplateGroup) : TemplateAdapterItem()
        data class ItemItem(val item: TemplateItem) : TemplateAdapterItem()
    }
    
    private fun showTemplateDialog(onSelect: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_template_select, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewTemplates)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        
        // 创建对话框
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        // 加载模板数据
        val templateGroups = loadTemplateGroups()
        val adapter = TemplateAdapter(templateGroups) { command ->
            onSelect(command)
            dialog.dismiss()
        }
         
         recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun formatShellScript(content: String): String {
        // 简单的Shell脚本格式化
        val lines = content.split("\n")
        val formatted = StringBuilder()
        var indentLevel = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // 减少缩进
            if (trimmed.startsWith("fi") || trimmed.startsWith("done") || trimmed.startsWith("}")) {
                indentLevel = maxOf(0, indentLevel - 1)
            }
            
            // 添加缩进
            val indent = "    ".repeat(indentLevel)
            formatted.append(indent).append(trimmed).append("\n")
            
            // 增加缩进
            if (trimmed.startsWith("if ") || trimmed.startsWith("for ") || trimmed.startsWith("while ") || trimmed.endsWith("{")) {
                indentLevel++
            }
        }
        
        return formatted.toString().trimEnd()
    }
    
    private fun showScriptOutput(title: String, output: String) {
        val dialogView = LayoutInflater.from(context).inflate(android.R.layout.select_dialog_item, null)
        val textView = android.widget.TextView(context).apply {
            text = output
            setPadding(32, 32, 32, 32)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        
        val scrollView = android.widget.ScrollView(context).apply {
            addView(textView)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("确定", null)
            .show()
    }
}

data class KeyItem(
    val code: Int,
    val name: String,
    var clickScript: String,
    var doubleClickScript: String,
    var shortPressScript: String,
    var longPressScript: String
)