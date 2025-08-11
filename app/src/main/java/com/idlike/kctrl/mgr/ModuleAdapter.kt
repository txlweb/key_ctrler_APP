package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

data class ModuleItem(
    val name: String,
    val description: String,
    var isEnabled: Boolean
)

class ModuleAdapter(
    private val modules: MutableList<ModuleItem>,
    private val onItemAction: (ModuleItem, String) -> Unit
) : RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder>() {

    class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val moduleName: TextView = itemView.findViewById(R.id.tv_module_name)
        val moduleDescription: TextView = itemView.findViewById(R.id.tv_module_description)
        val moduleStatus: TextView = itemView.findViewById(R.id.tv_module_status)
        val configButton: MaterialButton = itemView.findViewById(R.id.btn_module_config)
        val toggleButton: MaterialButton = itemView.findViewById(R.id.btn_module_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val module = modules[position]
        
        holder.moduleName.text = module.name
        holder.moduleDescription.text = module.description
        holder.moduleStatus.text = if (module.isEnabled) "已启用" else "已禁用"
        holder.toggleButton.text = if (module.isEnabled) "禁用" else "启用"
        
        holder.configButton.setOnClickListener {
            onItemAction(module, "config")
        }
        
        holder.toggleButton.setOnClickListener {
            onItemAction(module, "toggle")
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = modules.size
    
    fun addModule(module: ModuleItem) {
        modules.add(module)
        notifyItemInserted(modules.size - 1)
    }
    
    fun removeModule(position: Int) {
        if (position in 0 until modules.size) {
            modules.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}