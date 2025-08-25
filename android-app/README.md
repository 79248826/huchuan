# Android 端说明（MVP）

- 允许输入 PC 的上传 URL（如 `http://192.168.1.10:8090/upload`）
- 选择一个文件并以自定义协议上传（header: X-Filename，body: 原始二进制）
- 依赖简洁，无额外三方网络库

