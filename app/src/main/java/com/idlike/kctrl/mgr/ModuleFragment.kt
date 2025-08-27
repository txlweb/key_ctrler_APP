package com.idlike.kctrl.mgr

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Switch
import android.widget.Toast
import kotlin.math.max
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.CheckBox
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

class ModuleFragment : Fragment() {
    private lateinit var rvDevices: RecyclerView
    private lateinit var sliderClick: com.google.android.material.slider.Slider
    private lateinit var sliderShortPress: com.google.android.material.slider.Slider
    private lateinit var sliderLongPress: com.google.android.material.slider.Slider
    private lateinit var sliderDoubleClick: com.google.android.material.slider.Slider
    private lateinit var tvClickValue: TextView
    private lateinit var tvShortPressValue: TextView
    private lateinit var tvLongPressValue: TextView
    private lateinit var tvDoubleClickValue: TextView
    private lateinit var tvClickLabel: TextView
    private lateinit var tvShortPressLabel: TextView
    private lateinit var tvLongPressLabel: TextView
    private lateinit var segmentContainer: LinearLayout
    private lateinit var segmentClick: View
    private lateinit var segmentShortPress: View
    private lateinit var segmentLongPress: View
    private lateinit var segmentExtra: View
    private lateinit var labelClick: TextView
    private lateinit var labelShortPress: TextView
    private lateinit var labelLongPress: TextView
    private lateinit var labelExtra: TextView
    private lateinit var switchEnableLog: Switch
    private lateinit var llCpuCheckboxes: LinearLayout
    private lateinit var tvCpuCoresInfo: TextView
    private val cpuCheckBoxes = mutableListOf<CheckBox>()
    private lateinit var btnImportConfig: MaterialButton
    private lateinit var btnExportConfig: MaterialButton
    private lateinit var btnExportLogConfig: MaterialButton
    // 保存和加载按钮已移除，配置现在会自动保存并立即生效
    private lateinit var deviceAdapter: DeviceAdapter
    private val deviceList = mutableListOf<DeviceItem>()
    private var isLoadingConfig = false // 标志位，防止配置加载时触发自动保存
    
