package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

data class FunctionItem(
    val name: String,
    var isEnabled: Boolean,
    val onToggle: (Boolean) -> Unit
)

class FunctionAdapter(private val functions: List<FunctionItem>) :
    RecyclerView.Adapter<FunctionAdapter.FunctionViewHolder>() {

    class FunctionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val functionName: TextView = itemView.findViewById(R.id.tv_function_name)
        val functionSwitch: SwitchMaterial = itemView.findViewById(R.id.switch_function)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FunctionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_function_switch, parent, false)
        return FunctionViewHolder(view)
    }

    override fun onBindViewHolder(holder: FunctionViewHolder, position: Int) {
        val function = functions[position]
        
        holder.functionName.text = function.name
        holder.functionSwitch.isChecked = function.isEnabled
        
        holder.functionSwitch.setOnCheckedChangeListener { _, isChecked ->
            function.isEnabled = isChecked
            function.onToggle(isChecked)
        }
    }

    override fun getItemCount(): Int = functions.size
}