package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import androidx.core.content.ContextCompat

class DeviceAdapter(
    private val deviceList: List<DeviceItem>,
    private val onItemClick: (DeviceItem) -> Unit,
    private val onDeviceTypeToggle: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDevicePath: TextView = itemView.findViewById(R.id.tv_device_path)
        val tvDeviceName: TextView = itemView.findViewById(R.id.tv_device_name)
        val cbSelected: CheckBox = itemView.findViewById(R.id.cb_selected)
        val btnDevicePath: MaterialButton = itemView.findViewById(R.id.btn_device_path)
        val btnDeviceName: MaterialButton = itemView.findViewById(R.id.btn_device_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceList[position]
        
        // 检查是否有同名设备
        val sameNameDevices = deviceList.filter { it.name == device.name }
        val hasSameName = sameNameDevices.size > 1
        
        // 设置设备信息，如果有同名设备则添加标识
        if (hasSameName) {
            val deviceIndex = sameNameDevices.indexOf(device) + 1
            holder.tvDeviceName.text = "${device.name} (${deviceIndex}/${sameNameDevices.size})"
        } else {
            holder.tvDeviceName.text = device.name
        }
        holder.tvDevicePath.text = device.path
        
        // 临时移除监听器，避免在设置状态时触发回调
        holder.cbSelected.setOnCheckedChangeListener(null)
        holder.cbSelected.isChecked = device.isSelected
        
        // 设置按钮样式
        setButtonStyle(holder.btnDevicePath, !device.useDeviceName)
        setButtonStyle(holder.btnDeviceName, device.useDeviceName)
        
        // 设置按钮点击监听器
        holder.btnDevicePath.setOnClickListener {
            if (device.useDeviceName) {
                device.useDeviceName = false
                setButtonStyle(holder.btnDevicePath, true)
                setButtonStyle(holder.btnDeviceName, false)
                onDeviceTypeToggle(device)
            }
        }
        
        holder.btnDeviceName.setOnClickListener {
            if (!device.useDeviceName) {
                device.useDeviceName = true
                setButtonStyle(holder.btnDevicePath, false)
                setButtonStyle(holder.btnDeviceName, true)
                onDeviceTypeToggle(device)
            }
        }
        
        // 重新设置监听器
        holder.cbSelected.setOnCheckedChangeListener { _, _ ->
            onItemClick(device)
        }
        
        holder.itemView.setOnClickListener {
            holder.cbSelected.isChecked = !holder.cbSelected.isChecked
        }
    }
    
    private fun setButtonStyle(button: MaterialButton, isSelected: Boolean) {
        val context = button.context
        if (isSelected) {
            // 选中状态：使用实心按钮样式（类似导出配置）
            button.setBackgroundTintList(ContextCompat.getColorStateList(context, com.google.android.material.R.color.material_dynamic_primary70))
            button.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            button.strokeWidth = 0
        } else {
            // 非选中状态：使用轮廓按钮样式（类似导入配置）
            button.setBackgroundTintList(ContextCompat.getColorStateList(context, android.R.color.transparent))
            button.setTextColor(ContextCompat.getColorStateList(context, com.google.android.material.R.color.material_dynamic_primary70))
            button.strokeWidth = 2
            button.strokeColor = ContextCompat.getColorStateList(context, com.google.android.material.R.color.material_dynamic_primary70)
        }
    }

    override fun getItemCount(): Int = deviceList.size
}