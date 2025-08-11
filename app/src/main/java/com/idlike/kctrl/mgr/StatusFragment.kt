package com.idlike.kctrl.mgr

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import java.io.File

class StatusFragment : Fragment() {
    private lateinit var tvStatus: TextView
    private lateinit var tvPid: TextView
    private lateinit var statusIndicator: View
    private lateinit var statusCard: MaterialCardView
    private lateinit var btnRefresh: com.google.android.material.button.MaterialButton
    private lateinit var btnRestart: com.google.android.material.button.MaterialButton
    private lateinit var btnShowFloating: com.google.android.material.button.MaterialButton
    private lateinit var btnHideFloating: com.google.android.material.button.MaterialButton
    private lateinit var btnFloatingDemo: com.google.android.material.button.MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        updateStatus()
    }

    private fun initViews(view: View) {
        tvStatus = view.findViewById(R.id.tv_status)
        tvPid = view.findViewById(R.id.tv_pid)
        statusIndicator = view.findViewById(R.id.status_indicator)
        statusCard = view.findViewById(R.id.status_card)
        btnRefresh = view.findViewById(R.id.btn_refresh)
        btnRestart = view.findViewById(R.id.btn_restart)
        btnShowFloating = view.findViewById(R.id.btn_show_floating)
        btnHideFloating = view.findViewById(R.id.btn_hide_floating)
        btnFloatingDemo = view.findViewById(R.id.btn_floating_demo)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        btnRefresh.setOnClickListener {
            updateStatus()
        }
        
        btnRestart.setOnClickListener {
            restartKctrl()
        }
        
        btnShowFloating.setOnClickListener {
            showFloatingWindow()
        }
        
        btnHideFloating.setOnClickListener {
            hideFloatingWindow()
        }
        
        btnFloatingDemo.setOnClickListener {
            openFloatingDemo()
        }
    }

    private fun updateStatus() {
        val mainActivity = activity as? MainActivity ?: return
        
        // 异步检查权限和状态，避免阻塞UI
        Thread {
            // 等待权限检查完成，最多等待3秒
            var permissionCheckRetries = 0
            val maxRetries = 30 // 3秒，每次等待100ms
            
            while (!mainActivity.hasSuPermission() && permissionCheckRetries < maxRetries) {
                Thread.sleep(100)
                permissionCheckRetries++
            }
            
            val hasSu = mainActivity.hasSuPermission()
            val status = if (hasSu) mainActivity.checkKctrlStatus() else null
            
            // 在主线程更新UI
            activity?.runOnUiThread {
                if (!hasSu) {
                    // 无su权限
                    tvStatus.text = "无法获取"
                    tvPid.text = "需要Root权限"
                    statusIndicator.setBackgroundResource(R.drawable.status_stopped)
                    statusCard.setCardBackgroundColor(
                        resources.getColor(android.R.color.holo_red_light, null)
                    )
                    // 禁用重启按钮
                    btnRestart.isEnabled = false
                    btnRestart.alpha = 0.5f
                } else {
                    // 启用重启按钮
                    btnRestart.isEnabled = true
                    btnRestart.alpha = 1.0f
                    
                    if (status != null && status.isNotEmpty()) {
                        // KCTRL 正在运行（已验证进程存在）
                        tvStatus.text = "正在运行"
                        tvPid.text = "PID: $status (已验证)"
                        statusIndicator.setBackgroundResource(R.drawable.status_running)
                        statusCard.setCardBackgroundColor(
                            resources.getColor(android.R.color.holo_green_light, null)
                        )
                    } else {
                        // KCTRL 未运行或进程已停止
                        tvStatus.text = "未运行"
                        tvPid.text = "PID: 无"
                        statusIndicator.setBackgroundResource(R.drawable.status_stopped)
                        statusCard.setCardBackgroundColor(
                            resources.getColor(android.R.color.holo_red_light, null)
                        )
                    }
                    
                    // 更新MainActivity的服务状态
                    mainActivity.updateServiceStatus()
                }
            }
        }.start()
    }

    private fun restartKctrl() {
        val mainActivity = activity as? MainActivity ?: return
        
        // 检查su权限
        if (!mainActivity.hasSuPermission()) {
            context?.let {
                    android.widget.Toast.makeText(it, "无Root权限，无法重启服务", android.widget.Toast.LENGTH_SHORT).show()
                }
            return
        }
        
        // 显示重启中的提示
        context?.let {
                    android.widget.Toast.makeText(it, "正在重启服务...", android.widget.Toast.LENGTH_SHORT).show()
                }
        
        // 禁用重启按钮，防止重复点击
        btnRestart.isEnabled = false
        btnRestart.text = "重启中..."
        
        // 异步执行重启操作
        Thread {
            val success = mainActivity.restartKctrlService()
            
            // 在主线程更新UI
            activity?.runOnUiThread {
                btnRestart.isEnabled = true
                btnRestart.text = "重启服务"
                
                if (success) {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务重启成功", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    context?.let {
                        android.widget.Toast.makeText(it, "服务重启失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                // 重启后更新状态显示
                updateStatus()
                // 更新MainActivity的服务状态
                mainActivity.updateServiceStatus()
            }
        }.start()
    }
    
    private fun showFloatingWindow() {
        val mainActivity = activity as? MainActivity ?: return
        
        if (!mainActivity.hasOverlayPermission()) {
            FloatingWindowManager.requestOverlayPermission(mainActivity)
            return
        }
        
        // 显示悬浮窗，可以自定义文本和图标
        mainActivity.startFloatingWindow("KCtrl 控制器", R.drawable.ic_config)
        
        context?.let {
            android.widget.Toast.makeText(it, "悬浮窗已显示", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideFloatingWindow() {
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.hideFloatingWindow()
        
        context?.let {
            android.widget.Toast.makeText(it, "悬浮窗已隐藏", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFloatingDemo() {
        val intent = Intent(activity, FloatingWindowDemoActivity::class.java)
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}