# PC ↔ Android 文件互传工具

本项目提供一个最小可用(MVP)的“可视化互传文件”方案：
- PC（Windows）端：Electron 可视化界面 + 内置简易 HTTP 接收端/发送端
- Android 端：原生 Kotlin App，可选择文件并上传到 PC 的接收端

第一阶段能力（MVP）：
- 局域网同网段下，Android → PC 上传文件（PC 端开服务接收）
- PC 端也可上传到对端指定地址（用于后续 Android 作为接收端扩展）
- 传输协议：HTTP POST /upload，header: X-Filename=<文件名>，body: 原始二进制流

注意：当前使用无依赖的自定义简易协议（非 multipart），便于零依赖启动与示例演示。

## 目录结构
- pc-app/  Electron 应用（UI + 内置 HTTP 服务）
- android-app/  Android 应用（选择文件并上传）

## 运行（PC 端）
1. 安装 Node.js（建议 LTS）
2. 进入 pc-app 目录
3. 首次安装依赖（仅 Electron 本体）：
   - 建议执行：`npm init -y && npm install --save-dev electron`
   - 本示例服务端使用 Node 内置 http 模块，无需额外依赖
4. 启动：`npx electron .`
5. 在 UI 中点“启动接收服务”，记下显示的 IP:PORT 与上传 URL（例如 `http://192.168.1.10:8090/upload`）

## 运行（Android 端）
1. 使用 Android Studio 打开 android-app 目录
2. 连接真机或启动模拟器，运行应用
3. 在应用中输入 PC 的上传 URL（例如 `http://192.168.1.10:8090/upload`）
4. 选择文件后上传

## 安全说明
- 当前为局域网演示版，未启用 TLS/鉴权。请仅在可信网络内使用。
- 后续可扩展：首次配对指纹确认、共享密钥、端到端加密、二维码快速配对等。

## 后续规划
- Android 端内置接收服务（Ktor/NanoHTTPD）实现 PC → Android
- 局域网自动发现（mDNS/UDP 广播）与扫码配对
- 断点续传、Hash 校验、去重命名与冲突处理
- 更完善的错误处理、日志与带宽/并发控制

