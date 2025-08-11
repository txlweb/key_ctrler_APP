package com.idlike.kctrl.mgr

import android.os.Bundle
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar

class FloatingWindowDemoActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var etCustomText: EditText
    private lateinit var spinnerIcon: Spinner
    private lateinit var btnShowCustom: MaterialButton
    private lateinit var btnHideFloating: MaterialButton
    private lateinit var btnUpdateFloating: MaterialButton
    
    // 图标选项
    private val iconOptions = arrayOf(
        "配置图标" to R.drawable.ic_config,
        "状态图标" to R.drawable.ic_status,
        "模块图标" to R.drawable.ic_module,
        "播放图标" to R.drawable.ic_play_arrow,
        "编辑图标" to R.drawable.ic_edit,
        "保存图标" to R.drawable.ic_save,
        "添加图标" to R.drawable.ic_add
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floating_window_demo)
        
        initViews()
        setupToolbar()
        setupSpinner()
        setupClickListeners()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etCustomText = findViewById(R.id.et_custom_text)
        spinnerIcon = findViewById(R.id.spinner_icon)
        btnShowCustom = findViewById(R.id.btn_show_custom)
        btnHideFloating = findViewById(R.id.btn_hide_floating)
        btnUpdateFloating = findViewById(R.id.btn_update_floating)
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
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            iconOptions.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerIcon.adapter = adapter
    }
    
    private fun setupClickListeners() {
        btnShowCustom.setOnClickListener {
            showCustomFloatingWindow()
        }
        
        btnHideFloating.setOnClickListener {
            FloatingWindowManager.hideFloatingWindow(this)
        }
        
        btnUpdateFloating.setOnClickListener {
            updateFloatingWindow()
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
        
        FloatingWindowManager.startFloatingWindow(this, customText, iconId)
    }
    
    private fun updateFloatingWindow() {
        val customText = etCustomText.text.toString().ifEmpty { "更新后的悬浮窗" }
        val selectedIconIndex = spinnerIcon.selectedItemPosition
        val iconId = iconOptions[selectedIconIndex].second
        
        FloatingWindowManager.updateFloatingWindow(this, customText, iconId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 可选：在Activity销毁时隐藏悬浮窗
        // FloatingWindowManager.stopFloatingWindow(this)
    }
}