    // 文件选择器
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { exportConfigToUri(it) }
    }
    
    private val exportLogLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { exportLogConfigToUri(it) }
    }
    
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importConfigFromUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_module, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        
        // 预先扫描设备，不显示提示
        scanAllDevicesSilently()
        
        // 检查服务状态并加载配置
        view.post {
            checkServiceAndLoadConfig()
        }
    }

    override fun onResume() {
        super.onResume()
        // 当fragment重新可见时，重新检查服务状态
        checkServiceAndLoadConfig()
    }
    
    private fun checkServiceAndLoadConfig() {
        val mainActivity = activity as? MainActivity ?: return
        
        // 检查服务状态
        Thread {
            val status = mainActivity.checkKctrlStatus()
            
            activity?.runOnUiThread {
                 if (status != null && status.isNotEmpty()) {
                     // 服务运行中，启用功能并加载
                     enableFeatures()
                     loadConfig()
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
            Toast.makeText(it, "KCtrl服务未运行，系统配置功能不可用", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun disableFeatures() {
        rvDevices.alpha = 0.5f
        sliderClick.isEnabled = false
        sliderShortPress.isEnabled = false
        sliderLongPress.isEnabled = false
        sliderDoubleClick.isEnabled = false
        switchEnableLog.isEnabled = false
        btnImportConfig.isEnabled = false
        btnExportConfig.isEnabled = false
    }
    
    private fun enableFeatures() {
        rvDevices.alpha = 1.0f
        sliderClick.isEnabled = true
        sliderShortPress.isEnabled = true
        sliderLongPress.isEnabled = true
        sliderDoubleClick.isEnabled = true
        switchEnableLog.isEnabled = true
        btnImportConfig.isEnabled = true
        btnExportConfig.isEnabled = true
    }
    

    
    private fun getDeviceRealName(eventNumber: String): String? {
        val mainActivity = activity as? MainActivity
        val namePath = "/sys/class/input/event$eventNumber/device/name"
        
        // 尝试读取设备名称文件
        val result = mainActivity?.executeRootCommand("cat $namePath 2>/dev/null")
        return if (result.isNullOrBlank()) {
            // 如果读取失败，尝试从/proc/bus/input/devices获取
            getDeviceNameFromProc(eventNumber)
        } else {
            result.trim()
        }
    }
    
    private fun getDeviceNameFromProc(eventNumber: String): String? {
        val mainActivity = activity as? MainActivity
        
        // 从/proc/bus/input/devices读取设备信息
        val result = mainActivity?.executeRootCommand("cat /proc/bus/input/devices 2>/dev/null")
        if (result.isNullOrBlank()) return null
        
        val lines = result.split("\n")
        var currentDeviceName: String? = null
        var foundTargetDevice = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 查找设备名称行 (N: Name="...")
            if (trimmedLine.startsWith("N: Name=")) {
                currentDeviceName = trimmedLine.substringAfter("Name=\"")
                    .substringBefore("\"")
            }
            
            // 查找处理程序行 (H: Handlers=...)
            if (trimmedLine.startsWith("H: Handlers=") && 
                trimmedLine.contains("event$eventNumber")) {
                foundTargetDevice = true
                break
            }
            
            // 如果遇到空行，重置当前设备名称
            if (trimmedLine.isEmpty()) {
                currentDeviceName = null
            }
        }
        
        return if (foundTargetDevice) currentDeviceName else null
    }

    private fun initViews(view: View) {
        rvDevices = view.findViewById(R.id.rv_devices)
        sliderClick = view.findViewById(R.id.slider_click)
        sliderShortPress = view.findViewById(R.id.slider_short_press)
        sliderLongPress = view.findViewById(R.id.slider_long_press)
        sliderDoubleClick = view.findViewById(R.id.slider_double_click)
        tvClickValue = view.findViewById(R.id.tv_click_value)
        tvShortPressValue = view.findViewById(R.id.tv_short_press_value)
        tvLongPressValue = view.findViewById(R.id.tv_long_press_value)
        tvDoubleClickValue = view.findViewById(R.id.tv_double_click_value)
        tvClickLabel = view.findViewById(R.id.tv_click_label)
        tvShortPressLabel = view.findViewById(R.id.tv_short_press_label)
        tvLongPressLabel = view.findViewById(R.id.tv_long_press_label)
        segmentContainer = view.findViewById(R.id.segment_container)
        segmentClick = view.findViewById(R.id.segment_click)
        segmentShortPress = view.findViewById(R.id.segment_short_press)
        segmentLongPress = view.findViewById(R.id.segment_long_press)
        segmentExtra = view.findViewById(R.id.segment_extra)
        labelClick = view.findViewById(R.id.label_click)
        labelShortPress = view.findViewById(R.id.label_short_press)
        labelLongPress = view.findViewById(R.id.label_long_press)
        labelExtra = view.findViewById(R.id.label_extra)
        switchEnableLog = view.findViewById(R.id.switch_enable_log)
        llCpuCheckboxes = view.findViewById(R.id.ll_cpu_checkboxes)
        tvCpuCoresInfo = view.findViewById(R.id.tv_cpu_cores_info)
        btnImportConfig = view.findViewById(R.id.btn_import_config)
        btnExportConfig = view.findViewById(R.id.btn_export_config)
        btnExportLogConfig = view.findViewById(R.id.btn_export_log_config)
        
        // 初始化滑块值显示
        updateSliderValueTexts()
        
        // 获取CPU核心数量并创建CheckBox
        getCpuCoresCount()
    }

    private fun getCpuCoresCount() {
        try {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            tvCpuCoresInfo.text = "检测到的CPU核心数: $cpuCores"
            
            // 动态创建CheckBox
            createCpuCheckBoxes(cpuCores)
        } catch (e: Exception) {
            tvCpuCoresInfo.text = "无法检测CPU核心数"
            android.util.Log.e("ModuleFragment", "获取CPU核心数失败", e)
            // 如果检测失败，默认创建4个CheckBox
            createCpuCheckBoxes(4)
        }
    }
    
    private fun createCpuCheckBoxes(cpuCores: Int) {
        // 清除现有的CheckBox
        llCpuCheckboxes.removeAllViews()
        cpuCheckBoxes.clear()
        
        // 创建CheckBox，每行显示4个
        var currentRow: LinearLayout? = null
        for (i in 0 until cpuCores) {
            if (i % 4 == 0) {
                // 创建新行
                currentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
                }
                llCpuCheckboxes.addView(currentRow)
            }
            
            val checkBox = CheckBox(requireContext()).apply {
                text = "CPU$i"
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                // 默认选中CPU0
                isChecked = (i == 0)
                
                // 添加监听器，实现自动保存并提示重启服务
                setOnCheckedChangeListener { _, _ ->
                    if (!isLoadingConfig) {
                        // 延迟保存，避免频繁保存
                        view?.removeCallbacks(autoSaveRunnable)
                        view?.postDelayed(autoSaveRunnable, 500)
                        
                        // 提示用户需要重启服务
                        Toast.makeText(requireContext(), "CPU亲和性配置已修改，需要重启服务才能生效", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            cpuCheckBoxes.add(checkBox)
            currentRow?.addView(checkBox)
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(
            deviceList,
            onItemClick = { device ->
                // 处理设备选择状态变化
                val newSelectedState = !device.isSelected
                
                // 检查是否会导致没有设备被选中
                if (!newSelectedState) {
                    val currentSelectedCount = deviceList.count { it.isSelected }
                    if (device.useDeviceName) {
                        // 名称模式：检查同名设备组是否是唯一选中的组
                        val sameNameDevices = deviceList.filter { it.name == device.name }
                        val otherSelectedDevices = deviceList.filter { it.isSelected && it.name != device.name }
                        if (otherSelectedDevices.isEmpty()) {
                            context?.let {
                                 Toast.makeText(it, "❌ 至少需要选择一个设备，无法取消选择", Toast.LENGTH_SHORT).show()
                             }
                            return@DeviceAdapter
                        }
                    } else {
                        // 路径模式：检查是否是最后一个选中的设备
                        if (currentSelectedCount <= 1) {
                            context?.let {
                                 Toast.makeText(it, "❌ 至少需要选择一个设备，无法取消选择", Toast.LENGTH_SHORT).show()
                             }
                            return@DeviceAdapter
                        }
                    }
                }
                
                // 如果设备使用名称模式，同步所有同名设备的选择状态
                if (device.useDeviceName) {
                    val sameNameDevices = deviceList.filter { it.name == device.name }
                    sameNameDevices.forEach { it.isSelected = newSelectedState }
                    
                    // 刷新所有同名设备的显示
                    rvDevices.post {
                        sameNameDevices.forEach { sameDevice ->
                            val position = deviceList.indexOf(sameDevice)
                            if (position != -1) {
                                deviceAdapter.notifyItemChanged(position)
                            }
                        }
                    }
                } else {
                    // 路径模式下只改变当前设备
                    device.isSelected = newSelectedState
                    
                    // 使用post延迟执行UI更新，避免在布局过程中调用notifyDataSetChanged
                    rvDevices.post {
                        val position = deviceList.indexOf(device)
                        if (position != -1) {
                            deviceAdapter.notifyItemChanged(position)
                        }
                    }
                }
                
                // 设备选择变化时自动保存配置
                if (!isLoadingConfig) {
                    // 检查是否有同名设备，提供更清晰的提示
                    val sameNameDevices = deviceList.filter { it.name == device.name }
                    val deviceDisplayName = if (sameNameDevices.size > 1) {
                        val deviceIndex = sameNameDevices.indexOf(device) + 1
                        "${device.name} (${deviceIndex}/${sameNameDevices.size})"
                    } else {
                        device.name
                    }
                    
                    if (device.useDeviceName && sameNameDevices.size > 1) {
                        // 名称模式下的同名设备组操作提示
                        if (newSelectedState) {
                            context?.let {
                        Toast.makeText(it, "✅ 已选择同名设备组: ${device.name}\n包含 ${sameNameDevices.size} 个设备", Toast.LENGTH_SHORT).show()
                    }
                        } else {
                            context?.let {
                        Toast.makeText(it, "❌ 已取消选择同名设备组: ${device.name}\n包含 ${sameNameDevices.size} 个设备", Toast.LENGTH_SHORT).show()
                    }
                        }
                    } else {
                        // 单个设备操作提示
                        if (newSelectedState) {
                            context?.let {
                            Toast.makeText(it, "✅ 已选择设备: $deviceDisplayName\n路径: ${device.path}", Toast.LENGTH_SHORT).show()
                        }
                        } else {
                            context?.let {
                            Toast.makeText(it, "❌ 已取消选择设备: $deviceDisplayName\n路径: ${device.path}", Toast.LENGTH_SHORT).show()
                        }
                        }
                    }
                    
                    // 自动保存配置
                    saveConfig()
                    
                    // 提示用户需要重启服务
                    Toast.makeText(requireContext(), "设备配置已修改，需要重启服务才能生效", Toast.LENGTH_SHORT).show()
                } else {
                    android.util.Log.d("ModuleFragment", "跳过设备选择触发的保存，正在加载配置中")
                }
            },
            onDeviceTypeToggle = { device ->
                // 处理设备类型切换
                if (!isLoadingConfig) {
                    if (device.useDeviceName) {
                        // 切换到名称模式
                        val sameNameDevices = deviceList.filter { it.name == device.name }
                        if (sameNameDevices.size > 1) {
                            // 同步所有同名设备的选择状态和模式
                            val shouldSelect = sameNameDevices.any { it.isSelected }
                            sameNameDevices.forEach { 
                                it.useDeviceName = true
                                it.isSelected = shouldSelect
                            }
                            context?.let {
                            Toast.makeText(it, "✅ 同名设备组 '${device.name}' 已切换为设备名模式\n包含 ${sameNameDevices.size} 个设备，状态已同步", Toast.LENGTH_LONG).show()
                        }
                        } else {
                            context?.let {
                            Toast.makeText(it, "✅ ${device.name} 已切换为设备名模式", Toast.LENGTH_SHORT).show()
                        }
                        }
                    } else {
                        // 切换到路径模式
                        val sameNameDevices = deviceList.filter { it.name == device.name }
                        if (sameNameDevices.size > 1) {
                            // 将所有同名设备切换为路径模式，保持当前选择状态
                            sameNameDevices.forEach { it.useDeviceName = false }
                            context?.let {
                            Toast.makeText(it, "✅ 同名设备组 '${device.name}' 已切换为设备路径模式\n包含 ${sameNameDevices.size} 个设备，现在可独立选择", Toast.LENGTH_LONG).show()
                        }
                        } else {
                            context?.let {
                            Toast.makeText(it, "✅ ${device.name} 已切换为设备路径模式", Toast.LENGTH_SHORT).show()
                        }
                        }
                    }
                    
                    // 自动保存配置
                    saveConfig()
                    
                    // 提示用户需要重启服务
                    Toast.makeText(requireContext(), "设备配置已修改，需要重启服务才能生效", Toast.LENGTH_SHORT).show()
                    
                    // 立即刷新显示
                    rvDevices.post {
                        deviceAdapter.notifyDataSetChanged()
                    }
                } else {
                    android.util.Log.d("ModuleFragment", "跳过设备类型切换触发的保存，正在加载配置中")
                }
            }
        )
        
        rvDevices.layoutManager = LinearLayoutManager(context)
        rvDevices.adapter = deviceAdapter
    }

    private fun updateSliderValueTexts() {
        tvClickValue.text = "${sliderClick.value.toInt()}ms"
        tvShortPressValue.text = "${sliderShortPress.value.toInt()}ms"
        tvLongPressValue.text = "${sliderLongPress.value.toInt()}ms"
        tvDoubleClickValue.text = "${sliderDoubleClick.value.toInt()}ms"
        updateTimelineDisplay()
    }

    private fun updateTimelineDisplay() {
        // 获取当前滑块值
        val clickValue = sliderClick.value.toInt()
        val shortPressValue = sliderShortPress.value.toInt()
        val longPressValue = sliderLongPress.value.toInt()
        
        // 更新标签文本，包含具体数值
        tvClickLabel.text = "点击\n${clickValue}ms"
        tvShortPressLabel.text = "短按\n${shortPressValue}ms"
        tvLongPressLabel.text = "长按\n${longPressValue}ms"
        
        // 设置最大时间范围，确保0ms在最左端
        val maxTimeRange = 5000f
        
        // 获取时间轴的实际宽度
        val timelineLayout = tvClickLabel.parent as? RelativeLayout ?: return
        
        // 如果布局还未测量，延迟执行
        if (timelineLayout.width == 0) {
            timelineLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    timelineLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    updateTimelineDisplay()
                }
            })
            return
        }
        
        val timelineWidth = timelineLayout.width - timelineLayout.paddingLeft - timelineLayout.paddingRight - 60 // 留出边距
        
        // 计算每个标签的左对齐位置（从0ms开始）
        val clickPosition = if (clickValue > 0 && timelineWidth > 0) (clickValue / maxTimeRange * timelineWidth) else 0f
        val shortPressPosition = if (shortPressValue > 0 && timelineWidth > 0) (shortPressValue / maxTimeRange * timelineWidth) else 0f
        val longPressPosition = if (longPressValue > 0 && timelineWidth > 0) (longPressValue / maxTimeRange * timelineWidth) else 0f
        
        // 设置标签的可见性和位置
        tvClickLabel.visibility = if (clickValue > 0) View.VISIBLE else View.GONE
        tvShortPressLabel.visibility = if (shortPressValue > 0) View.VISIBLE else View.GONE
        tvLongPressLabel.visibility = if (longPressValue > 0) View.VISIBLE else View.GONE
        
        // 使用RelativeLayout的layout_marginStart来设置左对齐位置
        try {
            val clickParams = tvClickLabel.layoutParams as RelativeLayout.LayoutParams
            clickParams.marginStart = max(0, clickPosition.toInt())
            tvClickLabel.layoutParams = clickParams
            
            val shortPressParams = tvShortPressLabel.layoutParams as RelativeLayout.LayoutParams
            shortPressParams.marginStart = max(0, shortPressPosition.toInt())
            tvShortPressLabel.layoutParams = shortPressParams
            
            val longPressParams = tvLongPressLabel.layoutParams as RelativeLayout.LayoutParams
            longPressParams.marginStart = max(0, longPressPosition.toInt())
            tvLongPressLabel.layoutParams = longPressParams
        } catch (e: Exception) {
            // 布局参数可能不匹配，使用默认位置
        }
        
        // 更新分段区域的宽度（使用weight比例）
        if (segmentContainer.width > 0) {
            // 新逻辑：
            // - segmentClick 表示 0 -> 点击 的空白（不可见但占位）
            // - segmentShortPress 表示 点击 -> 短按 的空白（不可见但占位）
            // - segmentLongPress 表示 短按 -> 长按 的区域（短按）
            // - segmentExtra 表示 长按 -> 末尾 的区域（长按）
            val clickSpacerWeight = if (clickValue > 0) max(0f, clickValue / maxTimeRange) else 0f
            val shortPressSpacerWeight = if (shortPressValue > clickValue) max(0f, (shortPressValue - clickValue) / maxTimeRange) else 0f
            val shortRegionWeight = if (longPressValue > shortPressValue) max(0f, (longPressValue - shortPressValue) / maxTimeRange) else 0f
            val longRegionWeight = max(0f, 1.0f - clickSpacerWeight - shortPressSpacerWeight - shortRegionWeight)
            
            // 设置每个区域的权重
            segmentClick.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, clickSpacerWeight)
            segmentShortPress.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, shortPressSpacerWeight)
            segmentLongPress.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, shortRegionWeight)
            segmentExtra.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, longRegionWeight)
            
            // 更新区域标签
            labelClick.text = ""
            labelShortPress.text = ""
            labelLongPress.text = if (shortRegionWeight > 0f) "短按" else ""
            labelExtra.text = if (longRegionWeight > 0f) "长按" else ""
            
            // 显示或隐藏区域标签
            labelClick.visibility = View.VISIBLE
            labelShortPress.visibility = View.VISIBLE
            labelLongPress.visibility = if (shortRegionWeight > 0f) View.VISIBLE else View.GONE
            labelExtra.visibility = if (longRegionWeight > 0f) View.VISIBLE else View.GONE
            
            // 显示或隐藏区域
            segmentClick.visibility = if (clickSpacerWeight > 0f) View.VISIBLE else View.GONE
            segmentShortPress.visibility = if (shortPressSpacerWeight > 0f) View.VISIBLE else View.GONE
            segmentLongPress.visibility = if (shortRegionWeight > 0f) View.VISIBLE else View.GONE
            segmentExtra.visibility = if (longRegionWeight > 0f) View.VISIBLE else View.GONE

        }
    }

    private fun setupClickListeners() {
        // 保存和加载按钮已移除，配置现在会自动保存并立即生效
        
        // 为滑块添加值变化监听器，实现自动保存和值限制
        val clickSliderListener = object : com.google.android.material.slider.Slider.OnChangeListener {
            override fun onValueChange(slider: com.google.android.material.slider.Slider, value: Float, fromUser: Boolean) {
                if (fromUser && !isLoadingConfig) {
                    var adjusted = false
                    val minValue = 100f
                    val actualValue = maxOf(minValue, value)
                    
                    // 确保点击阈值不小于100ms
                    if (actualValue != value) {
                        slider.value = actualValue
                    }
                    
                    // 确保短按 > 点击
                    if (actualValue >= sliderShortPress.value) {
                        sliderShortPress.value = minOf(actualValue + 100f, sliderShortPress.valueTo)
                        adjusted = true
                    }
                    // 确保双击间隔 > 点击
                    if (actualValue >= sliderDoubleClick.value) {
                        sliderDoubleClick.value = minOf(actualValue + 100f, sliderDoubleClick.valueTo)
                        adjusted = true
                    }
                    if (adjusted) {
                        Toast.makeText(context, "点击阈值已调整相关参数", Toast.LENGTH_SHORT).show()
                    }
                    updateSliderValueTexts()
                    rvDevices.removeCallbacks(autoSaveRunnable)
                    rvDevices.postDelayed(autoSaveRunnable, 500)
                }
            }
        }
        
        val shortPressSliderListener = object : com.google.android.material.slider.Slider.OnChangeListener {
            override fun onValueChange(slider: com.google.android.material.slider.Slider, value: Float, fromUser: Boolean) {
                if (fromUser && !isLoadingConfig) {
                    var adjusted = false
                    val minValue = 100f
                    val actualValue = maxOf(minValue, value)
                    
                    // 确保短按阈值不小于100ms
                    if (actualValue != value) {
                        slider.value = actualValue
                    }
                    
                    // 确保短按 > 点击
                    if (actualValue <= sliderClick.value) {
                        sliderClick.value = maxOf(actualValue - 100f, sliderClick.valueFrom)
                        adjusted = true
                    }
                    // 确保长按 > 短按
                    if (actualValue >= sliderLongPress.value) {
                        sliderLongPress.value = minOf(actualValue + 100f, sliderLongPress.valueTo)
                        adjusted = true
                    }
                    if (adjusted) {
                        Toast.makeText(context, "短按阈值已调整相关参数", Toast.LENGTH_SHORT).show()
                    }
                    updateSliderValueTexts()
                    rvDevices.removeCallbacks(autoSaveRunnable)
                    rvDevices.postDelayed(autoSaveRunnable, 500)
                }
            }
        }
        
        val longPressSliderListener = object : com.google.android.material.slider.Slider.OnChangeListener {
            override fun onValueChange(slider: com.google.android.material.slider.Slider, value: Float, fromUser: Boolean) {
                if (fromUser && !isLoadingConfig) {
                    var adjusted = false
                    val minValue = 100f
                    val actualValue = maxOf(minValue, value)
                    
                    // 确保长按阈值不小于100ms
                    if (actualValue != value) {
                        slider.value = actualValue
                    }
                    
                    // 确保长按 > 短按
                    if (actualValue <= sliderShortPress.value) {
                        sliderShortPress.value = maxOf(actualValue - 100f, sliderShortPress.valueFrom)
                        adjusted = true
                        // 同时确保短按 > 点击
                        if (sliderShortPress.value <= sliderClick.value) {
                            sliderClick.value = maxOf(sliderShortPress.value - 100f, sliderClick.valueFrom)
                        }
                    }
                    if (adjusted) {
                        Toast.makeText(context, "长按阈值已调整相关参数", Toast.LENGTH_SHORT).show()
                    }
                    updateSliderValueTexts()
                    rvDevices.removeCallbacks(autoSaveRunnable)
                    rvDevices.postDelayed(autoSaveRunnable, 500)
                }
            }
        }
        
        val doubleClickSliderListener = object : com.google.android.material.slider.Slider.OnChangeListener {
            override fun onValueChange(slider: com.google.android.material.slider.Slider, value: Float, fromUser: Boolean) {
                if (fromUser && !isLoadingConfig) {
                    var adjusted = false
                    val minValue = 100f
                    val actualValue = maxOf(minValue, value)
                    
                    // 确保双击间隔不小于100ms
                    if (actualValue != value) {
                        slider.value = actualValue
                    }
                    
                    // 确保双击间隔 > 点击
                    if (actualValue <= sliderClick.value) {
                        sliderClick.value = maxOf(actualValue - 100f, sliderClick.valueFrom)
                        adjusted = true
                    }
                    if (adjusted) {
                        Toast.makeText(context, "双击间隔已调整相关参数", Toast.LENGTH_SHORT).show()
                    }
                    updateSliderValueTexts()
                    rvDevices.removeCallbacks(autoSaveRunnable)
                    rvDevices.postDelayed(autoSaveRunnable, 500)
                }
            }
        }
        
        sliderClick.addOnChangeListener(clickSliderListener)
        sliderShortPress.addOnChangeListener(shortPressSliderListener)
        sliderLongPress.addOnChangeListener(longPressSliderListener)
        sliderDoubleClick.addOnChangeListener(doubleClickSliderListener)
        
        // 为开关添加监听器，立即保存
        switchEnableLog.setOnCheckedChangeListener { _, _ ->
            if (!isLoadingConfig) {
                saveConfig()
            } else {
                android.util.Log.d("ModuleFragment", "跳过开关触发的保存，正在加载配置中")
            }
        }
        
        // 导入导出按钮点击监听器
        btnImportConfig.setOnClickListener {
            importConfig()
        }
        
        btnExportConfig.setOnClickListener {
            exportConfig()
        }
        
        btnExportLogConfig.setOnClickListener {
            exportLogConfig()
        }
    }
    
    private val autoSaveRunnable = Runnable {
        if (!isLoadingConfig) {
            saveConfig()
        } else {
            android.util.Log.d("ModuleFragment", "跳过自动保存，正在加载配置中")
        }
    }
    

    private fun loadConfig() {
        val mainActivity = activity as? MainActivity
        
        // 设置加载标志，防止触发自动保存
        isLoadingConfig = true
        android.util.Log.d("ModuleFragment", "开始加载配置，已禁用自动保存")
        
        mainActivity?.readConfigFileAsync { configContent ->
            android.util.Log.d("ModuleFragment", "loadConfig读取的配置内容长度: ${configContent?.length ?: 0}")
            android.util.Log.d("ModuleFragment", "loadConfig读取的配置内容前100字符: ${configContent?.take(100) ?: "null"}")
            
            activity?.runOnUiThread {
                if (configContent != null && configContent.isNotEmpty()) {
                    parseConfig(configContent)
                } else {
                    // 配置文件不存在时使用默认配置
                    setDefaultValues()
                }
                
                // 延迟清除加载标志，确保所有UI更新完成
                rvDevices.postDelayed({
                    isLoadingConfig = false
                    android.util.Log.d("ModuleFragment", "配置加载完成，已重新启用自动保存")
                }, 1000)
            }
        }
    }

    private fun parseConfig(content: String) {
        android.util.Log.d("ModuleFragment", "开始解析配置内容，长度: ${content.length}")
        android.util.Log.d("ModuleFragment", "配置内容: $content")
        
        val lines = content.split("\n")
        val configuredDevices = mutableListOf<String>()
        var clickThreshold = 100f
        var shortPressThreshold = 1000f
        var longPressThreshold = 2000f
        var doubleClickInterval = 300f
        
        for (line in lines) {
            val trimmedLine = line.trim()
            android.util.Log.d("ModuleFragment", "处理配置行: '$trimmedLine'")
            if (trimmedLine.startsWith("#") || !trimmedLine.contains("=")) continue
            
            val parts = trimmedLine.split("=", limit = 2)
            if (parts.size != 2) continue
            
            val key = parts[0].trim()
            val value = parts[1].trim()
            
            android.util.Log.d("ModuleFragment", "解析配置项: key='$key', value='$value'")
            
            when (key) {
                "device" -> {
                    // 解析设备配置，支持多个设备用|分隔
                    val devices = value.split("|")
                    for (deviceValue in devices) {
                        val trimmedValue = deviceValue.trim()
                        if (trimmedValue.isNotEmpty()) {
                            configuredDevices.add(trimmedValue)
                        }
                    }
                }
                "click_threshold" -> {
                    clickThreshold = maxOf(100f, value.toFloatOrNull() ?: 100f)
                    android.util.Log.d("ModuleFragment", "读取点击阈值: $clickThreshold")
                }
                "short_press_threshold" -> {
                    shortPressThreshold = maxOf(100f, value.toFloatOrNull() ?: 1000f)
                    android.util.Log.d("ModuleFragment", "读取短按阈值: $shortPressThreshold")
                }
                "long_press_threshold" -> {
                    longPressThreshold = maxOf(100f, value.toFloatOrNull() ?: 2000f)
                    android.util.Log.d("ModuleFragment", "读取长按阈值: $longPressThreshold")
                }
                "double_click_interval" -> {
                    doubleClickInterval = maxOf(100f, value.toFloatOrNull() ?: 300f)
                    android.util.Log.d("ModuleFragment", "读取双击间隔: $doubleClickInterval")
                }
                "enable_log" -> switchEnableLog.isChecked = value == "1"
                "cpu_affinity" -> {
                    // 解析CPU亲和性配置，设置对应的CheckBox
                    setCpuAffinityFromConfig(value)
                }
            }
        }
        
        android.util.Log.d("ModuleFragment", "解析完成 - 点击: $clickThreshold, 短按: $shortPressThreshold, 长按: $longPressThreshold, 双击: $doubleClickInterval")
        
        // 直接应用读取的值，不进行任何调整
        sliderClick.value = clickThreshold
        sliderShortPress.value = shortPressThreshold
        sliderLongPress.value = longPressThreshold
        sliderDoubleClick.value = doubleClickInterval
        
        // 应用配置到已扫描的设备列表
        applyConfigToDevices(configuredDevices)
        
        // 更新显示
        updateSliderValueTexts()
        updateTimelineDisplay()
    }
    
    private fun setCpuAffinityFromConfig(value: String) {
        try {
            // 先清除所有CheckBox的选中状态
            cpuCheckBoxes.forEach { it.isChecked = false }
            
            if (value.isNotEmpty()) {
                // 解析CPU核心列表，支持逗号分隔
                val cpuCores = value.split(",").map { it.trim().toIntOrNull() }.filterNotNull()
                
                // 设置对应CheckBox的选中状态
                for (cpuCore in cpuCores) {
                    if (cpuCore >= 0 && cpuCore < cpuCheckBoxes.size) {
                        cpuCheckBoxes[cpuCore].isChecked = true
                    }
                }
            }
            
            // 如果没有任何CheckBox被选中，默认选中CPU0
            if (cpuCheckBoxes.none { it.isChecked } && cpuCheckBoxes.isNotEmpty()) {
                cpuCheckBoxes[0].isChecked = true
            }
        } catch (e: Exception) {
            android.util.Log.e("ModuleFragment", "解析CPU亲和性配置失败: $value", e)
            // 出错时默认选中CPU0
            if (cpuCheckBoxes.isNotEmpty()) {
                cpuCheckBoxes.forEach { it.isChecked = false }
                cpuCheckBoxes[0].isChecked = true
            }
        }
    }
    
    private fun scanAllDevices(configuredDevices: List<String> = emptyList()) {
        val mainActivity = activity as? MainActivity
        
        // 创建进度对话框
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("扫描设备")
            .setMessage("正在扫描输入设备，请稍候...")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        // 使用su权限扫描/dev/input/目录获取所有输入设备
        mainActivity?.executeRootCommandAsync("find /dev/input -name 'event*' -type c | sort -V") { result ->
            deviceList.clear()
            
            if (result != null && result.isNotEmpty()) {
                val devicePaths = result.split("\n").filter { it.trim().isNotEmpty() }
                
                for (devicePath in devicePaths) {
                    val trimmedPath = devicePath.trim()
                    if (trimmedPath.startsWith("/dev/input/event")) {
                        // 获取设备真实名称
                        val eventNumber = trimmedPath.substringAfterLast("event")
                        val deviceName = getDeviceRealName(eventNumber) ?: "输入设备$eventNumber"
                        
                        // 检查该设备是否在配置文件中已被选中，并确定使用的模式
                        var isSelected = false
                        var useDeviceName = false
                        
                        for (configDevice in configuredDevices) {
                            if (configDevice == trimmedPath) {
                                // 匹配设备路径
                                isSelected = true
                                useDeviceName = false
                                break
                            } else if (configDevice.startsWith("\"") && configDevice.endsWith("\"") && 
                                      configDevice.substring(1, configDevice.length - 1) == deviceName) {
                                // 匹配设备名（去掉双引号）
                                isSelected = true
                                useDeviceName = true
                                break
                            }
                        }
                        

                        
                        deviceList.add(DeviceItem(trimmedPath, isSelected, deviceName, useDeviceName))
                    }
                }
            } else {
                // 如果扫描失败，尝试手动检查更多event设备
                val maxEventNumber = 20 // 检查event0到event20
                var checkedCount = 0
                
                for (i in 0..maxEventNumber) {
                    val devicePath = "/dev/input/event$i"
                    // 异步检查设备是否存在
                    mainActivity?.executeRootCommandAsync("test -e $devicePath && echo 'exists'") { checkResult ->
                        if (checkResult?.trim() == "exists") {
                            val deviceName = getDeviceRealName(i.toString()) ?: "输入设备$i"
                            
                            // 检查该设备是否在配置文件中已被选中，并确定使用的模式
                            var isSelected = false
                            var useDeviceName = false
                            
                            for (configDevice in configuredDevices) {
                                if (configDevice == devicePath) {
                                    // 匹配设备路径
                                    isSelected = true
                                    useDeviceName = false
                                    break
                                } else if (configDevice.startsWith("\"") && configDevice.endsWith("\"") && 
                                          configDevice.substring(1, configDevice.length - 1) == deviceName) {
                                    // 匹配设备名（去掉双引号）
                                    isSelected = true
                                    useDeviceName = true
                                    break
                                }
                            }
                            

                            
                            deviceList.add(DeviceItem(devicePath, isSelected, deviceName, useDeviceName))
                        }
                        
                        checkedCount++
                        // 当所有检查完成后，更新UI
                        if (checkedCount >= maxEventNumber + 1) {
                            activity?.runOnUiThread {
                                progressDialog.dismiss()
                                // 如果仍然没有找到任何设备，添加基本的默认列表
                                if (deviceList.isEmpty()) {
                                    addDefaultDevices(configuredDevices)
                                }
                                
                                // 确保至少有一个设备被选中，如果没有则默认选中第一个设备
                                if (deviceList.isNotEmpty() && deviceList.none { it.isSelected }) {
                                    deviceList[0].isSelected = true
                                }
                                
                                deviceAdapter.notifyDataSetChanged()
                                val deviceCount = deviceList.size
                                context?.let {
                                    Toast.makeText(it, "已扫描到 $deviceCount 个输入设备", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                
                // 如果仍然没有找到任何设备，添加基本的默认列表
                if (deviceList.isEmpty()) {
                    activity?.runOnUiThread {
                        progressDialog.dismiss()
                    }
                    addDefaultDevices(configuredDevices)
                    
                    // 确保至少有一个设备被选中，如果没有则默认选中第一个设备
                    if (deviceList.isNotEmpty() && deviceList.none { it.isSelected }) {
                        deviceList[0].isSelected = true
                    }
                }
            }
            
            // 确保在主线程更新UI
            activity?.runOnUiThread {
                progressDialog.dismiss()
                
                // 确保至少有一个设备被选中，如果没有则默认选中第一个设备
                if (deviceList.isNotEmpty() && deviceList.none { it.isSelected }) {
                    deviceList[0].isSelected = true
                }
                
                deviceAdapter.notifyDataSetChanged()
                
                // 显示扫描结果
                val deviceCount = deviceList.size
                context?.let {
                    Toast.makeText(it, "已扫描到 $deviceCount 个输入设备", Toast.LENGTH_SHORT).show()
                }  
            }
        }
    }

    private fun addDefaultDevices(configuredDevices: List<String>): MutableList<DeviceItem> {
        val basicDefaults = listOf(
            "/dev/input/event0",
            "/dev/input/event1",
            "/dev/input/event2",
            "/dev/input/event3",
            "/dev/input/event4",
            "/dev/input/event5"
        )
        
        for ((index, device) in basicDefaults.withIndex()) {
            val eventNumber = device.substringAfterLast("event")
            val deviceName = getDeviceRealName(eventNumber) ?: "输入设备$eventNumber"
            
            // 检查该设备是否在配置文件中已被选中
            var isSelected = configuredDevices.any { configDevice ->
                // 匹配设备路径
                configDevice == device ||
                // 匹配设备名（去掉双引号）
                (configDevice.startsWith("\"") && configDevice.endsWith("\"") && 
                 configDevice.substring(1, configDevice.length - 1) == deviceName)
            }
            

            
            deviceList.add(DeviceItem(device, isSelected, deviceName))
        }
        
        // 确保至少有一个设备被选中，如果没有则默认选中第一个设备
        if (deviceList.isNotEmpty() && deviceList.none { it.isSelected }) {
            deviceList[0].isSelected = true
        }

        return deviceList
    }

    private fun setDefaultValues() {
        // 设置默认值，确保符合限制条件：短按>点击，长按>短按，双击间隔>点击
        val clickDefault = 100f
        val shortPressDefault = 1000f
        val longPressDefault = 2000f
        val doubleClickDefault = 300f
        
        sliderClick.value = clickDefault
        sliderShortPress.value = maxOf(shortPressDefault, clickDefault + 100f)
        sliderLongPress.value = maxOf(longPressDefault, sliderShortPress.value + 100f)
        sliderDoubleClick.value = maxOf(doubleClickDefault, clickDefault + 100f)
        switchEnableLog.isChecked = false
        updateSliderValueTexts()
        // CPU亲和性的默认值在createCpuCheckBoxes方法中处理
    }

    private fun saveConfig() {
        val mainActivity = activity as? MainActivity
        
        // 先读取当前配置文件，保留按键配置
        mainActivity?.readConfigFileAsync { currentConfig ->
            val configContent = currentConfig ?: ""
            
            // 解析现有配置，保留按键配置（script_开头的行）
            val lines = configContent.split("\n").toMutableList()
            val keyConfigLines = lines.filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith("script_") && trimmed.contains("=")
            }
            
            // 构建新的配置内容
            val configBuilder = StringBuilder()
            
            // 添加注释和说明
            configBuilder.append("# KCTRL 系统配置\n")
            configBuilder.append("# 格式: key=value\n")
            configBuilder.append("# 以#开头的行为注释\n")
            configBuilder.append("\n")
            
            // 设备配置
            val selectedDevices = deviceList.filter { it.isSelected }
            if (selectedDevices.isNotEmpty()) {
                configBuilder.append("# 要监听的输入设备\n")
                configBuilder.append("# 支持多个设备，用|分隔符隔开\n")
                configBuilder.append("# 设备名用双引号包围，设备路径直接使用\n")
                
                // 处理同名设备的配置保存
                val deviceNameGroups = selectedDevices.filter { it.useDeviceName }.groupBy { it.name }
                val pathDevices = selectedDevices.filter { !it.useDeviceName }
                
                // 构建设备配置值列表
                val deviceValues = mutableListOf<String>()
                
                // 添加使用名称模式的设备（同名设备只添加一次）
                deviceNameGroups.forEach { (name, devices) ->
                    deviceValues.add("\"$name\"")
                    if (devices.size > 1) {
                        configBuilder.append("# 设备名 '$name' 包含 ${devices.size} 个物理设备: ${devices.map { it.path }.joinToString(", ")}\n")
                    }
                }
                
                // 添加使用路径模式的设备
                pathDevices.forEach { device ->
                    deviceValues.add(device.path)
                }
                
                val deviceValuesString = deviceValues.joinToString("|")
                configBuilder.append("device=$deviceValuesString\n")
            }
            configBuilder.append("\n")
            
            // 时间配置
            configBuilder.append("# 时间配置参数（毫秒）\n")
            configBuilder.append("click_threshold=${sliderClick.value.toInt()}\n")
            configBuilder.append("short_press_threshold=${sliderShortPress.value.toInt()}\n")
            configBuilder.append("long_press_threshold=${sliderLongPress.value.toInt()}\n")
            configBuilder.append("double_click_interval=${sliderDoubleClick.value.toInt()}\n")
            configBuilder.append("\n")
            
            // 日志配置
            configBuilder.append("# 日志开关配置\n")
            configBuilder.append("enable_log=${if (switchEnableLog.isChecked) "1" else "0"}\n")
            configBuilder.append("\n")
            
            // CPU亲和性配置
            configBuilder.append("# CPU亲和性配置（可选）\n")
            configBuilder.append("# 指定程序运行在哪些CPU核心上，用逗号分隔\n")
            configBuilder.append("# 例如: cpu_affinity=0 表示只使用CPU0\n")
            configBuilder.append("# 例如: cpu_affinity=0,1 表示使用CPU0和CPU1\n")
            configBuilder.append("# 例如: cpu_affinity=2,3,4 表示使用CPU2、CPU3和CPU4\n")
            configBuilder.append("# 如果不配置此项，默认使用CPU0\n")
            val selectedCpuCores = cpuCheckBoxes.mapIndexedNotNull { index, checkBox ->
                if (checkBox.isChecked) index else null
            }
            val cpuAffinityValue = if (selectedCpuCores.isNotEmpty()) {
                selectedCpuCores.joinToString(",")
            } else {
                "0" // 默认使用CPU0
            }
            configBuilder.append("cpu_affinity=$cpuAffinityValue\n")
            
            // 保留现有的按键配置
            if (keyConfigLines.isNotEmpty()) {
                configBuilder.append("\n")
                configBuilder.append("# 按键配置（由按键配置页面管理）\n")
                keyConfigLines.forEach { line ->
                    configBuilder.append("$line\n")
                }
            }
            
            // 使用异步的配置文件写入方法
            mainActivity.writeConfigFileAsync(configBuilder.toString()) { success ->
                activity?.runOnUiThread {
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
        }
    }
    
    private fun importConfig() {
        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }
    
    private fun exportConfig() {
        val timestamp = System.currentTimeMillis()
        exportLauncher.launch("kctrl_config_$timestamp.zip")
    }
    
    private fun exportLogConfig() {
        val timestamp = System.currentTimeMillis()
        exportLogLauncher.launch("kctrl_log_config_$timestamp.txt")
    }
    
    private fun exportConfigToUri(uri: Uri) {
        val mainActivity = activity as? MainActivity ?: return
        
        Thread {
            try {
                val contentResolver = requireContext().contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        // 导出 config.txt - 直接读取原始文件内容
                        android.util.Log.d("ModuleFragment", "开始导出配置文件...")
                        
                        // 先检查配置文件是否存在
                        val fileCheckResult = mainActivity.executeRootCommand("test -f ${MainActivity.CONFIG_FILE} && echo 'exists' || echo 'not_exists'")
                        android.util.Log.d("ModuleFragment", "配置文件检查结果: $fileCheckResult")
                        
                        val configEntry = ZipEntry("config.txt")
                        zipOut.putNextEntry(configEntry)
                        
                        if (fileCheckResult?.trim() == "exists") {
                            // 直接读取原始文件内容，不经过base64解码
                            val rawConfigContent = mainActivity.executeRootCommand("cat ${MainActivity.CONFIG_FILE} 2>/dev/null")
                            if (!rawConfigContent.isNullOrEmpty()) {
                                zipOut.write(rawConfigContent.toByteArray(Charsets.UTF_8))
                                android.util.Log.d("ModuleFragment", "配置文件导出成功，内容长度: ${rawConfigContent.length}")
                            } else {
                                // 如果配置为空，创建默认配置
                                val defaultConfig = "# KCtrl 配置文件\n# 此文件由应用自动生成\n"
                                zipOut.write(defaultConfig.toByteArray())
                                android.util.Log.w("ModuleFragment", "配置文件为空，已导出默认配置")
                            }
                        } else {
                            // 如果配置文件不存在，创建默认配置
                            val defaultConfig = "# KCtrl 配置文件\n# 此文件由应用自动生成\n"
                            zipOut.write(defaultConfig.toByteArray())
                            android.util.Log.w("ModuleFragment", "配置文件不存在，已导出默认配置")
                        }
                        zipOut.closeEntry()
                        
                        // 导出 scripts 文件夹 - 使用同步root命令
                        exportScriptsToZipSync(mainActivity, zipOut)
                        
                        // 导出完成后的操作
                        activity?.runOnUiThread {
                            context?.let {
                                Toast.makeText(it, "配置导出成功", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    context?.let {
                        Toast.makeText(it, "配置导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }
    
    private fun importConfigFromUri(uri: Uri) {
        val mainActivity = activity as? MainActivity ?: return
        
        Thread {
            try {
                val contentResolver = requireContext().contentResolver
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry: ZipEntry?
                        while (zipIn.nextEntry.also { entry = it } != null) {
                            val entryName = entry!!.name
                            
                            if (entryName == "config.txt") {
                                // 导入配置文件 - 使用ByteArray确保完整读取
                                val configBytes = zipIn.readBytes()
                                val configContent = String(configBytes, Charsets.UTF_8)
                                android.util.Log.d("ModuleFragment", "ZIP中配置文件字节数: ${configBytes.size}")
                                android.util.Log.d("ModuleFragment", "导入的配置内容长度: ${configContent.length}")
                                android.util.Log.d("ModuleFragment", "导入的配置内容前100字符: ${configContent.take(100)}")
                                
                                // 使用异步方式写入配置文件
                                val configContentStr = String(configBytes, Charsets.UTF_8)
                                mainActivity.writeConfigFileAsync(configContentStr) { writeSuccess ->
                                    android.util.Log.d("ModuleFragment", "配置文件写入结果: $writeSuccess")
                                    
                                    if (writeSuccess) {
                                        // 验证写入的内容
                                        mainActivity.readConfigFileAsync { writtenContent ->
                                            android.util.Log.d("ModuleFragment", "写入后读取的内容长度: ${writtenContent?.length ?: 0}")
                                        }
                                    }
                                }
                            } else if (entryName.startsWith("scripts/")) {
                                // 导入脚本文件 - 使用异步root命令
                                if (!entry!!.isDirectory) {
                                    val scriptContent = zipIn.readBytes().toString(Charsets.UTF_8)
                                    val targetPath = "${MainActivity.KCTRL_MODULE_PATH}/$entryName"
                                    
                                    // 异步确保目录存在
                                    val dirPath = targetPath.substringBeforeLast("/")
                                    mainActivity.executeRootCommandAsync("mkdir -p '$dirPath'") { _ ->
                                        // 异步使用base64编码写入文件内容
                                        val encoded = android.util.Base64.encodeToString(scriptContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                                        val writeCommand = "echo '$encoded' | base64 -d > '$targetPath' && chmod +x '$targetPath'"
                                        mainActivity.executeRootCommandAsync(writeCommand) { result ->
                                            android.util.Log.d("ModuleFragment", "脚本文件 $entryName 导入结果: $result")
                                        }
                                    }
                                }
                            }
                            zipIn.closeEntry()
                        }
                    }
                }
                
                activity?.runOnUiThread {
                    context?.let {
                    Toast.makeText(it, "配置导入成功，正在刷新设备列表...", Toast.LENGTH_SHORT).show()
                }
                    
                    // 异步验证配置是否正确写入
                    mainActivity.readConfigFileAsync { verifyContent ->
                        android.util.Log.d("ModuleFragment", "导入后验证读取的配置内容长度: ${verifyContent?.length ?: 0}")
                        android.util.Log.d("ModuleFragment", "导入后验证读取的配置内容前100字符: ${verifyContent?.take(100) ?: "null"}")
                        
                        activity?.runOnUiThread {
                            // 取消任何待执行的自动保存
                            rvDevices.removeCallbacks(autoSaveRunnable)
                            
                            // 重新加载配置并刷新设备列表
                            loadConfig()
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    context?.let {
                    Toast.makeText(it, "配置导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                }
            }
        }.start()
    }
    
    private fun exportScriptsToZip(mainActivity: MainActivity, zipOut: ZipOutputStream, onComplete: () -> Unit) {
        val scriptsPath = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        
        // 检查scripts目录是否存在
        mainActivity.executeRootCommandAsync("test -d $scriptsPath && echo 'exists' || echo 'not_exists'") { checkResult ->
            if (checkResult?.trim() != "exists") {
                onComplete() // scripts目录不存在，跳过
                return@executeRootCommandAsync
            }
            
            // 获取scripts目录下的所有文件
            mainActivity.executeRootCommandAsync("find $scriptsPath -type f 2>/dev/null || true") { fileList ->
                if (fileList.isNullOrEmpty()) {
                    onComplete() // 没有文件
                    return@executeRootCommandAsync
                }
                
                val filePaths = fileList.split("\n").filter { it.trim().isNotEmpty() }
                if (filePaths.isEmpty()) {
                    onComplete()
                    return@executeRootCommandAsync
                }
                
                var processedCount = 0
                
                // 处理每个文件
                filePaths.forEach { filePath ->
                    val relativePath = filePath.removePrefix("${MainActivity.KCTRL_MODULE_PATH}/")
                    
                    // 读取文件内容
                    mainActivity.executeRootCommandAsync("cat '$filePath' 2>/dev/null || true") { fileContent ->
                        if (!fileContent.isNullOrEmpty()) {
                            val fileEntry = ZipEntry(relativePath)
                            zipOut.putNextEntry(fileEntry)
                            zipOut.write(fileContent.toByteArray())
                            zipOut.closeEntry()
                        }
                        
                        processedCount++
                        if (processedCount >= filePaths.size) {
                            onComplete()
                        }
                    }
                }
            }
        }
    }
    
    private fun exportScriptsToZipSync(mainActivity: MainActivity, zipOut: ZipOutputStream) {
        val scriptsPath = "${MainActivity.KCTRL_MODULE_PATH}/scripts"
        
        // 检查scripts目录是否存在
        val checkResult = mainActivity.executeRootCommand("test -d $scriptsPath && echo 'exists' || echo 'not_exists'")
        if (checkResult?.trim() != "exists") {
            return // scripts目录不存在，跳过
        }
        
        // 获取scripts目录下的所有文件
        val fileList = mainActivity.executeRootCommand("find $scriptsPath -type f 2>/dev/null || true")
        if (fileList.isNullOrEmpty()) {
            return // 没有文件
        }
        
        val filePaths = fileList.split("\n").filter { it.trim().isNotEmpty() }
        if (filePaths.isEmpty()) {
            return
        }
        
        // 处理每个文件
        filePaths.forEach { filePath ->
            val relativePath = filePath.removePrefix("${MainActivity.KCTRL_MODULE_PATH}/")
            
            // 读取文件内容
            val fileContent = mainActivity.executeRootCommand("cat '$filePath' 2>/dev/null || true")
            if (!fileContent.isNullOrEmpty()) {
                val fileEntry = ZipEntry(relativePath)
                zipOut.putNextEntry(fileEntry)
                zipOut.write(fileContent.toByteArray())
                zipOut.closeEntry()
            }
        }
    }
    
    private fun exportLogConfigToUri(uri: Uri) {
        val mainActivity = activity as? MainActivity ?: return
        
        Thread {
            try {
                val contentResolver = requireContext().contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    // 创建日志配置内容
                    val logConfigBuilder = StringBuilder()
                    logConfigBuilder.append("# KCtrl 日志配置导出\n")
                    logConfigBuilder.append("# 导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
                    logConfigBuilder.append("\n")
                    
                    // 读取当前配置文件中的日志相关配置
                    val fileCheckResult = mainActivity.executeRootCommand("test -f ${MainActivity.CONFIG_FILE} && echo 'exists' || echo 'not_exists'")
                    
                    if (fileCheckResult?.trim() == "exists") {
                        val configContent = mainActivity.executeRootCommand("cat ${MainActivity.CONFIG_FILE} 2>/dev/null")
                        if (!configContent.isNullOrEmpty()) {
                            // 提取日志相关的配置项
                            val lines = configContent.split("\n")
                            logConfigBuilder.append("# 当前配置文件中的日志配置:\n")
                            
                            for (line in lines) {
                                val trimmedLine = line.trim()
                                if (trimmedLine.startsWith("enable_log") || 
                                    trimmedLine.startsWith("#") && (trimmedLine.contains("日志") || trimmedLine.contains("log"))) {
                                    logConfigBuilder.append(trimmedLine).append("\n")
                                }
                            }
                        } else {
                            logConfigBuilder.append("# 当前配置文件为空\n")
                        }
                    } else {
                        logConfigBuilder.append("# 配置文件不存在\n")
                    }
                    
                    logConfigBuilder.append("\n")
                    logConfigBuilder.append("# 当前应用中的日志设置:\n")
                    logConfigBuilder.append("enable_log=${if (switchEnableLog.isChecked) "1" else "0"}\n")
                    
                    // 检查日志文件是否存在（如果服务已运行）
            val logFileCheck = mainActivity.executeRootCommand("test -f /data/adb/modules/kctrl/klog.log && echo 'exists' || echo 'not_exists'")
            if (logFileCheck?.trim() == "exists") {
                val logFileSize = mainActivity.executeRootCommand("du -h /data/adb/modules/kctrl/klog.log 2>/dev/null | cut -f1")
                logConfigBuilder.append("# 日志文件大小: ${logFileSize?.trim() ?: "未知"}\n")
                
                val logFileLines = mainActivity.executeRootCommand("wc -l /data/adb/modules/kctrl/klog.log 2>/dev/null | cut -d' ' -f1")
                logConfigBuilder.append("# 日志文件行数: ${logFileLines?.trim() ?: "未知"}\n")
            } else {
                logConfigBuilder.append("# 日志文件不存在 (/data/adb/modules/kctrl/klog.log)\n")
            }
            
            logConfigBuilder.append("\n")
            logConfigBuilder.append("# ========================================\n")
            logConfigBuilder.append("# 配置文件内容 (config.txt)\n")
            logConfigBuilder.append("# ========================================\n")
            
            // 读取配置文件内容
            val configContent = mainActivity.executeRootCommand("cat /data/adb/modules/kctrl/config.txt 2>/dev/null")
            if (configContent != null && configContent.isNotEmpty()) {
                logConfigBuilder.append(configContent)
            } else {
                logConfigBuilder.append("# 配置文件不存在或为空\n")
            }
            
            logConfigBuilder.append("\n")
            logConfigBuilder.append("# ========================================\n")
            logConfigBuilder.append("# 日志文件内容 (klog.log)\n")
            logConfigBuilder.append("# ========================================\n")
            
            // 读取日志文件内容（限制为最后1000行，防止文件过大）
            if (logFileCheck?.trim() == "exists") {
                val logContent = mainActivity.executeRootCommand("tail -1000 /data/adb/modules/kctrl/klog.log 2>/dev/null")
                if (logContent != null && logContent.isNotEmpty()) {
                    logConfigBuilder.append(logContent)
                } else {
                    logConfigBuilder.append("# 日志文件为空\n")
                }
            } else {
                logConfigBuilder.append("# 日志文件不存在\n")
            }
            
            // 写入完整配置到文件
            outputStream.write(logConfigBuilder.toString().toByteArray(Charsets.UTF_8))
                    
                    activity?.runOnUiThread {
                        context?.let {
                            Toast.makeText(it, "日志配置导出成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    context?.let {
                        Toast.makeText(it, "日志配置导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }
    
    private fun addDirectoryToZip(zipOut: ZipOutputStream, dir: File, basePath: String) {
        val files = dir.listFiles() ?: return
        
        for (file in files) {
            val entryPath = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
            
            if (file.isDirectory) {
                // 添加目录条目
                val dirEntry = ZipEntry("$entryPath/")
                zipOut.putNextEntry(dirEntry)
                zipOut.closeEntry()
                
                // 递归添加子目录
                addDirectoryToZip(zipOut, file, entryPath)
            } else {
                // 添加文件
                val fileEntry = ZipEntry(entryPath)
                zipOut.putNextEntry(fileEntry)
                
                FileInputStream(file).use { fileIn ->
                    fileIn.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
    
    private fun scanAllDevicesSilently() {
        val mainActivity = activity as? MainActivity
        
        // 静默扫描，不显示进度对话框
        mainActivity?.executeRootCommandAsync("find /dev/input -name 'event*' -type c | sort -V") { result ->
            deviceList.clear()
            
            if (result != null && result.isNotEmpty()) {
                val devicePaths = result.split("\n").filter { it.trim().isNotEmpty() }
                
                for (devicePath in devicePaths) {
                    val trimmedPath = devicePath.trim()
                    if (trimmedPath.startsWith("/dev/input/event")) {
                        // 获取设备真实名称
                        val eventNumber = trimmedPath.substringAfterLast("event")
                        val deviceName = getDeviceRealName(eventNumber) ?: "输入设备$eventNumber"
                        
                        deviceList.add(DeviceItem(trimmedPath, false, deviceName, false))
                    }
                }
            } else {
                // 如果扫描失败，尝试手动检查更多event设备
                val maxEventNumber = 20
                var checkedCount = 0
                
                for (i in 0..maxEventNumber) {
                    val devicePath = "/dev/input/event$i"
                    mainActivity?.executeRootCommandAsync("test -e $devicePath && echo 'exists'") { checkResult ->
                        if (checkResult?.trim() == "exists") {
                            val deviceName = getDeviceRealName(i.toString()) ?: "输入设备$i"
                            deviceList.add(DeviceItem(devicePath, false, deviceName, false))
                        }
                        
                        checkedCount++
                        if (checkedCount >= maxEventNumber + 1) {
                            activity?.runOnUiThread {
                                if (deviceList.isEmpty()) {
                                    addDefaultDevices(emptyList())
                                }
                                deviceAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
                return@executeRootCommandAsync
            }
            
            // 静默更新UI
            activity?.runOnUiThread {
                if (deviceList.isEmpty()) {
                    addDefaultDevices(emptyList())
                }
                deviceAdapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun applyConfigToDevices(configuredDevices: List<String>) {
        // 重置所有设备的选中状态
        deviceList.forEach { device ->
            device.isSelected = false
            device.useDeviceName = false
            
            // 根据配置文件设置选中状态和模式
            for (configDevice in configuredDevices) {
                if (configDevice == device.path) {
                    // 匹配设备路径
                    device.isSelected = true
                    device.useDeviceName = false
                    break
                } else if (configDevice.startsWith("\"") && configDevice.endsWith("\"") && 
                          configDevice.substring(1, configDevice.length - 1) == device.name) {
                    // 匹配设备名（去掉双引号）
                    device.isSelected = true
                    device.useDeviceName = true
                    break
                }
            }
        }
        
        // 更新UI
        activity?.runOnUiThread {
            deviceAdapter.notifyDataSetChanged()
        }
    }
}

data class DeviceItem(
    val path: String,
    var isSelected: Boolean,
    val name: String = "未知设备",
    var useDeviceName: Boolean = false // true表示使用设备名，false表示使用设备路径
)