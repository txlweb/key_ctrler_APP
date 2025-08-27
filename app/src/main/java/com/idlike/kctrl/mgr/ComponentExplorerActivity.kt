package com.idlike.kctrl.mgr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.ChipGroup
import androidx.appcompat.widget.SearchView

/**
 * 页面与服务浏览Activity，包含搜索和筛选功能
 */
class ComponentExplorerActivity : AppCompatActivity() {
    
    private lateinit var searchView: SearchView
    private lateinit var chipGroup: ChipGroup
    private var fragment: ComponentExplorerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_component_explorer)

        // 设置返回按钮
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            finish()
        }

        // 初始化搜索和筛选控件
        searchView = findViewById(R.id.searchView)
        chipGroup = findViewById(R.id.chipGroup)

        // 加载 Fragment
        if (savedInstanceState == null) {
            fragment = ComponentExplorerFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment!!)
                .commit()
        } else {
            fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? ComponentExplorerFragment
        }
        
        // 确保Fragment事务完成后再设置搜索和筛选
        supportFragmentManager.executePendingTransactions()

        setupSearchAndFilter()
        
        // 默认展开搜索框
        searchView.isIconified = false
    }

    private fun setupSearchAndFilter() {
        // 文本搜索功能
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                fragment?.filterComponents(query ?: "")
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                fragment?.filterComponents(newText ?: "")
                return true
            }
        })

        // 筛选Chip组
        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            val filterType = when (checkedId) {
                R.id.chipUser -> ComponentExplorerFragment.FilterType.USER
                R.id.chipSystem -> ComponentExplorerFragment.FilterType.SYSTEM
                else -> ComponentExplorerFragment.FilterType.ALL
            }
            fragment?.setFilterType(filterType)
            fragment?.filterComponents(searchView.query?.toString() ?: "")
        }
    }
}