# WebView-XiaomiTV

一个极小的静态 RRO（Runtime Resource Overlay），用于覆盖 `framework-res` 的
`xml/config_webview_packages`，把 `com.thorium.webview`（Thorium）和
`org.axpos.aosmium_wv`（AOSmium）加入系统 WebView 提供者列表，并让它们优先于
ROM 自带 WebView 被选中。这样宿主 App 里的 `WebView` 就会使用新版 Chromium。

当前目标设备：

- 小米 TV（`mulan`，Android 9 / API 28，armeabi-v7a）——本仓库的主要目标；
- 同一份 APK 也适用于 Android 12 的 Amlogic 盒子（`isStatic` / `priority` 在 API 28 就已支持）。

Thorium 使用 `SystemWebView_arm32.apk`（`138.0.7204.303`，包名 `com.thorium.webview`）。
AOSmium System WebView 为 `138.0.7204.45`，包名 `org.axpos.aosmium_wv`，同样是
armeabi-v7a、minSdk 26。
注意：桌面上那个 `org.chromium.thorium` 是 Thorium **浏览器**，不能当 WebView provider。
`config_webview_packages.xml` 的结构参考了 Amlogic A12 固件里的
`GoogleWebViewOverlay.apk`。

## 目录结构

```
WebView-XiaomiTV/
  AndroidManifest.xml
  res/xml/config_webview_packages.xml
  installer/                          # 设备端安装器（Kotlin + Compose for TV）
    app/src/main/java/com/tvlaunchers/webviewinstaller/
  .github/workflows/build-release.yml  # GitHub Actions（唯一构建入口）
  README.md
```

## 选择逻辑

`WebViewUpdateServiceImpl.findPreferredWebViewPackage()` 的顺序是：

1. 用户显式选择过的 provider（`Settings.Global.WEBVIEW_PROVIDER`）；
2. 否则按 `config_webview_packages.xml` 的**文档顺序**，取第一个
   `availableByDefault="true"`、已安装、已启用、targetSdk / 版本号 / 签名都合法的 provider；
3. 都没有时使用 `isFallback="true"` 的兜底 provider。

因此本 overlay 把 Thorium 放在第一条、AOSmium 放在第二条：两者都安装时选中
Thorium，只安装其中一个时选中已安装的那个。Google WebView 继续保留
`isFallback="true"`，这样两个新 WebView 都不可用时系统仍能退回原有实现。

XML 里的 `<signature>` 是从对应 APK 的实际签名证书提取的（DER 转 Base64）：

| 包名 | 签名证书 SHA-256 |
| --- | --- |
| `com.thorium.webview` | `32a2fc74d731105859e5a85df16d95f102d85b22099b8064c5d8915c61dad1e0` |
| `org.axpos.aosmium_wv` | `005c9805d501bf50c1a8bfd3204b6908843088581fdcf3db8ab4f688ffc0e7b6` |

由于 XML 中显式写了 `<signature>`，无论作为系统应用预置还是普通安装，APK 的签名
都必须与这里一致，否则该 provider 会被系统跳过。

## 当前小米 TV 的实际状态（部署前必读）

在 `192.168.1.211`（`mulan`，API 28）上实测：

| 项目 | 值 |
| --- | --- |
| 当前 provider | `com.google.android.webview` 138.0.7204.181 |
| `settings get global webview_provider` | `com.google.android.webview`（**被显式钉死**） |
| 已存在的 overlay | `/vendor/overlay/WebViewOverlay.apk` = `com.afk.webviewoverlay`，static，priority=1 |
| 已安装的 Thorium | `org.chromium.thorium`（浏览器，不是 WebView） |
| `su` | 不存在（production build，`adb root` 不可用） |
| `/vendor` | `ro` 挂载；`/vendor/overlay` 目录虽然是 0777，但写入会报 read-only |

由此得到两个关键结论：

1. **即使 overlay 生效，只要 `webview_provider` 仍是 Google，Thorium 也不会被选中。**
   部署后必须清掉或改写这个设置。
2. 旧的 `com.afk.webviewoverlay` 也是 static + priority=1。本 overlay 使用
   **priority=2**，在 Android 9 的静态 overlay 排序中优先级更高。
   最稳妥的做法仍是部署时替换掉 `/vendor/overlay/WebViewOverlay.apk`。

