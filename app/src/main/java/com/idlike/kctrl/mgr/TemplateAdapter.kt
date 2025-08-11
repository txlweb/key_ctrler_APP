package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                (holder as GroupViewHolder).bind(item.group)
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
    
    private inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivExpandIcon: ImageView = itemView.findViewById(R.id.ivExpandIcon)
        private val tvGroupTitle: TextView = itemView.findViewById(R.id.tvGroupTitle)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)
        
        fun bind(group: ScriptEditActivity.TemplateGroup) {
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
    
    private inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTemplateLabel: TextView = itemView.findViewById(R.id.tvTemplateLabel)
        private val tvTemplateCommand: TextView = itemView.findViewById(R.id.tvTemplateCommand)
        
        fun bind(item: ScriptEditActivity.TemplateItem) {
            tvTemplateLabel.text = item.label
            tvTemplateCommand.text = item.command
            
            itemView.setOnClickListener {
                onItemClick(item.command)
            }
        }
    }
    
    // 密封类用于区分不同类型的适配器项
    sealed class TemplateAdapterItem {
        data class GroupItem(val group: ScriptEditActivity.TemplateGroup) : TemplateAdapterItem()
        data class ItemItem(val item: ScriptEditActivity.TemplateItem) : TemplateAdapterItem()
    }
}