package com.idlike.kctrl.mgr

import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.text.TextWatcher
import android.widget.AdapterView
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class FloatingWindowDemoActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var etCustomText: EditText
    private lateinit var spinnerIcon: Spinner
    private lateinit var spinnerWidth: Spinner
    private lateinit var btnShowCustom: MaterialButton
    private lateinit var btnHideFloating: MaterialButton
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnClearCustom: MaterialButton
    private lateinit var ivCustomIcon: ImageView
    private lateinit var etAmCommand: EditText
    private lateinit var etX: EditText
    private lateinit var etY: EditText
    private lateinit var rvCustomIcons: RecyclerView
    private lateinit var customIconAdapter: CustomIconAdapter
    
    private var customIconPath: String? = null
    private var customIconBase64: String? = null // 保留旧字段用于清理
    private val PICK_IMAGE_REQUEST = 1001
    
    // 图标选项
    private val iconOptions = arrayOf(
        "配置图标" to R.drawable.ic_config,
        "状态图标" to R.drawable.ic_status,
        "模块图标" to R.drawable.ic_module,
        "播放图标" to R.drawable.ic_play_arrow,
        "编辑图标" to R.drawable.ic_edit,
        "保存图标" to R.drawable.ic_save,
        "添加图标" to R.drawable.ic_add,
        "删除图标" to R.drawable.ic_delete,
        "下载图标" to R.drawable.ic_download,
        "上传图标" to R.drawable.ic_upload,
        "返回图标" to R.drawable.ic_arrow_back,
        "展开图标" to R.drawable.ic_expand_more,
        "拖拽图标" to R.drawable.ic_drag_handle,
        "错误图标" to R.drawable.ic_error,
        "空状态图标" to R.drawable.ic_empty,
        "占位符图标" to R.drawable.ic_placeholder,
        "捐赠二维码" to R.drawable.qr_donate,
        "应用图标1" to R.drawable.jy,
        "应用图标2" to R.drawable.xl,
        "应用图标3" to R.drawable.zd
    )
    
    // 宽度选项
    private val widthOptions = arrayOf(
        "窄 (120dp)" to 120,
        "标准 (160dp)" to 160,
        "宽 (200dp)" to 200,
        "更宽 (240dp)" to 240
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用动态颜色（莫奈取色）
        DynamicColors.applyToActivityIfAvailable(this)
        
        setContentView(R.layout.activity_floating_window_demo)
        
        initViews()
        setupToolbar()
        setupSpinner()
        setupClickListeners()
        
        // 恢复保存的自定义图标
        restoreCustomIcon()
        
        // 初始化AM命令
        updateAmCommand()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etCustomText = findViewById(R.id.et_custom_text)
        spinnerIcon = findViewById(R.id.spinner_icon)
        spinnerWidth = findViewById(R.id.spinner_width)
        btnShowCustom = findViewById(R.id.btn_show_custom)
        btnHideFloating = findViewById(R.id.btn_hide_floating)
        btnSelectImage = findViewById(R.id.btn_select_image)
        btnClearCustom = findViewById(R.id.btn_clear_custom)
        ivCustomIcon = findViewById(R.id.iv_custom_icon)
        etAmCommand = findViewById(R.id.et_am_command)
        etX = findViewById(R.id.et_x)
        etY = findViewById(R.id.et_y)
        rvCustomIcons = findViewById(R.id.rv_custom_icons)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "悬浮窗演示"
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupSpinner() {
        // 设置图标选择器
        val iconAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            iconOptions.map { it.first }
        )
        iconAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerIcon.adapter = iconAdapter
        
        // 设置宽度选择器
        val widthAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            widthOptions.map { it.first }
        )
        widthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerWidth.adapter = widthAdapter
    }
    
    private fun setupClickListeners() {
        btnShowCustom.setOnClickListener {
            showCustomFloatingWindow()
        }
        
        btnHideFloating.setOnClickListener {
            FloatingWindowManager.hideFloatingWindow(this)
        }
        
        btnSelectImage.setOnClickListener {
            selectImage()
        }
        
        btnClearCustom.setOnClickListener {
            clearCustomIcon()
        }
        
        rvCustomIcons.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        customIconAdapter = CustomIconAdapter(this, mutableListOf(), 
            onIconSelected = { icon ->
                customIconPath = icon.filePath
                updateAmCommand()
                getSharedPreferences("floating_prefs", Context.MODE_PRIVATE).edit()
                    .putString("custom_icon_path", icon.filePath)
                    .apply()
                val bitmap = BitmapFactory.decodeFile(icon.filePath)
                if (bitmap != null) {
                    ivCustomIcon.setImageBitmap(bitmap)
                    ivCustomIcon.visibility = ImageView.VISIBLE
                }
            },
            onIconDeleted = { icon ->
                // 删除文件
                val file = File(icon.filePath)
                if (file.exists()) {
                    file.delete()
                }
                
                // 如果删除的是当前选中的图标，清除选择
                if (customIconPath == icon.filePath) {
                    customIconPath = null
                    ivCustomIcon.visibility = ImageView.GONE
                    getSharedPreferences("floating_prefs", Context.MODE_PRIVATE).edit()
                        .remove("custom_icon_path")
                        .apply()
                    updateAmCommand()
                }
            }
        )
        rvCustomIcons.adapter = customIconAdapter
        
        // 添加左滑删除功能
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val icon = customIconAdapter.getIconAt(position)
                customIconAdapter.removeIcon(position)
                
                // 删除文件
                val file = File(icon.filePath)
                if (file.exists()) {
                    file.delete()
                }
                
                // 如果删除的是当前选中的图标，清除选择
                if (customIconPath == icon.filePath) {
                    customIconPath = null
                    ivCustomIcon.visibility = ImageView.GONE
                    getSharedPreferences("floating_prefs", Context.MODE_PRIVATE).edit()
                        .remove("custom_icon_path")
                        .apply()
                    updateAmCommand()
                }
            }

            override fun onChildDraw(
                canvas: android.graphics.Canvas,
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top
                val isCancelled = dX == 0f && !isCurrentlyActive

                if (isCancelled) {
                    clearCanvas(canvas, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                // 绘制删除背景
                val background = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(canvas)

                // 绘制删除图标
                val deleteIcon = androidx.core.content.ContextCompat.getDrawable(this@FloatingWindowDemoActivity, R.drawable.ic_delete)
                deleteIcon?.let {
                    val iconMargin = (itemHeight - it.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemHeight - it.intrinsicHeight) / 2
                    val iconBottom = iconTop + it.intrinsicHeight
                    val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                    val iconRight = itemView.right - iconMargin

                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.setTint(android.graphics.Color.WHITE)
                    it.draw(canvas)
                }

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            private fun clearCanvas(c: android.graphics.Canvas, left: Float, top: Float, right: Float, bottom: Float) {
                c.drawRect(left, top, right, bottom, android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
            }
        })
        itemTouchHelper.attachToRecyclerView(rvCustomIcons)
        loadCustomIcons()
        
        // 设置文本变化和图标选择变化监听器，实时更新AM命令
        etCustomText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAmCommand()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        spinnerIcon.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateAmCommand()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 监听宽度选择器变化
        spinnerWidth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateAmCommand()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        etX.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAmCommand()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        etY.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAmCommand()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // 点击AM命令文本框复制到剪贴板
        etAmCommand.setOnClickListener {
            val command = etAmCommand.text.toString()
            if (command.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("AM Command", command)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "命令已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showCustomFloatingWindow() {
        if (!FloatingWindowManager.hasOverlayPermission(this)) {
            FloatingWindowManager.requestOverlayPermission(this)
            return
        }
        
        val customText = etCustomText.text.toString().ifEmpty { "自定义悬浮窗" }
        val selectedIconIndex = spinnerIcon.selectedItemPosition
        val iconId = iconOptions[selectedIconIndex].second
        val selectedWidthIndex = spinnerWidth.selectedItemPosition
        val widthDp = widthOptions[selectedWidthIndex].second
        val x = etX.text.toString().toIntOrNull() ?: -1
        val y = etY.text.toString().toIntOrNull() ?: -1
        
        FloatingWindowManager.startFloatingWindow(this, customText, iconId, widthDp, x, y, customIconPath)
    }
    
    private fun updateAmCommand() {
        val customText = etCustomText.text.toString().ifEmpty { "KCtrl 控制器" }
        val selectedIconIndex = spinnerIcon.selectedItemPosition
        val iconName = iconOptions[selectedIconIndex].first
        val selectedWidthIndex = spinnerWidth.selectedItemPosition
        val widthDp = widthOptions[selectedWidthIndex].second
        val x = etX.text.toString().toIntOrNull() ?: -1
        val y = etY.text.toString().toIntOrNull() ?: -1

        // 生成AM命令
        val packageName = packageName
        val activityName = "com.idlike.kctrl.mgr.FloatingWindowService"
        val textParam = customText.replace(" ", "\\ ") // 转义空格
        val iconParam = when (iconName) {
            "配置图标" -> "ic_config"
            "状态图标" -> "ic_status" 
            "模块图标" -> "ic_module"
            "播放图标" -> "ic_play_arrow"
            "编辑图标" -> "ic_edit"
            "保存图标" -> "ic_save"
            "添加图标" -> "ic_add"
            "删除图标" -> "ic_delete"
            "下载图标" -> "ic_download"
            "上传图标" -> "ic_upload"
            "返回图标" -> "ic_arrow_back"
            "展开图标" -> "ic_expand_more"
            "拖拽图标" -> "ic_drag_handle"
            "错误图标" -> "ic_error"
            "空状态图标" -> "ic_empty"
            "占位符图标" -> "ic_placeholder"
            "捐赠二维码" -> "qr_donate"
            "应用图标1" -> "jy"
            "应用图标2" -> "xl"
            "应用图标3" -> "zd"
            else -> "ic_config"
        }
        
        val pathParam = customIconPath?.let { " --es extra_custom_icon_path \"$it\"" } ?: ""
        
        val amCommand = "am startservice -n $packageName/$activityName -a action_show --es floating true --es text \"$textParam\" --es icon $iconParam --ei width $widthDp --ei x $x --ei y $y$pathParam"
        etAmCommand.setText(amCommand)
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun clearCustomIcon() {
        customIconBase64 = null
        customIconPath = null
        getSharedPreferences("floating_prefs", Context.MODE_PRIVATE).edit()
            .remove("custom_icon_path")
            .apply()
        ivCustomIcon.setImageResource(0)
        ivCustomIcon.visibility = ImageView.GONE
        customIconAdapter.setSelectedIcon("")
        updateAmCommand()
    }

    private fun restoreCustomIcon() {
        val prefs = getSharedPreferences("floating_prefs", Context.MODE_PRIVATE)
        val savedPath = prefs.getString("custom_icon_path", null)
        
        savedPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                customIconPath = path
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    ivCustomIcon.setImageBitmap(bitmap)
                    ivCustomIcon.visibility = ImageView.VISIBLE
                }
                customIconAdapter.setSelectedIcon(path)
            }
        }
    }

    private fun loadCustomIcons() {
        val iconFiles = filesDir.listFiles { file ->
            file.name.startsWith("custom_icon_") && file.extension.equals("png", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val icons = iconFiles.map { file ->
            CustomIconAdapter.CustomIcon(file.absolutePath, file.name)
        }.toMutableList()

        customIconAdapter.icons.clear()
        customIconAdapter.icons.addAll(icons)
        customIconAdapter.notifyDataSetChanged()
        rvCustomIcons.adapter = customIconAdapter
        rvCustomIcons.visibility = if (icons.isNotEmpty()) View.VISIBLE else View.GONE
        if (customIconPath != null) {
            customIconAdapter.setSelectedIcon(customIconPath!!)
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            val imageUri: Uri = data.data ?: return

            try {
                val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // 缩放图片到合适大小
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                    
                    // 生成UUID文件名
                    val uuid = java.util.UUID.randomUUID().toString()
                    val filename = "custom_icon_$uuid.png"
                    
                    // 保存到应用内部目录
                    val file = File(filesDir, filename)
                    java.io.FileOutputStream(file).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    
                    // 保存文件路径
                    customIconPath = file.absolutePath
                    
                    // 保存到SharedPreferences
                    getSharedPreferences("floating_prefs", Context.MODE_PRIVATE).edit()
                        .putString("custom_icon_path", file.absolutePath)
                        .apply()
                    
                    ivCustomIcon.setImageBitmap(scaledBitmap)
                    ivCustomIcon.visibility = ImageView.VISIBLE
                    Toast.makeText(this, "图片已保存: ${file.name}", Toast.LENGTH_SHORT).show()
                    loadCustomIcons()
                    updateAmCommand()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "选择图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 可选：在Activity销毁时隐藏悬浮窗
        // FloatingWindowManager.stopFloatingWindow(this)
    }
}