另外，这台 ROM 上 `cmd overlay list --user 0` 输出为空（返回码 0），
但 `dumpsys overlay` 能正常看到 overlay，验证时请以后者为准。

## 构建

### GitHub Actions（唯一构建方式）

本项目不提供本地构建脚本，所有产物都由 CI 构建。工作流内联执行
`aapt2 compile` → `aapt2 link` → `zipalign -p -f 4` → `apksigner sign` →
`apksigner verify`，并在上传前校验 manifest 与 provider 列表。

工作流为 `.github/workflows/build-release.yml`，触发方式：

- 向 `main` 提交 PR 并且**已合并**（closed + merged）；
- 或在 Actions 页面手动 `Run workflow`。

每次运行都会：

1. 用 `aapt2` / `zipalign` / `apksigner` 构建并校验 `WebViewOverlay.apk`；
2. 用 Gradle 构建 `WebViewInstaller.apk`（`installer/`，`assembleRelease`）；
3. 上传 Artifacts（**WebViewTools**，包含两个 APK 及各自的 `.sha256`）；
4. 以构建日期作为版本号和 Release tag：tag 与 `versionName` 为
   `YYYY.MM.DD`（Asia/Shanghai），`versionCode` 为 `YYYYMMDD`；
   创建或更新该日期的 Release，并标记为 GitHub 的 **latest**。
   同一天重复构建会覆盖当天的 Release。

安装器跟随 GitHub 的 latest Release 下载，因此不需要知道日期 tag：

- `https://github.com/TVLaunchers/WebView-XiaomiTV/releases/latest/download/WebViewOverlay.apk`
- `https://github.com/TVLaunchers/WebView-XiaomiTV/releases/latest/download/WebViewOverlay.apk.sha256`

### 签名

安装器需要在多次更新之间保持**同一个签名**（否则 `pm install -r` 会失败），
因此 CI 要求必须配置组织/仓库 Secrets（仓库级同名 Secret 优先）：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | JKS 文件的 base64（`base64 -w0 my.jks`），**必需** |
| `KEYSTORE_PASS` | keystore / key 口令，**必需** |
| `KEY_ALIAS` | 别名，缺省 `thorium-overlay` |

缺少 `KEYSTORE_BASE64` 或 `KEYSTORE_PASS` 时工作流会直接失败，不会退回到一次性密钥。

## 设备端安装器（WebViewInstaller）

`installer/` 是一个面向 Android TV（遥控器操作、横屏）的原生应用，包名
`com.tvlaunchers.webviewinstaller`，minSdk 28 / targetSdk 28。它把原来需要
`adb` + root 手工执行的步骤做成了可在电视上直接运行的界面：

- 检测并展示 Root 通道、当前 WebView provider、overlay 状态、已安装 WebView；
- 下载并安装 Thorium / AOSmium / Google WebView；
- 下载并部署 `/vendor/overlay/WebViewOverlay.apk`；
- 把任意一个**签名合法且已被系统识别**的 provider 写入 `webview_provider`；
- 一键完整还原（启用 AOSP WebView、恢复默认 provider、卸载三个第三方 WebView、删除 overlay）。

### 安装与启动

从 GitHub 的 latest Release（按构建日期打 tag）下载 `WebViewInstaller.apk`，然后：

```bash
adb install -r WebViewInstaller.apk
```

也可以把 APK 放到 U 盘，用电视上的文件管理器安装。首次启动时：

1. 应用会请求 `WRITE_EXTERNAL_STORAGE`。授予后下载到
   `/sdcard/Download/WebViewInstaller/downloads`；拒绝则改用应用专属外部目录，
   功能不受影响。
2. 检查 `Build.DEVICE`。不是 `mulan` 时弹出警告，默认按钮是**退出**，
   只有显式选择“仍要继续”才会继续。
3. 如果 `/vendor/overlay` 不存在，直接终止（本工具只支持该部署方式）。

### 提权通道

复用 `Shizuku-XiaomiTV` 里的 TvService 诊断通道：

```bash
service call TvService 4400 s16 s s16 <脚本绝对路径>
```

