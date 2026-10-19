# WebView-XiaomiTV

小米 TV 的 WebView 安装与管理工具。通过内置的静态 overlay，把
**Thorium WebView** 和 **AOSmium WebView** 加入系统 WebView 提供者列表，
并可在电视上直接完成安装、切换默认 WebView、部署 overlay 和恢复系统默认。

## 功能

- 查看设备信息、Root 通道、当前默认 WebView、overlay 状态和已安装 WebView；
- 安装 / 更新 / 重装 / 卸载 Thorium、AOSmium、Google WebView；
- 部署内置 overlay 到 `/product/overlay/WebViewOverlay.apk`；
- 将已安装且被系统识别的 WebView 设为默认；
- 一键恢复系统默认设置。

## 适用设备

- 小米 TV `mulan`（Android 9 / API 28，armeabi-v7a）；
- 其他设备会弹出警告，需要自行确认后才会继续。

## 安装

从 [Releases](https://github.com/TVLaunchers/WebView-XiaomiTV/releases/latest)
下载 `WebViewInstaller.apk`，然后：

- 用 U 盘拷到电视，通过文件管理器安装；或
- `adb install -r WebViewInstaller.apk`。

升级安装器时请覆盖安装，保持同一签名。

## 使用

1. **确认 Root 通道**：启动后状态页显示“Root 通道 · 可用”才能执行安装、
   部署等写操作；不可用时只能查看状态。
2. **安装 WebView**：在右侧列表点击未安装的 WebView（Thorium / AOSmium /
   Google），确认后自动下载并安装。已安装的项点击后可选择重装、卸载，
   有新版本时还可更新。安装完成后会询问是否删除已下载的安装包，可选择
   删除以释放空间，或保留以便下次直接重装。
3. **部署 Overlay**：点击 `Webview Overlay` 一行，确认部署。overlay 已内置在
   安装器中，无需联网。部署完成后**必须重启**。
4. **设置默认 WebView**：重启后点击“默认 WebView”，从列表中选择目标实现；
   修改同样在重启后生效。
5. **恢复系统默认**：点击“恢复系统默认设置”，会启用 AOSP WebView、恢复默认
   provider、卸载三个第三方 WebView 并删除 overlay。此操作不可撤销。

部署或切换后，应用会提示重启。重启后状态页显示
`Webview Overlay · 已部署 · 已识别` 即表示 overlay 已生效。

## 下载

`latest` Release 中的文件：

| 文件 | 说明 |
| --- | --- |
| `WebViewInstaller.apk` | 安装器，已内置 overlay |
| `WebViewInstaller.apk.sha256` | 安装器的 SHA-256 校验值 |

`webview-assets` Release 提供安装器下载的 WebView 本体，以及 overlay 归档副本
`WebViewOverlay.apk`（供离线使用或核对），也可手动下载后安装。

## 注意事项

- 静态 overlay 只在开机时加载，部署后必须重启；
- 写操作依赖设备的 TvService 诊断通道，通道不可用时应用处于只读模式；
- 安装器不会自动更新，更新时请重新下载并覆盖安装；
- 仅针对小米 TV `mulan` 验证，其他机型请谨慎操作。
