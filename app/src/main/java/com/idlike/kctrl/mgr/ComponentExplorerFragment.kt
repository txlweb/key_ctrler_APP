package com.idlike.kctrl.mgr

import android.content.pm.PackageManager


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

import kotlinx.coroutines.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.PopupMenu
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import androidx.recyclerview.widget.ItemTouchHelper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.GestureDetector

/**
 * 简易组件浏览器：扫描设备上所有 Activity / Service 并提供搜索与刷新功能。
 * 后续将引入折叠与滑动菜单，这里先保证功能可运行。
 */
class ComponentExplorerFragment : Fragment() {
    private val componentItems = mutableListOf<ComponentItem>()
    private lateinit var adapter: ComponentGroupAdapter
    private var swipedPosition: Int = -1
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val cacheFileName = "components_cache.json"

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 判断是否具备 root，用于后续扩展
    private fun hasRoot(): Boolean {
        return try {
            Runtime.getRuntime().exec("su").destroy()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_component_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        val fabScrollToTop = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabScrollToTop)

        adapter = ComponentGroupAdapter(mutableListOf()) { item, anchor ->
            showComponentMenu(item, anchor)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // 取消滑动菜单，使用下拉刷新和更多按钮操作

        // 快速加载本地缓存
        loadComponentsFromCache()

        swipeRefreshLayout.setOnRefreshListener { scanComponents() }
        fabScrollToTop.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }



        scanComponents()
    }

    private var currentFilter: FilterType = FilterType.ALL

    enum class FilterType {
        ALL, USER, SYSTEM
    }

    fun setFilterType(filterType: FilterType) {
        currentFilter = filterType
        filterComponents() // 立即应用新的筛选条件
    }

