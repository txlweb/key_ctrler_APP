# 服务测试指南

本文档提供了对项目中三个主要服务的测试方法和验证步骤。

## 服务概览

项目包含以下三个服务：

1. **TorchToggleService** - 手电筒控制服务
2. **VibrationService** - 震动服务
3. **NoteModeGetter** - 情景模式获取服务

## 测试前准备

### 1. 设备要求
- Android设备（已root）
- 启用USB调试
- 安装应用并授予必要权限

### 2. 权限检查
确保应用已获得以下权限：
- 摄像头权限（用于手电筒功能）
- 震动权限
- 通知策略访问权限（用于勿扰模式）
- 前台服务权限
- 唤醒锁权限
- 忽略电池优化权限
- 精确闹钟权限

## 服务测试

### 1. TorchToggleService 测试

#### 功能描述
- 控制设备手电筒开关
- 前台服务，带通知
- 自动重启机制
- 电池优化豁免

#### 测试步骤

**方法1：通过应用界面测试**
1. 打开应用
2. 进入"按键配置"页面
3. 点击添加按键
4. 选择"手电开关"模板
5. 保存并测试按键

**方法2：通过ADB命令测试**
```bash
# 启动手电筒服务
adb shell am start-foreground-service -n com.idlike.kctrl.mgr/.TorchToggleService

# 检查服务是否运行
adb shell dumpsys activity services | grep TorchToggleService

# 检查通知是否显示
adb shell dumpsys notification | grep "服务已启动"
```

**方法3：通过脚本测试**
```bash
# 组合命令测试
adb shell "input keyevent 224 && am start-foreground-service -n com.idlike.kctrl.mgr/.TorchToggleService"
```

#### 预期结果
- 手电筒状态切换（开/关）
- 通知栏显示"服务已启动"通知
- 服务在后台持续运行
- 应用被杀死后服务自动重启

#### 验证要点
- [ ] 手电筒正常开关
- [ ] 前台通知正常显示
- [ ] 服务保活机制工作
- [ ] 权限检查生效
- [ ] 电池优化豁免提示

### 2. VibrationService 测试

#### 功能描述
- 触发设备震动
- 短暂前台服务
- 执行完毕自动停止

#### 测试步骤

**方法1：通过应用界面测试**
1. 打开应用
2. 进入"按键配置"页面
3. 点击添加按键
4. 选择"震动一下"模板
5. 保存并测试按键

**方法2：通过ADB命令测试**
```bash
# 启动震动服务（默认500ms）
adb shell am startservice -n com.idlike.kctrl.mgr/.VibrationService

# 启动震动服务并指定时长（1000ms）
adb shell am startservice -n com.idlike.kctrl.mgr/.VibrationService --el time 1000

# 启动震动服务并指定时长（2000ms）
adb shell am startservice -n com.idlike.kctrl.mgr/.VibrationService --el time 2000

# 检查服务状态（应该很快停止）
adb shell dumpsys activity services | grep VibrationService
```

#### 预期结果
- 设备应该震动一次，时长根据 time 参数决定
- 默认震动时长为 500ms
- 可通过 time 参数自定义震动时长（单位：毫秒）
- 服务启动后立即停止
- 通知栏短暂显示震动服务通知，显示震动时长信息

#### 验证要点
- [ ] 设备正常震动
- [ ] 震动时长约500ms
- [ ] 通知正常显示和消失
- [ ] 服务自动停止

### 3. NoteModeGetter 测试

#### 功能描述
- 获取当前情景模式
- 支持响铃、震动、静音、勿扰模式切换
- 前台服务，带通知
- 需要通知策略访问权限

#### 测试步骤

**方法1：通过应用界面测试**
1. 打开应用
2. 进入"按键配置"页面
3. 选择情景模式相关的模板
4. 测试模式切换功能

**方法2：通过ADB命令测试**
```bash
# 启动情景模式服务
adb shell am start-foreground-service -n com.idlike.kctrl.mgr/.NoteModeGetter

# 检查模式文件
adb shell cat /sdcard/Android/data/com.idlike.kctrl.mgr/files/mode.txt

# 测试模式切换
adb shell cmd audio set-ringer-mode NORMAL    # 响铃
adb shell cmd audio set-ringer-mode VIBRATE   # 震动
adb shell cmd audio set-ringer-mode SILENT    # 静音
adb shell cmd notification set_dnd on         # 勿扰开
adb shell cmd notification set_dnd off        # 勿扰关
```

#### 预期结果
- 正确识别当前情景模式
- 模式文件正确写入
- 模式切换功能正常
- Toast提示正确显示

#### 验证要点
- [ ] 模式识别准确
- [ ] 文件写入正常
- [ ] 模式切换生效
- [ ] 权限检查工作
- [ ] Toast提示显示

## 综合测试场景

### 场景1：组合功能测试
测试多个服务的组合使用：
```bash
# 复杂脚本测试
adb shell 'am start-foreground-service -n com.idlike.kctrl.mgr/.NoteModeGetter && sleep 0.3 && mode=$(cat /sdcard/Android/data/com.idlike.kctrl.mgr/files/mode.txt) && echo "当前模式: $mode"'
```

### 场景2：权限验证测试
1. 撤销摄像头权限
2. 尝试使用手电筒功能
3. 验证权限提示是否正确显示

### 场景3：服务保活测试
1. 启动TorchToggleService
2. 强制关闭应用
3. 验证服务是否自动重启

### 场景4：电池优化测试
1. 启动服务
2. 检查是否提示忽略电池优化
3. 验证服务在息屏后是否继续运行

## 故障排除

### 常见问题

1. **手电筒不工作**
   - 检查摄像头权限
   - 确认设备支持手电筒
   - 检查其他应用是否占用摄像头

2. **震动不工作**
   - 检查震动权限
   - 确认设备震动功能正常
   - 检查系统震动设置

3. **情景模式切换失败**
   - 检查通知策略访问权限
   - 确认系统版本兼容性
   - 检查勿扰模式设置

4. **服务无法启动**
   - 检查应用是否有root权限
   - 确认服务在AndroidManifest.xml中正确声明
   - 检查系统日志错误信息

### 调试命令

```bash
# 查看应用日志
adb logcat | grep "com.idlike.kctrl"

# 查看服务状态
adb shell dumpsys activity services | grep "com.idlike.kctrl"

# 查看权限状态
adb shell dumpsys package com.idlike.kctrl.mgr | grep permission

# 查看通知状态
adb shell dumpsys notification | grep "com.idlike.kctrl"
```

## 测试报告模板

### 测试环境
- 设备型号：
- Android版本：
- 应用版本：
- 测试日期：

### 测试结果

| 服务 | 功能 | 状态 | 备注 |
|------|------|------|------|
| TorchToggleService | 手电筒开关 | ✅/❌ | |
| TorchToggleService | 前台通知 | ✅/❌ | |
| TorchToggleService | 服务保活 | ✅/❌ | |
| VibrationService | 震动功能 | ✅/❌ | |
| VibrationService | 自动停止 | ✅/❌ | |
| NoteModeGetter | 模式识别 | ✅/❌ | |
| NoteModeGetter | 模式切换 | ✅/❌ | |
| NoteModeGetter | 权限检查 | ✅/❌ | |

### 问题记录
- 问题描述：
- 复现步骤：
- 预期结果：
- 实际结果：
- 解决方案：

---

**注意：测试时请确保设备已获得root权限，并且应用已安装并授予必要权限。**