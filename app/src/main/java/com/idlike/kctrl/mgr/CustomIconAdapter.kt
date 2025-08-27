package com.idlike.kctrl.mgr

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class CustomIconAdapter(
    private val context: Context,
    val icons: MutableList<CustomIcon>,
    private val onIconSelected: (CustomIcon) -> Unit,
    private val onIconDeleted: (CustomIcon) -> Unit
) : RecyclerView.Adapter<CustomIconAdapter.IconViewHolder>() {

    private var selectedPosition: Int = -1

    data class CustomIcon(
        val filePath: String,
        val fileName: String
    )

    inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_icon)
        val tvFilename: TextView = itemView.findViewById(R.id.tv_filename)
        val tvPath: TextView = itemView.findViewById(R.id.tv_path)
        val rbSelected: RadioButton = itemView.findViewById(R.id.rb_selected)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousSelected = selectedPosition
                    selectedPosition = position
                    
                    // 更新选中状态
                    notifyItemChanged(previousSelected)
                    notifyItemChanged(selectedPosition)
                    
                    // 回调选择事件
                    onIconSelected(icons[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_custom_icon, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val icon = icons[position]
        
        holder.tvFilename.text = icon.fileName
        holder.tvPath.text = icon.filePath
        
        // 加载图片
        val file = File(icon.filePath)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(icon.filePath)
            if (bitmap != null) {
                holder.ivIcon.setImageBitmap(bitmap)
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
        }
        
        // 设置选中状态
        holder.rbSelected.isChecked = position == selectedPosition
    }

    override fun getItemCount(): Int = icons.size

    fun setSelectedIcon(filePath: String) {
        selectedPosition = icons.indexOfFirst { it.filePath == filePath }
        notifyDataSetChanged()
    }

    fun getIconAt(position: Int): CustomIcon {
        return icons[position]
    }

    fun removeIcon(position: Int) {
        icons.removeAt(position)
        notifyItemRemoved(position)
        if (selectedPosition >= icons.size) {
            selectedPosition = -1
        } else if (selectedPosition > position) {
            selectedPosition--
        }
    }
}