    fun filterComponents(searchText: String = "") {
        val keywords = searchText.lowercase()
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        var filtered = componentItems

        // 按关键词筛选 - 支持空格分割的多关键词搜索
        if (keywords.isNotEmpty()) {
            filtered = filtered.filter { item ->
                val textToSearch = buildString {
                    append(item.label).append(" ")
                    append(item.componentName).append(" ")
                    append(item.appLabel).append(" ")
                    append(item.packageName)
                }.lowercase()
                
                // 所有关键词都必须匹配
                keywords.all { keyword ->
                    textToSearch.contains(keyword)
                }
            }.toMutableList()
        }

        // 按应用类型筛选
        filtered = when (currentFilter) {
            FilterType.ALL -> filtered
            FilterType.USER -> filtered.filter { !isSystemApp(it.packageName) }.toMutableList()
            FilterType.SYSTEM -> filtered.filter { isSystemApp(it.packageName) }.toMutableList()
        }

        adapter.submitItems(filtered, keywords)
        
        // 显示搜索结果提示
        if (filtered.isEmpty() && componentItems.isNotEmpty()) {
            Toast.makeText(requireContext(), "未找到匹配的组件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val pm = requireContext().packageManager
            val appInfo = pm.getPackageInfo(packageName, 0)?.applicationInfo
            appInfo?.let { 
                (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun scanComponents() {
        swipeRefreshLayout.isRefreshing = true
        coroutineScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    fetchAllComponents()
                } catch (e: SecurityException) {
                    // QUERY_ALL_PACKAGES 未授权或低版本兼容
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "权限不足，无法扫描所有应用，仅显示本应用组件", Toast.LENGTH_LONG).show()
                    }
                    fetchAllComponents(selfOnly = true)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "扫描失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    emptyList()
                }
            }
            componentItems.clear()
            componentItems.addAll(list)
            adapter.submitItems(componentItems.toList())
            // 保存缓存
            saveComponentsToCache(componentItems)
            swipeRefreshLayout.isRefreshing = false
            if (componentItems.isEmpty()) {
                Toast.makeText(requireContext(), "未找到可用组件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchAllComponents(selfOnly: Boolean = false): List<ComponentItem> {
        val pm = requireContext().packageManager
        val result = mutableListOf<ComponentItem>()
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
        val packages = if (selfOnly) {
            listOf(pm.getPackageInfo(requireContext().packageName, flags))
        } else {
            pm.getInstalledPackages(flags)
        }
        for (pkg in packages) {
            val appLabel = pkg.applicationInfo?.loadLabel(pm).toString()
            val packageName = pkg.packageName
            pkg.activities?.forEach { act ->
                result.add(ComponentItem(appLabel, packageName, "Activity", act.name))
            }
            pkg.services?.forEach { svc ->
                result.add(ComponentItem(appLabel, packageName, "Service", svc.name))
            }
        }
        return result.sortedWith(
            compareBy<ComponentItem> { it.appLabel.lowercase() }
                .thenBy { it.type }
                .thenBy { it.componentName }
        )
    }

    private fun copyAmCommand(item: ComponentItem) {
        val cmd = generateAmCommand(item)
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("am", cmd))
        Toast.makeText(requireContext(), "已复制: $cmd", Toast.LENGTH_SHORT).show()
    }

    private fun testRunComponent(item: ComponentItem) {
        try {
            val mainActivity = activity as? MainActivity
            if (mainActivity == null) {
                // 如果不是MainActivity，尝试直接执行root命令
                executeRootCommandDirectly(item)
                return
            }
            
            if (!mainActivity.hasSuPermission()) {
                Toast.makeText(requireContext(), "无Root权限，无法测试启动", Toast.LENGTH_SHORT).show()
                return
            }
            
            val command = if (item.type == "Activity") {
                "su -c am start -n ${item.packageName}/${item.componentName}"
            } else {
                "su -c am startservice -n ${item.packageName}/${item.componentName}"
            }
            
            Toast.makeText(requireContext(), "正在通过Root启动 ${item.simpleName}", Toast.LENGTH_SHORT).show()
            
            // 在后台线程执行root命令
            Thread {
                val result = mainActivity.executeRootCommand(command)
                activity?.runOnUiThread {
                    if (result != null) {
                        if (result.contains("ERROR:")) {
                            Toast.makeText(requireContext(), "启动失败: ${result.replace("ERROR:", "").trim()}", Toast.LENGTH_LONG).show()
                        } else {
                            val successMsg = if (item.type == "Activity") {
                                "Activity启动成功: ${item.simpleName}"
                            } else {
                                "Service启动成功: ${item.simpleName}"
                            }
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "启动命令执行失败 - 无Root权限或命令错误", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeRootCommandDirectly(item: ComponentItem) {
        val command = if (item.type == "Activity") {
            "su -c am start -n ${item.packageName}/${item.componentName}"
        } else {
            "su -c am startservice -n ${item.packageName}/${item.componentName}"
        }
        
        Toast.makeText(requireContext(), "正在通过Root启动 ${item.simpleName}", Toast.LENGTH_SHORT).show()
        
        // 直接执行root命令
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val outputStream = process.outputStream
                outputStream.write((command + "\n").toByteArray())
                outputStream.flush()
                outputStream.close()
                
                val exitCode = process.waitFor()
                activity?.runOnUiThread {
                    if (exitCode == 0) {
                        val successMsg = if (item.type == "Activity") {
                            "Activity启动成功: ${item.simpleName}"
                        } else {
                            "Service启动成功: ${item.simpleName}"
                        }
                        Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "启动失败 - 无Root权限或命令错误", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showComponentMenu(item: ComponentItem, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, "复制命令")
        popup.menu.add(0, 1, 1, "测试启动")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> {
                    copyAmCommand(item)
                    true
                }
                1 -> {
                    testRunComponent(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }





    private fun generateAmCommand(item: ComponentItem): String {
        return if (item.type == "Activity") {
            "am start -n ${item.packageName}/${item.componentName}"
        } else {
            "am startservice -n ${item.packageName}/${item.componentName}"
        }
    }

    /** 读取本地缓存 */
    private fun loadComponentsFromCache() {
        try {
            val file = File(requireContext().filesDir, cacheFileName)
            if (!file.exists()) return
            val text = file.readText()
            if (text.isBlank()) return
            val arr = JSONArray(text)
            val temp = mutableListOf<ComponentItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                temp.add(
                    ComponentItem(
                        obj.getString("appLabel"),
                        obj.getString("packageName"),
                        obj.getString("type"),
                        obj.getString("componentName")
                    )
                )
            }
            componentItems.clear()
            componentItems.addAll(temp)
            adapter.submitItems(componentItems.toList())
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }

    /** 保存本地缓存 */
    private fun saveComponentsToCache(list: List<ComponentItem>) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                list.forEach {
                    val obj = JSONObject()
                    obj.put("appLabel", it.appLabel)
                    obj.put("packageName", it.packageName)
                    obj.put("type", it.type)
                    obj.put("componentName", it.componentName)
                    arr.put(obj)
                }
                val file = File(requireContext().filesDir, cacheFileName)
                file.writeText(arr.toString())
            } catch (_: Exception) {
            }
        }
    }

}
    
// 数据类
data class ComponentItem(
    val appLabel: String,
    val packageName: String,
    val type: String,          // Activity / Service
    val componentName: String,
    val label: String = componentName.substringAfterLast('.'),
    val simpleName: String = componentName.substringAfterLast('.')
)

sealed class ComponentGroup {
    data class AppGroup(val appLabel: String, val packageName: String) : ComponentGroup()
    data class TypeGroup(val appLabel: String, val type: String) : ComponentGroup()
    data class Item(val item: ComponentItem) : ComponentGroup()
}