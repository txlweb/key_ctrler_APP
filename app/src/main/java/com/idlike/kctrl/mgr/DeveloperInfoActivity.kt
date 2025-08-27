package com.idlike.kctrl.mgr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView

class DeveloperInfoActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var ivDeveloperAvatar: ShapeableImageView
    private lateinit var tvDeveloperName: MaterialTextView
    private lateinit var tvDeveloperDescription: MaterialTextView
    private lateinit var tvFinalAuthor: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_info)

        // 初始化视图
        toolbar = findViewById(R.id.toolbar)
        ivDeveloperAvatar = findViewById(R.id.ivDeveloperAvatar)
        tvDeveloperName = findViewById(R.id.tvDeveloperName)
        tvDeveloperDescription = findViewById(R.id.tvDeveloperDescription)

        // 设置状态栏颜色
        window.statusBarColor = ContextCompat.getColor(this, R.color.dynamic_primary)

        // 设置返回按钮
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 页面内容直接显示，无动画
    }
}