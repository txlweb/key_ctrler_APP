package com.idlike.kctrl.mgr

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

class ExclusiveModeInfoActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exclusive_mode_info)
        
        // 设置标题
        supportActionBar?.title = "独占模式说明"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 显示作用机理信息
        findViewById<TextView>(R.id.tv_mechanism).text = HtmlCompat.fromHtml(
            """
            <h3>独占模式工作原理</h3>
            <p><strong>程序如何独占输入设备：</strong></p>
            <ol>
                <li><strong>设备发现阶段：</strong>程序扫描/dev/input/目录下的所有输入设备节点（如/dev/input/event0, event1等）</li>
                <li><strong>权限获取：</strong>使用root权限打开选中的输入设备节点</li>
                <li><strong>独占锁定：</strong>通过系统调用对设备节点进行独占访问锁定，防止其他进程访问</li>
                <li><strong>事件拦截：</strong>程序开始接收并处理该设备的所有输入事件</li>
                <li><strong>系统隔离：</strong>系统和其他应用将无法接收到该设备的任何输入事件</li>
            </ol>
            
            <p><strong>技术实现：</strong></p>
            <ul>
                <li>使用Linux的evdev接口访问输入设备</li>
                <li>通过EVIOCGRAB ioctl调用实现设备独占</li>
                <li>读取原始输入事件并进行自定义处理</li>
                <li>阻止事件传递到系统默认处理程序</li>
            </ul>
            
            <p><strong>⚠️ 风险提示：</strong></p>
            <ul>
                <li>选中电源键可能导致无法正常关机</li>
                <li>选中音量键可能导致无法调节音量</li>
                <li>选中触摸屏可能导致无法操作手机</li>
                <li>错误配置可能导致设备无法正常使用</li>
            </ul>
            """,
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        
        // 显示模块启动位置信息
        findViewById<TextView>(R.id.tv_startup_location).text = HtmlCompat.fromHtml(
            """
            <h3>模块启动位置</h3>
            <p><strong>系统启动流程：</strong></p>
            <ol>
                <li><strong>系统启动：</strong>Android系统启动后，init进程开始执行</li>
                <li><strong>Root管理器加载：</strong>Magisk或其他root管理器启动并加载模块</li>
                <li><strong>Service.sh执行：</strong>模块的service.sh脚本被root管理器执行</li>
                <li><strong>主程序启动：</strong>service.sh启动kctrl主程序进程</li>
                <li><strong>配置加载：</strong>程序读取/data/data/com.idlike.kctrl.mgr/files/config.txt配置文件</li>
                <li><strong>设备初始化：</strong>根据配置打开并独占指定的输入设备</li>
            </ol>
            
            <p><strong>具体路径：</strong></p>
            <ul>
                <li><strong>模块路径：</strong>/data/adb/modules/kctrl/</li>
                <li><strong>启动脚本：</strong>/data/adb/modules/kctrl/service.sh</li>
                <li><strong>主程序：</strong>/data/adb/modules/kctrl/system/bin/kctrl</li>
                <li><strong>配置文件：</strong>/data/data/com.idlike.kctrl.mgr/files/config.txt</li>
                <li><strong>日志文件：</strong>/data/data/com.idlike.kctrl.mgr/files/kctrl.log</li>
            </ul>
            
            <p><strong>启动时机：</strong></p>
            <p>模块在系统完全启动后（late_start阶段）启动，确保所有系统服务已就绪。</p>
            """,
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}