# 局域网无线键盘 / LAN Wireless Keyboard

[中文](README.md) · [English](README.en.md)

把 Android 手机变成 Windows 的局域网无线键盘、触控板和游戏移动控制器。手机使用原生输入法，Windows 接收端通过 TLS 验证连接，并使用 Win32 `SendInput` 注入键盘与鼠标事件。

<p align="center">
  <img src="docs/images/android-preview.jpg" width="420" alt="Android 客户端界面预览">
</p>

## 功能

- 中文拼音候选只留在手机，选字提交后才发送最终文字。
- 同步模式实时输入；异步模式先编辑草稿，再点击发送。
- 提供方向键、W/A/S/D 游戏摇杆、Esc、独立右键和可选滚轮区。
- 触控板支持移动、轻点左键、第二次抬手触发双击、长按一秒拖动、轻点后按住拖动、双指滚动与双指右键。
- 地球键和“中/EN”键可切换电脑输入法；设置中提供常见 Windows/macOS 快捷键预设。
- 可分别调节指针速度、触控板加速度、滚轮刻度、惯性和震感；`1.0×` 保持精确原速，快速滑动可渐进提升至设定倍率。
- Android 离开前台立即断开；Windows 关闭窗口后留在通知区域，选择“退出”才完全结束。
- Android 默认跟随系统浅色/深色模式，也可强制指定；双主题配置框架可统一修改页面、图标、文字、输入框和触控板颜色，并可连同 AI 提示词一键复制。
- Android 界面以及 Windows 的 EXE、窗口和通知区域使用 MIT 许可的 Hugeicons Stroke Rounded 图标。

## 下载

在 GitHub 的 **Releases** 页面下载 Android `v1.2.0` 与兼容的 Windows 接收端 `v1.1.0`：

- `LAN-Wireless-Keyboard-Android-v1.2.0.apk`
- `LAN-Wireless-Keyboard-Windows-x64-v1.1.0.zip`

可用同一 Release 中的 `SHA256SUMS.txt` 校验下载文件。Windows 接收端协议未变，本次沿用 `v1.1.0`；它仅支持 64 位 x64 Windows。

## 1. 安装 Windows 的 .NET 环境

Windows 接收端需要 **.NET 10 Desktop Runtime x64**。不要选择 ASP.NET Core Runtime；只运行本软件也不需要安装 SDK。

### 使用 WinGet

以管理员身份打开 Windows Terminal 或 PowerShell：

```powershell
winget install Microsoft.DotNet.DesktopRuntime.10
```

### 手动安装

1. 打开 [.NET 10 官方下载页面](https://dotnet.microsoft.com/en-us/download/dotnet/10.0)。
2. 找到 **.NET Desktop Runtime 10**。
3. 在 Windows 一栏下载 **x64 Installer**。
4. 运行安装程序并完成安装。

安装后重新打开终端并验证：

```powershell
dotnet --list-runtimes
```

输出中应存在 `Microsoft.WindowsDesktop.App 10.` 开头的一行。若不存在，请确认安装的是 Desktop Runtime x64。

## 2. 启动 Windows 接收端

1. 解压 Windows ZIP 到当前用户有写入权限的普通文件夹，不要直接在 ZIP 内运行 EXE，也不要放入需要管理员写权限的目录。
2. 双击 `VirtualKeyboardReceiver.exe`。接收端清单要求管理员权限；Windows 显示 UAC 时确认启动。它仍无法操作 UAC 安全桌面。
3. Windows SmartScreen 若提示未知发布者，先核对文件名与 SHA-256，再选择“更多信息”→“仍要运行”。本项目暂未使用商业代码签名证书。
4. Windows 防火墙首次询问时，只允许**专用网络**，不要允许公用网络。
5. 记录窗口显示的局域网 IPv4、端口和 16 位配对码。

接收端首次启动会在 EXE 附近的 `runtime-data` 中生成配对码和 TLS 证书。不要分享、上传或删除这些文件。关闭窗口只会最小化到通知区域；要完全退出，请右键通知区域图标并选择“退出”。

## 3. 安装 Android 客户端

1. 把 APK 下载到手机。
2. Android 提示时，仅为当前使用的浏览器或文件管理器打开“允许安装未知应用”。
3. 安装 APK；安装完成后可关闭该来源的安装权限。
4. 确认手机和电脑连接到同一个可信局域网，且访客 Wi‑Fi 没有启用设备隔离。
5. 打开应用，进入设置，填写 Windows 窗口中的 IPv4、端口和配对码，然后保存。
6. 返回主页面并等待状态变成“已连接”。首次成功连接后，手机会固定该接收端的证书；以后证书发生变化会拒绝连接。

## 4. 使用

- 点击输入框后使用手机原生输入法。中文未选字的拼音不会发送到电脑，选字后才发送最终文字。
- 顶部选择“同步模式”会实时发送；选择“异步模式”会保留本地草稿，点击发送图标后整段提交。两种模式的输入栏均保持一行高。
- 同步模式输入栏右侧的地球键默认发送 `Win + Space`，“中/EN”默认发送 `Shift`；可在设置中选择其他固定快捷键。
- 左侧方向区映射 Windows 方向键；右侧 W/A/S/D 按钮和八向摇杆用于游戏人物移动。
- 在触控区拖动可移动鼠标；轻点触发左键，连续轻点两次会在第二次抬手时形成标准左键双击。长按一秒进入左键按住状态并震动一次，轻点后再次按住仍可作为保底拖拽手势；普通触摸和移动光标不会震动。双指滑动用于滚轮，双指轻点或右上角按钮触发右键。
- 触控区右侧独立滚轮可在设置中关闭；左上角按钮发送 Esc。
- 在设置中分别调整指针速度与触控板加速度。加速度 `1.0×` 为中性，数值越高，快速滑动时移动距离越大，慢速移动仍保持精细。
- Android 应用离开前台、锁屏或关闭时会断开连接，不在后台保留网络线程或待发送输入。

## 排障

- **提示缺少 .NET：**重新安装 .NET 10 Desktop Runtime x64，并用 `dotnet --list-runtimes` 验证。
- **无法连接：**确认双方在同一局域网，IP 没有变化，端口一致，防火墙只在专用网络放行接收端。
- **配对失败：**重新输入窗口当前显示的完整 16 位配对码，不要使用 README 中的示例值。
- **提示证书变化：**先确认没有连接到另一台电脑。只有确定是同一接收端且其 `runtime-data` 被重建后，才在手机设置中重新保存配对信息。
- **部分游戏不响应：**使用 Raw Input、反作弊驱动或主动拒绝 `SendInput` 的程序可能不接受模拟输入。
- **UAC 页面不能操作：**接收端即使以管理员权限运行，也不能向 Windows UAC 安全桌面注入输入。
- **通知区域找不到图标：**展开 Windows 通知区域的隐藏图标；图标在浅色任务栏为黑色，暗色任务栏为白色。

## 从源码构建

- Android：JDK 17、Android SDK 36，运行 `./gradlew testDebugUnitTest lintDebug assembleDebug`。
- Windows：.NET 10 SDK，在 `windows` 目录运行 `dotnet test`，再按项目文件中的 `win-x64` 配置发布。

本项目采用 [Apache License 2.0](LICENSE)。第三方图标说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
