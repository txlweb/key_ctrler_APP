package com.idlike.kctrl.mgr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

class FloatingWindowManager {
    
    companion object {
        const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
        
        /**
         * 检查是否有悬浮窗权限
         */
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        /**
         * 请求悬浮窗权限
         */
        fun requestOverlayPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(activity)) {
                    showPermissionDialog(activity)
                }
            }
        }
        
        /**
         * 显示权限申请对话框
         */
        private fun showPermissionDialog(activity: Activity) {
            AlertDialog.Builder(activity)
                .setTitle("悬浮窗权限")
                .setMessage("为了显示悬浮窗，需要授予悬浮窗权限。这将允许应用在其他应用上方显示内容，包括在锁屏和设置界面。")
                .setPositiveButton("去设置") { _, _ ->
                    openOverlaySettings(activity)
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        /**
         * 打开悬浮窗权限设置页面
         */
        private fun openOverlaySettings(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
            }
        }
        
        /**
         * 启动悬浮窗服务
         */
        fun startFloatingWindow(context: Context, text: String = "悬浮窗", iconId: Int = R.drawable.ic_config, widthDp: Int = 160, x: Int = -1, y: Int = -1, customIconPath: String? = null) {
            if (hasOverlayPermission(context)) {
                val intent = Intent(context, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_SHOW
                    putExtra(FloatingWindowService.EXTRA_TEXT, text)
                    putExtra(FloatingWindowService.EXTRA_ICON_ID, iconId)
                    putExtra(FloatingWindowService.EXTRA_WIDTH, widthDp)
                    putExtra(FloatingWindowService.EXTRA_X, x)
                    putExtra(FloatingWindowService.EXTRA_Y, y)
                    customIconPath?.let { putExtra(FloatingWindowService.EXTRA_CUSTOM_ICON_PATH, it) }
                }
                context.startForegroundService(intent)
            }
        }
        
        /**
         * 隐藏悬浮窗
         */
        fun hideFloatingWindow(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_HIDE
            }
            context.startService(intent)
        }
        
        /**
         * 更新悬浮窗内容
         */
        fun updateFloatingWindow(context: Context, text: String, iconId: Int = R.drawable.ic_config, widthDp: Int = 160, x: Int = -1, y: Int = -1, customIconPath: String? = null) {
            if (hasOverlayPermission(context)) {
                // 先隐藏悬浮窗
                val hideIntent = Intent(context, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_HIDE
                }
                context.startService(hideIntent)
                
                // 立即重新显示悬浮窗，确保更新生效
                val showIntent = Intent(context, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_SHOW
                    putExtra(FloatingWindowService.EXTRA_TEXT, text)
                    putExtra(FloatingWindowService.EXTRA_ICON_ID, iconId)
                    putExtra(FloatingWindowService.EXTRA_WIDTH, widthDp)
                    putExtra(FloatingWindowService.EXTRA_X, x)
                    putExtra(FloatingWindowService.EXTRA_Y, y)
                    customIconPath?.let { putExtra(FloatingWindowService.EXTRA_CUSTOM_ICON_PATH, it) }
                }
                context.startForegroundService(showIntent)
            }
        }
        
        /**
         * 停止悬浮窗服务
         */
        fun stopFloatingWindow(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
}