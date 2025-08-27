package com.idlike.kctrl.mgr

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ScriptItemAdapter(
    private val items: MutableList<ScriptEditActivity.ScriptItem>,
    private val onDeleteClick: (Int) -> Unit,
    private val commandToLabelMap: Map<String, String> = emptyMap(),
    private val onContentChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<ScriptItemAdapter.ScriptItemViewHolder>() {
    
    private fun findTemplateLabel(command: String): String? {
        // 调试信息
        Log.d("ScriptItemAdapter", "查找命令 '$command' 的模板标签，映射表大小: ${commandToLabelMap.size}")
        
        // 首先尝试精确匹配
        val exactMatch = commandToLabelMap[command]
        if (exactMatch != null) {
            Log.d("ScriptItemAdapter", "精确匹配找到: $exactMatch")
            return exactMatch
        }
        
        // 如果精确匹配失败，尝试去除首尾空白字符后匹配
        val trimmedCommand = command.trim()
        val trimmedMatch = commandToLabelMap[trimmedCommand]
        if (trimmedMatch != null) {
            Log.d("ScriptItemAdapter", "去空白匹配找到: $trimmedMatch")
            return trimmedMatch
        }
        
        // 如果还是没有匹配，尝试在映射表中查找包含该命令的条目
        for ((mapCommand, label) in commandToLabelMap) {
            if (mapCommand.trim() == trimmedCommand) {
                Log.d("ScriptItemAdapter", "遍历匹配找到: $label")
                return label
            }
        }
        
        Log.d("ScriptItemAdapter", "未找到匹配的模板标签")
        return null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script_line, parent, false)
        return ScriptItemViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ScriptItemViewHolder, position: Int) {
        holder.bind(items[position], position)
    }
    
    override fun getItemCount(): Int = items.size
    
    inner class ScriptItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDragHandle: ImageView = itemView.findViewById(R.id.ivDragHandle)
        private val etScriptContent: TextInputEditText = itemView.findViewById(R.id.etScriptContent)
        private val textInputLayout: TextInputLayout = etScriptContent.parent.parent as TextInputLayout
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        
        private var textWatcher: TextWatcher? = null
        
        fun bind(item: ScriptEditActivity.ScriptItem, position: Int) {
            Log.d("ScriptItemAdapter", "bind方法被调用，位置: $position, 内容: '${item.content}', 模板标签: ${item.templateLabel}")
            
            // 移除之前的TextWatcher
            textWatcher?.let { 
                etScriptContent.removeTextChangedListener(it)
                Log.d("ScriptItemAdapter", "移除了之前的TextWatcher")
            }
            
            // 设置文本内容 - 直接使用content
            etScriptContent.setText(item.content)
            
            // 根据item类型设置hint文本
            val hintText = when {
                item.isComment && !item.content.trim().startsWith("#!") -> {
                    Log.d("ScriptItemAdapter", "检测到注释行，内容: '${item.content}'")
                    "注释"
                }
                item.templateLabel != null -> "[${item.templateLabel}]"
                item.content.isNotEmpty() && item.templateLabel == null -> "自定义命令"
                else -> "输入脚本命令"
            }
            textInputLayout.hint = hintText
            Log.d("ScriptItemAdapter", "设置初始hint为: $hintText, isComment: ${item.isComment}, templateLabel: ${item.templateLabel}")
            
            // 创建新的TextWatcher
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    Log.d("ScriptItemAdapter", "TextWatcher afterTextChanged 被触发")
                    val currentPosition = adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION && currentPosition < items.size) {
                        val newText = s?.toString() ?: ""
                        Log.d("ScriptItemAdapter", "位置 $currentPosition 文本变更为: '$newText'")
                        
                        // 更新content
                        items[currentPosition].content = newText
                        
                        // 更新item的状态
                        if (newText.trim().startsWith("#") && !newText.trim().startsWith("#!")) {
                            Log.d("ScriptItemAdapter", "识别为注释行")
                            items[currentPosition].isComment = true
                            items[currentPosition].templateLabel = null
                        } else {
                            items[currentPosition].isComment = false
                            
                            // 动态查找命令对应的模板名称
                            Log.d("ScriptItemAdapter", "开始查找模板标签")
                            items[currentPosition].templateLabel = findTemplateLabel(newText.trim())
                        }
                        
                        // 更新hint文本
                        val newHintText = when {
                            items[currentPosition].isComment -> "注释"
                            items[currentPosition].templateLabel != null -> "[${items[currentPosition].templateLabel}]"
                            newText.isNotEmpty() && items[currentPosition].templateLabel == null -> "自定义命令"
                            else -> "输入脚本命令"
                        }  
                        Log.d("ScriptItemAdapter", "更新hint为: $newHintText")
                        textInputLayout.hint = newHintText
                        
                        // 通知内容变化
                        onContentChanged?.invoke()
                    } else {
                        Log.w("ScriptItemAdapter", "无效的位置: $currentPosition, items大小: ${items.size}")
                    }
                }
            }
            
            // 添加TextWatcher
            etScriptContent.addTextChangedListener(textWatcher)
            Log.d("ScriptItemAdapter", "TextWatcher已添加到EditText")
            
            // 设置焦点监听器，只在必要时滚动到可见位置
            etScriptContent.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val currentPosition = adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        // 延迟滚动，确保键盘已经弹出
                        itemView.postDelayed({
                            (itemView.parent as? RecyclerView)?.let { recyclerView ->
                                val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                layoutManager?.let { lm ->
                                    // 检查当前项是否已经在可见区域内
                                    val firstVisible = lm.findFirstVisibleItemPosition()
                                    val lastVisible = lm.findLastVisibleItemPosition()
                                    
                                    // 只有当当前项不在可见范围内时才滚动
                                    if (currentPosition < firstVisible || currentPosition > lastVisible) {
                                        val offset = recyclerView.height / 3
                                        lm.scrollToPositionWithOffset(currentPosition, offset)
                                    }
                                }
                            }
                        }, 100)
                    }
                }
            }
            
            // 设置删除按钮点击事件
            btnDelete.setOnClickListener {
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(currentPosition)
                }
            }
            
            // 拖动手柄可以用于ItemTouchHelper识别
            ivDragHandle.setOnTouchListener { _, _ ->
                // 这里可以添加拖动开始的逻辑
                false
            }
        }
    }
}