package com.idlike.kctrl.mgr

import android.animation.ValueAnimator
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ComponentGroupAdapter(
    val groups: MutableList<ComponentGroup>,
    private val onItemAction: (ComponentItem, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val expanded = mutableSetOf<String>()
    private var originalItems: List<ComponentItem> = emptyList()
    private var keywords: List<String> = emptyList()

    companion object {
        private const val TYPE_APP = 0
        private const val TYPE_TYPE = 1
        private const val TYPE_ITEM = 2
    }

    override fun getItemViewType(position: Int): Int = when (groups[position]) {
        is ComponentGroup.AppGroup -> TYPE_APP
        is ComponentGroup.TypeGroup -> TYPE_TYPE
        is ComponentGroup.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_APP -> AppVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false))
            TYPE_TYPE -> TypeVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_type, parent, false))
            TYPE_ITEM -> ItemVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_component, parent, false))
            else -> throw IllegalArgumentException("Unknown viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val group = groups[position]) {
            is ComponentGroup.AppGroup -> (holder as AppVH).bind(group)
            is ComponentGroup.TypeGroup -> (holder as TypeVH).bind(group)
            is ComponentGroup.Item -> (holder as ItemVH).bind(group.item)
        }
    }

    override fun getItemCount(): Int = groups.size

    fun submitList(newList: List<ComponentGroup>) {
        groups.clear()
        groups.addAll(newList)
        notifyDataSetChanged()
    }

    private fun buildKey(appLabel: String, type: String? = null): String =
        if (type == null) appLabel else "$appLabel:$type"

    private fun highlightKeywords(text: String, keywords: List<String>): CharSequence {
        if (keywords.isEmpty()) return text
        
        val spannable = SpannableString(text)
        val lowerText = text.lowercase()
        
        keywords.forEach { keyword ->
            if (keyword.isBlank()) return@forEach
            
            var startIndex = 0
            while (startIndex < lowerText.length) {
                val index = lowerText.indexOf(keyword, startIndex)
                if (index == -1) break
                
                // 设置背景高亮（黄色背景）
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#FFEB3B")),
                    index,
                    index + keyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // 设置文字颜色（深色文字）
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#000000")),
                    index,
                    index + keyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                startIndex = index + keyword.length
            }
        }
        
        return spannable
    }

    inner class AppVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAppLabel: TextView = itemView.findViewById(R.id.tv_app_label)
        private val tvPackageName: TextView = itemView.findViewById(R.id.tv_package_name)
        private val ivArrow: ImageView = itemView.findViewById(R.id.iv_arrow)
        private val ivAppIcon: ImageView = itemView.findViewById(R.id.iv_app_icon)
        
        fun bind(app: ComponentGroup.AppGroup) {
            tvAppLabel.text = highlightKeywords(app.appLabel, keywords)
            tvPackageName.text = highlightKeywords(app.packageName, keywords)
            
            // 加载应用图标
            try {
                val packageManager = itemView.context.packageManager
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                val icon = packageManager.getApplicationIcon(appInfo)
                ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                // 如果获取图标失败，使用默认图标
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            
            val key = buildKey(app.appLabel)
            val isExpanded = expanded.contains(key)
            rotateArrow(ivArrow, isExpanded)
            itemView.setOnClickListener {
                toggleApp(app.appLabel)
            }
        }
    }

    inner class TypeVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType: TextView = itemView.findViewById(R.id.tv_type)
        private val ivArrow: ImageView = itemView.findViewById(R.id.iv_arrow)
        fun bind(type: ComponentGroup.TypeGroup) {
            val typeText = when (type.type) {
                "Activity" -> "页面"
                "Service" -> "服务"
                else -> type.type
            }
            tvType.text = highlightKeywords(typeText, keywords)
            val key = buildKey(type.appLabel, type.type)
            val isExpanded = expanded.contains(key)
            rotateArrow(ivArrow, isExpanded)
            itemView.setOnClickListener {
                toggleType(type.appLabel, type.type)
            }
        }
    }

    inner class ItemVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAppLabel: TextView = itemView.findViewById(R.id.tv_app_label)
        private val tvComponentName: TextView = itemView.findViewById(R.id.tv_component_name)
        private val tvPackageName: TextView = itemView.findViewById(R.id.tv_package_name)
        private val tvType: TextView = itemView.findViewById(R.id.tv_type)
        private val ivMore: ImageView = itemView.findViewById(R.id.iv_more)
        fun bind(item: ComponentItem) {
            tvAppLabel.text = highlightKeywords(item.appLabel, keywords)
            tvComponentName.text = highlightKeywords(item.simpleName, keywords)
            tvPackageName.text = highlightKeywords(item.packageName, keywords)
            tvType.text = highlightKeywords(when (item.type) {
                "Activity" -> "页面"
                "Service" -> "服务"
                else -> item.type
            }, keywords)
            ivMore.setOnClickListener { onItemAction(item, ivMore) }
        }
    }

    private fun rotateArrow(iv: ImageView, expanded: Boolean) {
        val target = if (expanded) 180f else 0f
        ValueAnimator.ofFloat(iv.rotation, target).apply {
            duration = 200
            addUpdateListener { iv.rotation = it.animatedValue as Float }
        }.start()
    }

    private fun toggleApp(appLabel: String) {
        val key = buildKey(appLabel)
        val hasChildren = originalItems.any { it.appLabel == appLabel }
        if (!hasChildren) return
        
        // 单应用展开：折叠其他应用，保留当前应用状态
        if (expanded.contains(key)) {
            expanded.remove(key)
            // 同时移除该应用下的所有类型展开
            expanded.removeAll { it.startsWith("$appLabel:") }
        } else {
            // 清除所有应用和类型的展开
            expanded.clear()
            expanded.add(key)
        }
        rebuild()
    }

    private fun toggleType(appLabel: String, type: String) {
        val key = buildKey(appLabel, type)
        val hasChildren = originalItems.any { it.appLabel == appLabel && it.type == type }
        if (!hasChildren) return
        
        // 单类型展开：在同一应用内，只展开一个类型
        val appKey = buildKey(appLabel)
        if (expanded.contains(key)) {
            expanded.remove(key)
        } else {
            // 清除该应用下的所有类型展开，但保留应用本身的展开
            expanded.removeAll { it.startsWith("$appLabel:") }
            expanded.add(key)
        }
        rebuild()
    }

    private fun rebuild() {
        val flat = mutableListOf<ComponentGroup>()
        originalItems.groupBy { it.appLabel }.forEach { (appLabel, appItems) ->
            val packageName = appItems.firstOrNull()?.packageName ?: ""
            flat.add(ComponentGroup.AppGroup(appLabel, packageName))
            if (expanded.contains(buildKey(appLabel))) {
                appItems.groupBy { it.type }.forEach { (type, typeItems) ->
                    flat.add(ComponentGroup.TypeGroup(appLabel, type))
                    if (expanded.contains(buildKey(appLabel, type))) {
                        typeItems.forEach { flat.add(ComponentGroup.Item(it)) }
                    }
                }
            }
        }
        groups.clear()
        groups.addAll(flat)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ComponentItem? {
        val group = groups.getOrNull(position)
        return when (group) {
            is ComponentGroup.Item -> group.item
            else -> null
        }
    }

    fun submitItems(items: List<ComponentItem>, keywords: List<String> = emptyList()) {
        this.keywords = keywords
        originalItems = items
        val flat = mutableListOf<ComponentGroup>()
        items.groupBy { it.appLabel }.forEach { (appLabel, appItems) ->
            val packageName = appItems.firstOrNull()?.packageName ?: ""
            flat.add(ComponentGroup.AppGroup(appLabel, packageName))
            if (expanded.contains(buildKey(appLabel))) {
                appItems.groupBy { it.type }.forEach { (type, typeItems) ->
                    flat.add(ComponentGroup.TypeGroup(appLabel, type))
                    if (expanded.contains(buildKey(appLabel, type))) {
                        typeItems.forEach { flat.add(ComponentGroup.Item(it)) }
                    }
                }
            }
        }
        groups.clear()
        groups.addAll(flat)
        notifyDataSetChanged()
    }
}