- 启动前用 `service list` 确认存在 `TvService` 与 `mitv.internal.ITvService`，
  再执行一次 `id -u` 探测；探测失败则整个应用进入**只读模式**，所有写操作禁用。
- 脚本与日志放在应用私有目录 `files/wvinstaller/{run,logs}`，脚本通过
  `setReadable(true, false)` 暴露给 TvService；TvService 以 `sh` 读取执行，
  因此不依赖可执行位。
- 每次任务生成唯一的 `<id>.sh`、`<id>.log` 与 `<id>.exit`；应用每 250 ms
  轮询退出文件，并读取日志。超时、非零退出码都会把日志内联显示在错误弹窗里。
- 脚本内统一设置 `PATH=/system/bin:/vendor/bin:/vendor/xbin`，并做了
  `/system/bin/sh` 兼容处理（不依赖 bash）。
- 该通道以 `uid=0`、SELinux 域 `u:r:misysdiagnose:s0` 运行。它不是普通 `su`，
  没有交互式 shell，也没有 stdout 回传——这正是用日志文件 + 退出文件的原因。

### 使用流程

1. **状态页**：显示设备信息、Root 通道、当前 provider、`webview_provider` 设置、
   overlay 是否存在 / 是否被系统识别 / 本地与远端哈希、已安装 WebView 列表。
   “刷新”会同时拉取 Release 的 `.sha256` 和资产清单。
2. **安装 / 更新 WebView**：每个 WebView 显示本机版本、远端版本和大小。
   下载完成后先校验 APK 包名，再以 root 执行 `pm install -r --user 0`。
   可重复运行，已安装同版本时按钮显示“重新安装”。
3. **部署 Overlay**：下载 latest Release 中的 overlay，校验 `.sha256` 与包名后，
   以 root 重新挂载 `/vendor`、写入 `/vendor/overlay/WebViewOverlay.apk`、
   设置 `644 root:root`、`restorecon`、`sync`，最后重新挂载为只读。
4. **设置默认 WebView**：列表来自 `dumpsys webviewupdate` 中签名有效的
   provider。选择后写入 `settings put global webview_provider <包名>`。
   已安装但尚未被系统识别的包会灰显，并提示先部署 overlay 并重启。
5. **完整还原**：启用 `com.android.webview`、把 provider 写回
   `com.android.webview`、卸载 `com.thorium.webview` / `org.axpos.aosmium_wv` /
   `com.google.android.webview`、删除 overlay 文件。此操作不可撤销。

写操作完成后，应用会提示重启；重启后状态页会清除“等待重启”标记，
并以 `dumpsys` 的实际结果为准。

### 下载与校验

- 前台 Service + 通知栏进度，支持 HTTP `Range` 续传；取消时保留 `.part`
  文件，下次从断点继续。
- 来源顺序：GitHub 直连 → `https://ghfast.top/` → `https://gh-proxy.com/`，
  前一个失败自动切换下一个。
- 每个来源下载完成后都会校验 SHA-256；不匹配则删除该来源的分片并换源。
- overlay 文件较小，走内存外的流式下载 + 同样的哈希校验。

### Release 布局

| tag | 文件 | 维护方式 |
| --- | --- | --- |
| `YYYY.MM.DD` | `WebViewOverlay.apk`、`WebViewOverlay.apk.sha256`、`WebViewInstaller.apk`、`WebViewInstaller.apk.sha256` | CI 按构建日期创建，当天重复构建会覆盖；最新的一个被标记为 GitHub latest |
| `webview-assets` | `manifest.json`、`thorium.apk`、`aosmium.apk`、`google.apk` | **手动维护** |

`webview-assets` 只提供“离线 / 镜像”性质的 WebView 本体，CI 不构建也不校验它们。
`manifest.json` 的结构：

```json
{
  "packages": [
    {
      "id": "thorium",
      "package": "com.thorium.webview",
      "file": "thorium.apk",
      "sha256": "<64 位十六进制>",
      "size": 123456789,
      "versionName": "138.0.7204.303",
      "versionCode": 1387204303,
      "minSdk": 26,
      "abi": "armeabi-v7a"
    }
  ]
}
```

