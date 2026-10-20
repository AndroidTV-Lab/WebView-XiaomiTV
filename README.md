# WebView-XiaomiTV

小米 TV 的 WebView 安装与管理工具。通过内置的静态 overlay，把
**Thorium WebView**、**AOSmium WebView** 和 **Google WebView** 加入系统
WebView 提供者列表，并同时信任厂商签名与项目签名，因此原厂包和重新签名的
定制包都能被系统识别。可在电视上直接完成安装、切换版本、切换默认 WebView、
部署 overlay 和恢复系统默认。

## 功能

- 查看设备信息、Root 通道、当前默认 WebView、overlay 状态和已安装 WebView；
- 安装 / 切换 / 重装 / 卸载 Thorium、AOSmium、Google WebView，每类可提供多个版本；
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
2. **安装 WebView**：在右侧列表点击 Thorium / AOSmium / Google，会打开该
   WebView 的版本列表。每类 WebView 可以提供多个版本（例如标准版、关闭
   WebGPU 的定制版），列表会标注版本号、大小、签名来源和适用说明；选择
   一个版本确认后自动下载并安装。已安装的版本会标记为“已安装”，点击可重装；
   选择另一个版本即切换。若两个版本的签名不同，安装器会在下载完成后提示
   先卸载旧包再安装，并在需要时临时切回系统 WebView。安装完成后会询问是否
   删除已下载的安装包，可选择删除以释放空间，或保留以便下次直接重装。
   WebView 列表本身也可以卸载当前已安装的版本。
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

`manifest.json` 中同一个 `package` 可以出现多个条目，用唯一的 `id` 区分版本，
并可附带 `title` / `title_en`、`description` / `description_en`、`signer`
（`vendor` 表示厂商签名，`project` 表示项目签名）和 `webgpu` 字段。安装器据此
展示版本列表；APK 必须只有一个签名者，否则 Android 9 的 WebView 校验会拒绝。

## 注意事项

- 静态 overlay 只在开机时加载，部署后必须重启；
- 写操作依赖设备的 TvService 诊断通道，通道不可用时应用处于只读模式；
- 安装器不会自动更新，更新时请重新下载并覆盖安装；
- 仅针对小米 TV `mulan` 验证，其他机型请谨慎操作。
