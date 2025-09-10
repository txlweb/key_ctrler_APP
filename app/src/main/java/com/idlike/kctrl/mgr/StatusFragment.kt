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
import android.util.Base64 as AndroidBase64

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
    private lateinit var btnDiagnose: com.google.android.material.button.MaterialButton
    private lateinit var btnShowFloating: com.google.android.material.button.MaterialButton
    private lateinit var btnHideFloating: com.google.android.material.button.MaterialButton
    private lateinit var btnFloatingDemo: com.google.android.material.button.MaterialButton
    private lateinit var btnHotUpdate: com.google.android.material.button.MaterialButton
    private lateinit var btnHotInstall: com.google.android.material.button.MaterialButton
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
        btnDiagnose = view.findViewById(R.id.btn_diagnose)
        btnShowFloating = view.findViewById(R.id.btn_show_floating)
        btnHideFloating = view.findViewById(R.id.btn_hide_floating)
        btnFloatingDemo = view.findViewById(R.id.btn_floating_demo)
        btnHotUpdate = view.findViewById(R.id.btn_hot_update)
        btnHotInstall = view.findViewById(R.id.btn_hot_install)
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

        btnDiagnose.setOnClickListener {
            diagnoseModule()
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
        
        btnHotInstall.setOnClickListener {
            hotUpdateModuleFromAssets()
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

    private fun diagnoseModule() {
        val mainActivity = activity as? MainActivity ?: return

        if (!mainActivity.hasSuPermission()) {
            context?.let {
                android.widget.Toast.makeText(it, "无Root权限，无法执行诊断", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        context?.let {
            android.widget.Toast.makeText(it, "正在执行设备诊断...", android.widget.Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                // 使用base64编码的Shell脚本，避免转义问题
                val base64Script = "IyEvc3lzdGVtL2Jpbi9zaAoKIyBLQ1RSTCDorr7lpIfor4rmlq3ohJrmnKwKIyDnlKjkuo7or4rmlq3orr7lpIfnm5HlkKzpl67popjnmoTlt6XlhbcKCmVjaG8gIj09PSBLQ1RSTCDorr7lpIfor4rmlq3lt6XlhbcgPT09IgplY2hvICLmo4Dmn6Xorr7lpIfnirbmgIHlkozmnYPpmZAuLi4iCmVjaG8gIiIKCiMg5qOA5p+lcm9vdOadg+mZkAplY2hvICIxLiDmo4Dmn6VSb2905p2D6ZmQOiIKaWYgWyAiJChpZCAtdSkiIC1lcSAwIF07IHRoZW4KICAgIGVjaG8gIiAgIOKchSDlt7Lojrflj5ZSb2905p2D6ZmQIgplbHNlCiAgICBlY2hvICIgICDinYwg5pyq6I635Y+WUm9vdOadg+mZkO+8jOivt+S9v+eUqHN15oiWc3Vkb+i/kOihjCIKZmkKZWNobyAiIgoKIyDmo4Dmn6UvZGV2L2lucHV055uu5b2VCmVjaG8gIjIuIOajgOafpei+k+WFpeiuvuWkh+ebruW9lToiCmlmIFsgLWQgIi9kZXYvaW5wdXQiIF07IHRoZW4KICAgIGVjaG8gIiAgIOKchSAvZGV2L2lucHV055uu5b2V5a2Y5ZyoIgogICAgZWNobyAiICAg5Y+R546w5Lul5LiL6K6+5aSHOiIKICAgIGxzIC1sYSAvZGV2L2lucHV0L2V2ZW50KiAyPi9kZXYvbnVsbCB8IHdoaWxlIHJlYWQgbGluZTsgZG8KICAgICAgICBlY2hvICIgICAgICAkbGluZSIKICAgIGRvbmUKZWxzZQogICAgZWNobyAiICAg4p2MIC9kZXYvaW5wdXTnm67lvZXkuI3lrZjlnKgiCmZpCmVjaG8gIiIKCiMg5qOA5p+l6K6+5aSH5p2D6ZmQCmVjaG8gIjMuIOajgOafpeiuvuWkh+adg+mZkDoiCmZvciBkZXZpY2UgaW4gL2Rldi9pbnB1dC9ldmVudCo7IGRvCiAgICBpZiBbIC1lICIkZGV2aWNlIiBdOyB0aGVuCiAgICAgICAgaWYgWyAtciAiJGRldmljZSIgXTsgdGhlbgogICAgICAgICAgICBlY2hvICIgICDinIUgJGRldmljZSAtIOWPr+ivuyIKICAgICAgICBlbHNlCiAgICAgICAgICAgIGVjaG8gIiAgIOKdjCAkZGV2aWNlIC0g5LiN5Y+v6K+7IgogICAgICAgIGZpCiAgICBmaQpkb25lCmVjaG8gIiIKCiMg5qOA5p+l6K6+5aSH5L+h5oGvCmVjaG8gIjQuIOajgOafpeiuvuWkh+S/oeaBrzoiCmVjaG8gIiAgIOS9v+eUqGdldGV2ZW50IC1s5p+l55yL6K6+5aSHOiIKZ2V0ZXZlbnQgLWwgMj4vZGV2L251bGwgfCBoZWFkIC0yMAplY2hvICIiCgojIOajgOafpeiuvuWkh+acieaViOaApwplY2hvICI0YS4g6aqM6K+B6K6+5aSH5pyJ5pWI5oCnOiIKZm9yIGRldmljZSBpbiAvZGV2L2lucHV0L2V2ZW50KjsgZG8KICAgIGlmIFsgLWUgIiRkZXZpY2UiIF07IHRoZW4KICAgICAgICBpZiBbIC1jICIkZGV2aWNlIiBdOyB0aGVuCiAgICAgICAgICAgIGVjaG8gIiAgIOKchSAkZGV2aWNlIC0g5piv5a2X56ym6K6+5aSHIgogICAgICAgIGVsc2UKICAgICAgICAgICAgZWNobyAiICAg4p2MICRkZXZpY2UgLSDkuI3mmK/lrZfnrKborr7lpIciCiAgICAgICAgICAgIGNvbnRpbnVlCiAgICAgICAgZmkKICAgICAgICAKICAgICAgICAjIOS9v+eUqGdldGV2ZW506aqM6K+B6K6+5aSH5piv5ZCm5Y+v5Lul5q2j5bi46K+75Y+WCiAgICAgICAgaWYgdGltZW91dCAycyBnZXRldmVudCAiJGRldmljZSIgMj4vZGV2L251bGwgfCBoZWFkIC0xID4gL2Rldi9udWxsOyB0aGVuCiAgICAgICAgICAgIGVjaG8gIiAgIOKchSAkZGV2aWNlIC0g5Y+v5Lul5q2j5bi46K+75Y+W6L6T5YWl5LqL5Lu2IgogICAgICAgIGVsc2UKICAgICAgICAgICAgZWNobyAiICAg4p2MICRkZXZpY2UgLSDml6Dms5Xor7vlj5bovpPlhaXkuovku7YgKOWPr+iDveaYr+adg+mZkOaIluiuvuWkh+mXrumimCkiCiAgICAgICAgICAgIAogICAgICAgICAgICAjIOajgOafpeWFt+S9k+mUmeivrwogICAgICAgICAgICBpZiBbICEgLXIgIiRkZXZpY2UiIF07IHRoZW4KICAgICAgICAgICAgICAgIGVjaG8gIiAgICAgIOWOn+WboDog5p2D6ZmQ5LiN6Laz77yM5b2T5YmN55So5oi35peg5rOV6K+75Y+WIgogICAgICAgICAgICBlbGlmIFsgIiQoc3RhdCAtYyAnJXQnICIkZGV2aWNlIiAyPi9kZXYvbnVsbCkiICE9ICJlIiBdOyB0aGVuCiAgICAgICAgICAgICAgICBlY2hvICIgICAgICDljp/lm6A6IOS4jeaYr+acieaViOeahOi+k+WFpeiuvuWkhyIKICAgICAgICAgICAgZWxzZQogICAgICAgICAgICAgICAgZWNobyAiICAgICAg5Y6f5ZugOiDorr7lpIflj6/og73ooqvljaDnlKjmiJbml6DmlYgiCiAgICAgICAgICAgIGZpCiAgICAgICAgZmkKICAgIGZpCmRvbmUKZWNobyAiIgoKIyDmo4Dmn6UvcHJvYy9idXMvaW5wdXQvZGV2aWNlcwplY2hvICI1LiDmo4Dmn6UvcHJvYy9idXMvaW5wdXQvZGV2aWNlczoiCmlmIFsgLXIgIi9wcm9jL2J1cy9pbnB1dC9kZXZpY2VzIiBdOyB0aGVuCiAgICBlY2hvICIgICDinIUgL3Byb2MvYnVzL2lucHV0L2RldmljZXPlj6/or7siCiAgICBlY2hvICIgICDorr7lpIfliJfooag6IgogICAgY2F0IC9wcm9jL2J1cy9pbnB1dC9kZXZpY2VzIHwgZ3JlcCAtRSAiXk46fF5IOiIgfCBoZWFkIC0xMAplbHNlCiAgICBlY2hvICIgICDinYwgL3Byb2MvYnVzL2lucHV0L2RldmljZXPkuI3lj6/or7siCmZpCmVjaG8gIiIKCiMg5qOA5p+l6L+b56iL54q25oCBCmVjaG8gIjYuIOajgOafpUtDVFJM6L+b56iLOiIKaWYgcGdyZXAgLWYgImtjdHJsIiA+IC9kZXYvbnVsbDsgdGhlbgogICAgZWNobyAiICAg4pqg77iPICDlj5HnjrBrY3RybOi/m+eoi+ato+WcqOi/kOihjDoiCiAgICBwcyB8IGdyZXAga2N0cmwKICAgIGVjaG8gIiAgIOWmgumcgOWBnOatou+8jOivt+aJp+ihjDogcGtpbGwgLWYga2N0cmwiCmVsc2UKICAgIGVjaG8gIiAgIOKchSDmnKrlj5HnjrBrY3RybOi/m+eoi+i/kOihjCIKZmkKZWNobyAiIgoKIyDmo4Dmn6XmqKHlnZfnm67lvZUKZWNobyAiNy4g5qOA5p+l5qih5Z2X55uu5b2VOiIKaWYgWyAtZCAiL2RhdGEvYWRiL21vZHVsZXMva2N0cmwiIF07IHRoZW4KICAgIGVjaG8gIiAgIOKchSDmqKHlnZfnm67lvZXlrZjlnKg6IC9kYXRhL2FkYi9tb2R1bGVzL2tjdHJsIgogICAgZWNobyAiICAg6YWN572u5paH5Lu2OiIKICAgIGxzIC1sYSAvZGF0YS9hZGIvbW9kdWxlcy9rY3RybC9jb25maWcudHh0IDI+L2Rldi9udWxsIHx8IGVjaG8gIiAgIOKdjCDphY3nva7mlofku7bkuI3lrZjlnKgiCmVsc2UKICAgIGVjaG8gIiAgIOKdjCDmqKHlnZfnm67lvZXkuI3lrZjlnKgiCmZpCmVjaG8gIiIKCmVjaG8gIj09PSDor4rmlq3lrozmiJAgPT09IgplY2hvICIiCmVjaG8gIuW4uOingemXrumimOino+WGszoiCmVjaG8gIjEuIOWmguaenOiuvuWkh+S4jeWPr+ivuzog5qOA5p+l5paH5Lu25p2D6ZmQ5oiW5L2/55SoY2htb2Tkv67mlLkiCmVjaG8gIjIuIOWmguaenOiuvuWkh+S4jeWtmOWcqDog5qOA5p+l6K6+5aSH6Lev5b6E5piv5ZCm5q2j56GuIgplY2hvICIzLiDlpoLmnpzor7vlj5bplJnor686IOWwneivleS9v+eUqOS4jeWQjOeahGV2ZW506K6+5aSHIgplY2hvICI0LiDmn6XnnIvml6Xlv5c6IGxvZ2NhdCB8IGdyZXAgS0NUUkwi"
                val scriptContent = String(AndroidBase64.decode(base64Script, AndroidBase64.DEFAULT))

                val scriptFile = File(requireContext().cacheDir, "kctrl_diagnostic.sh")
                scriptFile.writeText(scriptContent)
                scriptFile.setExecutable(true)

                val command = "su -c 'sh ${scriptFile.absolutePath}'"
                val result = mainActivity.executeRootCommand(command)

                activity?.runOnUiThread {
                    if (result != null) {
                        showDiagnosticResult(result)
                    } else {
                        android.widget.Toast.makeText(context, "诊断执行失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(context, "诊断脚本执行出错: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showDiagnosticResult(result: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("设备诊断结果")
            .setMessage(result)
            .setPositiveButton("复制结果") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("诊断结果", result)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "结果已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .setCancelable(false)
            .create()
        
        dialog.show()
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
            
            // 先备份config.txt和scripts目录，然后清理目标目录
            val cleanCommands = listOf(
                // 备份config.txt文件
                "if [ -f ${moduleDir.absolutePath}/config.txt ]; then cp ${moduleDir.absolutePath}/config.txt ${moduleDir.absolutePath}/config.txt.bak; fi",
                // 备份scripts目录
                "if [ -d ${moduleDir.absolutePath}/scripts ]; then mv ${moduleDir.absolutePath}/scripts ${moduleDir.absolutePath}/scripts.bak; fi",
                // 清理目标目录中的所有文件和目录
                "rm -rf ${moduleDir.absolutePath}/*"
            )
            
            for (command in cleanCommands) {
                mainActivity.executeRootCommand("su -c '$command'")
            }
            
            // 复制文件（排除config.txt和scripts文件夹）
            val commands = listOf(
                // 使用cp -r命令保留目录结构
                "cp -r ${extractDir.absolutePath}/* ${moduleDir.absolutePath}/",
                // 如果存在config.txt，恢复原来的配置文件
                "if [ -f ${moduleDir.absolutePath}/config.txt.bak ]; then mv ${moduleDir.absolutePath}/config.txt.bak ${moduleDir.absolutePath}/config.txt; fi",
                // 如果存在scripts目录，恢复原来的scripts目录
                "if [ -d ${moduleDir.absolutePath}/scripts.bak ]; then rm -rf ${moduleDir.absolutePath}/scripts && mv ${moduleDir.absolutePath}/scripts.bak ${moduleDir.absolutePath}/scripts; fi",
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
    private fun hotUpdateModuleFromAssets() {
        val mainActivity = activity as? MainActivity ?: return
        
        if (!mainActivity.hasSuPermission()) {
            context?.let {
                android.widget.Toast.makeText(it, "无Root权限，无法热更新模块", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        context?.let {
            android.widget.Toast.makeText(it, "正在从内置资源安装模块...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        Thread {
            try {
                val tempDir = context?.getDir("temp_module", Context.MODE_PRIVATE) ?: return@Thread
                val zipFile = File(tempDir, "module.zip")
                val extractDir = File(tempDir, "extracted")
                
                // 清理临时目录
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                extractDir.mkdirs()
                
                // 从assets复制ZIP到临时目录
                context?.assets?.open("module.zip")?.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 解压ZIP文件
                extractZipFile(zipFile, extractDir)
                
                val success = extractAndCopyModule(Uri.fromFile(zipFile))
                activity?.runOnUiThread {
                    if (success) {
                        android.widget.Toast.makeText(context, "模块安装成功，正在重启服务...", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // 重启服务
                        mainActivity.restartKctrlService()
                        Handler(Looper.getMainLooper()).postDelayed({
                            updateStatus()
                        }, 3000)
                    } else {
                        android.widget.Toast.makeText(context, "模块安装失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(context, "安装出错: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
    