安装器会跳过缺少 `package`、`file` 或合法 `sha256` 的条目。`id` 缺省时按包名
匹配内置的 `thorium` / `aosmium` / `google`。

### 已知限制

- 静态 overlay **只在开机扫描时加载**。部署后必须重启，重启前状态页不可能显示
  “已被系统识别”，因此安装器不会在重启前做 overlay 有效性判断。
- `dumpsys webviewupdate` 只列出**当前已合法**的 provider。新安装的
  Thorium / AOSmium 在 overlay 生效并重启之前不会出现在“设置默认”列表里；
  这是 Android 9 的行为，不是安装器遗漏。
- 非 `mulan` 设备仅提供警告；overlay 路径固定为 `/vendor/overlay`，
  Amlogic `product/overlay` 仍需手工部署。
- 只校验 SHA-256，不做签名校验。`manifest.json` 与 APK 同源，
  如果镜像同时篡改了清单和文件，安装器无法发现；请优先使用 GitHub 直连。
- v1 不做应用自更新；更新安装器请重新下载 `WebViewInstaller.apk` 覆盖安装
  （必须使用同一签名）。

## 部署

> 前提：设备需要 root（或能改系统分区 / 使用 Magisk），因为 overlay 必须从系统分区加载。

### 小米 TV（Android 9 / API 28）

Android 9 已经支持 manifest 里的 `android:isStatic` / `android:priority`：

- `isStatic="true"` 的 overlay 由 `OverlayManagerService` 视为
  `STATE_ENABLED_STATIC`，**开机自动生效、不可在运行时禁用**；
- 不需要 `cmd overlay enable`（该命令对静态 overlay 会被忽略）；
- 同 target 的多个静态 overlay 按 `priority` 排序，数值大的优先。

1. 取得 root 后重新挂载 vendor（只读 → 可写），把 overlay 放进去：

   ```bash
   adb root                     # production build 会失败，需要设备端 root 方案
   adb shell mount -o rw,remount /vendor
   adb push out/WebViewOverlay.apk /vendor/overlay/WebViewOverlay.apk
   adb shell chmod 644 /vendor/overlay/WebViewOverlay.apk
   ```

   直接覆盖旧的 `WebViewOverlay.apk`（`com.afk.webviewoverlay`）可以避免两个
   静态 overlay 同时存在；如果想保留旧的，新文件名也可以，priority=2 会胜出。

   > 如果之前部署过旧包名 `com.thorium.overlay.webview` 的 overlay，请先找到并删除
   > 旧文件再重启（例如 `find /vendor /product -name '*.apk' -exec ...`）。
   > 否则会同时存在两个 target 相同、priority 都是 2 的静态 overlay，
   > 最终生效哪一个取决于扫描顺序。
   >
   > 当前包名为 `com.android.webviewoverlay`。

2. 安装 WebView 本体（Thorium 和/或 AOSmium；只装其中一个也可以）：

   ```bash
   # Thorium：作为系统应用预置（推荐）
   adb root
   adb shell mount -o rw,remount /vendor
   adb shell mkdir -p /vendor/app/ThoriumWebView
   adb push ThoriumWebView.apk /vendor/app/ThoriumWebView/ThoriumWebView.apk
   adb shell chmod 644 /vendor/app/ThoriumWebView/ThoriumWebView.apk

   # AOSmium：作为系统应用预置
   adb shell mkdir -p /vendor/app/AOSmiumWebView
   adb push "AOSmium System WebView 138.0.7204.45.720404500.apk" \
       /vendor/app/AOSmiumWebView/AOSmiumWebView.apk
   adb shell chmod 644 /vendor/app/AOSmiumWebView/AOSmiumWebView.apk

   # 或普通安装到 /data（依赖 XML 里的 <signature>，不需要 root）
   adb install -r ThoriumWebView.apk
   adb install -r "AOSmium System WebView 138.0.7204.45.720404500.apk"
   ```

   两者都安装时按 XML 顺序优先生效 Thorium；想优先 AOSmium，把它的条目移到
   Thorium 之前即可。

