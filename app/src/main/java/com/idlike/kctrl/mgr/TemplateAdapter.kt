package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TemplateAdapter(
    private val groups: List<ScriptEditActivity.TemplateGroup>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_ITEM = 1
    }
    
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
        // 调试信息
        println("TemplateAdapter: 更新显示项目，共 ${displayItems.size} 个项目")
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is TemplateAdapterItem.GroupItem -> TYPE_GROUP
            is TemplateAdapterItem.ItemItem -> TYPE_ITEM
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_template_group, parent, false)
                GroupViewHolder(view)
            }
            TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_template, parent, false)
                ItemViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is TemplateAdapterItem.GroupItem -> {
                (holder as GroupViewHolder).bind(item.group, position)
            }
            is TemplateAdapterItem.ItemItem -> {
                (holder as ItemViewHolder).bind(item.item)
            }
        }
    }
    
    override fun getItemCount(): Int = displayItems.size
    
    fun refreshData() {
        updateDisplayItems()
        notifyDataSetChanged()
    }
    
    fun toggleGroupWithAnimation(position: Int) {
        val item = displayItems[position]
        if (item is TemplateAdapterItem.GroupItem) {
            val group = item.group
            val wasExpanded = group.isExpanded
            
            // 如果当前分组未展开，先折叠所有其他已展开的分组
            if (!wasExpanded) {
                val previouslyExpandedGroups = mutableListOf<Pair<Int, Int>>()
                
                // 找出所有已展开的分组及其项目数量
                for (i in groups.indices) {
                    if (groups[i].isExpanded && groups[i] != group) {
                        val itemCount = groups[i].items.size
                        // 计算该分组在displayItems中的位置
                        var groupPosition = 0
                        for (j in 0 until i) {
                            groupPosition += 1 + if (groups[j].isExpanded) groups[j].items.size else 0
                        }
                        previouslyExpandedGroups.add(Pair(groupPosition, itemCount))
                        groups[i].isExpanded = false
                    }
                }
                
                // 先折叠所有其他分组，从后往前处理避免位置错乱
                previouslyExpandedGroups.sortedByDescending { it.first }
                for ((groupPos, itemCount) in previouslyExpandedGroups) {
                    if (itemCount > 0) {
                        notifyItemRangeRemoved(groupPos + 1, itemCount)
                    }
                }
            }
            
            // 切换当前分组的展开状态
            group.isExpanded = !wasExpanded

            if (group.isExpanded) {
                // 展开：先更新数据源，再插入项目
                val insertCount = group.items.size
                updateDisplayItems()
                if (insertCount > 0) {
                    notifyItemRangeInserted(position + 1, insertCount)
                }
                notifyItemChanged(position)           // 更新组标题和箭头
            } else {
                // 折叠：先移除项目，再更新数据源
                val removeCount = group.items.size
                if (removeCount > 0) {
                    notifyItemRangeRemoved(position + 1, removeCount)
                }
                updateDisplayItems()
                notifyItemChanged(position)           // 更新组标题和箭头
            }
        }
    }
    
    private inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivExpandIcon: ImageView = itemView.findViewById(R.id.ivExpandIcon)
        private val tvGroupTitle: TextView = itemView.findViewById(R.id.tvGroupTitle)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)
        
        fun bind(group: ScriptEditActivity.TemplateGroup, position: Int) {
            tvGroupTitle.text = group.title
            tvItemCount.text = group.items.size.toString()
            
            // 设置展开/折叠图标 - 使用流畅旋转动画
            val targetRotation = if (group.isExpanded) 180f else 0f
            ivExpandIcon.animate()
                .rotation(targetRotation)
                .setDuration(300)
                .start()
            
            itemView.setOnClickListener {
                val adapterPosition = adapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    // 先执行箭头旋转动画
                    val newRotation = if (group.isExpanded) 0f else 180f
                    ivExpandIcon.animate()
                        .rotation(newRotation)
                        .setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                    
                    // 然后执行展开/折叠动画
                    toggleGroupWithAnimation(adapterPosition)
                }
            }
        }
    }
    
    private inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTemplateLabel: TextView = itemView.findViewById(R.id.tvTemplateLabel)
        private val tvTemplateCommand: TextView = itemView.findViewById(R.id.tvTemplateCommand)
        
        fun bind(item: ScriptEditActivity.TemplateItem) {
            tvTemplateLabel.text = item.label
            tvTemplateCommand.text = item.command
            
            // 移除淡入动画，避免展开时显示空白
            itemView.alpha = 1f
            
            itemView.setOnClickListener {
                // 简单的点击动画
                it.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        onItemClick(item.command)
                    }
                    .start()
            }
        }
    }
    
    // 密封类用于区分不同类型的适配器项
    sealed class TemplateAdapterItem {
        data class GroupItem(val group: ScriptEditActivity.TemplateGroup) : TemplateAdapterItem()
        data class ItemItem(val item: ScriptEditActivity.TemplateItem) : TemplateAdapterItem()
    }
}