3. 重启，然后清掉被钉死的 provider 设置：

   ```bash
   adb reboot
   adb shell settings delete global webview_provider
   # 或直接指定
   adb shell settings put global webview_provider com.thorium.webview
   # 或
   adb shell settings put global webview_provider org.axpos.aosmium_wv
   ```

   也可以在开发者选项 → WebView implementation 里选择 Thorium 或 AOSmium。

### Amlogic A12 super 镜像

1. 把产物拷进产品分区 overlay 目录：

   ```
   E:\Amlogic\S905L3A-A12-KM2-MOD\super\product\product\overlay\WebViewOverlay.apk
   ```

   文件权限 `0644`、属主 `root:root`，SELinux 标签与同目录其它 overlay 保持一致。

2. 该分区没有 `overlay/config/config.xml`，Android 12 会直接使用 APK manifest 里的
   `android:isStatic` 和 `android:priority`。本 overlay 是 `isStatic="true"`、
   `priority="2"`，而 `GoogleWebViewOverlay.apk` 是 `priority="0"`，
   两者 targetPackage 都是 `android`，所以本 overlay 优先级更高、覆盖生效。

   `GoogleWebViewOverlay.apk` 可以保留（方便回滚），也可以删掉。

3. WebView 本体放到 `super\product\product\app\ThoriumWebView\ThoriumWebView.apk`
   （AOSmium 同理，如 `...\app\AOSmiumWebView\AOSmiumWebView.apk`），或 `adb install`
   到 `/data`。

## 验证

```bash
adb shell dumpsys webviewupdate
adb shell settings get global webview_provider
adb shell dumpsys overlay | grep -A 8 webviewoverlay
adb logcat -s WebViewUpdateService
```

期望 `dumpsys webviewupdate` 中出现（取决于安装的是哪个；两者都装时为 Thorium）：

```
Current WebView package (name, version): (com.thorium.webview, 138.0.7204.303)
# 或
Current WebView package (name, version): (org.axpos.aosmium_wv, 138.0.7204.45)
```

如果仍然指向旧 WebView：

1. `settings get global webview_provider` 有值的话，说明被显式指定过。清掉或改成目标包名：

   ```bash
   adb shell settings delete global webview_provider
   # 或
   adb shell settings put global webview_provider com.thorium.webview
   ```

2. 确认 overlay 已被系统识别（这台 TV 上 `cmd overlay list` 不可靠）：

   ```bash
   adb shell dumpsys overlay | grep -B 2 -A 8 com.android.webviewoverlay
   ```

   期望看到 `mState: STATE_ENABLED_STATIC`、`mTargetPackageName: android`、
   `mPriority: 2`。

3. 确认目标包对系统可见、包名正确且签名一致：

   ```bash
   adb shell pm list packages | grep -Ei 'thorium|aosmium'
   adb shell dumpsys package com.thorium.webview | grep -E 'versionName|codePath'
   adb shell dumpsys package org.axpos.aosmium_wv | grep -E 'versionName|codePath'
   ```

   注意 `org.chromium.thorium` 是 Thorium 浏览器，不是 WebView provider。

改完 provider 后需要 `am force-stop` 宿主 App（或重启）才会生效。

## 回滚

- 删掉对应的 overlay APK 并重启，原来的 `config_webview_packages` 立即恢复；
- 或保留 overlay，把 `webview_provider` 设回原来的包名；
- Amlogic A12：删掉 `product/overlay/WebViewOverlay.apk` 后重新刷机，
  `GoogleWebViewOverlay.apk` 继续生效。

## 注意

- Thorium `SystemWebView_arm32.apk` 是 **armeabi-v7a** 构建，要求设备/固件支持 32 位 ABI。
  小米 TV 与 S905L3A 均满足；纯 64 位 userspace 的固件需要改用
  `SystemWebView_arm64.apk` 并重新提取签名。
- 该 APK 的 `minSdkVersion=26`，Android 9（API 28）与 Android 12 都满足。
- overlay 自身的 `minSdkVersion` 是 28；targetSdk 31 只是为了使用新版编译工具链，
  在 Android 9 上不影响加载。
- 如果开启 `isStatic` 后系统仍不识别，先确认 APK 确实位于被扫描的 overlay 目录，
  并且文件 SELinux 上下文与同目录其它文件一致。
