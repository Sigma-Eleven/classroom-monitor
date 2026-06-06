# 课堂专注度监测与学习行为分析助手

这个项目用于：上传课堂照片后，识别学生整体学习状态（抬头听课/低头/睡觉/玩手机/走神/其他），并输出统计结果（人数、占比、课堂小结）。提供网页界面与后端 API 两种使用方式。

技术实现：Spring Boot 提供 Web/API；Spring AI 通过 OpenAI 兼容接口调用 Kimi（Moonshot）视觉模型做图片识别（也支持关闭真实调用走模拟识别）。

## 目录

- [环境要求](#环境要求)
- [启动与打包](#启动与打包)
- [配置（Kimi）](#配置kimi)
- [API 调用](#api-调用)
- [数据与文件](#数据与文件)
- [常见问题](#常见问题)

## 环境要求

- JDK 17+
- Maven Wrapper（项目包含 `mvnw.cmd`）
- Windows 推荐使用 PowerShell 运行命令

## 启动与打包

在项目根目录运行（开发启动，保持终端不要关闭）：

```powershell
.\mvnw.cmd -DskipTests spring-boot:run
```

页面入口：

- <http://localhost:8080/>

停止服务：在运行中的终端按 `Ctrl + C`。

打包并运行：

```powershell
.\mvnw.cmd -DskipTests clean package
java -jar .\target\classroom-monitor.jar
```

## 配置（Kimi）

默认关闭真实 AI 调用（走模拟识别），避免在未配置密钥时启动失败。

开关：

- `APP_AI_ENABLED=false`：不调用模型，走模拟识别（输出固定）
- `APP_AI_ENABLED=true`：调用 Kimi 视觉模型进行识别

启用真实识别建议使用本机 `.env`（项目启动时自动读取，且 `.env` 已在 `.gitignore` 中忽略，不会提交到仓库）：

1. 复制 `.env.example` 为 `.env`

2. 在 `.env` 中设置：

- `KIMI_API_KEY=你的KimiKey`
- `APP_AI_ENABLED=true`
- `KIMI_MODEL=moonshot-v1-8k-vision-preview`（图片识别使用视觉模型）

可选配置：

- `KIMI_BASE_URL=https://api.moonshot.cn`（OpenAI 兼容接口地址）
- `KIMI_COMPLETIONS_PATH=/v1/chat/completions`（一般不需要改；当 base-url 自己包含 `/v1` 时可设为 `/chat/completions`；不需要就不要写该项，避免写成空值）

兼容项（可选）：

- `.env` 中填写 `MOONSHOT_API_KEY` 时，会自动按 `KIMI_API_KEY` 使用

默认值与完整配置入口见 [application.yml](src/main/resources/application.yml) 与 [.env.example](.env.example)。

## API 调用

后端接口统一前缀：`/api/v1`（默认端口 8080）。

调用前提：先启动后端服务，并保持运行中的终端不要关闭（建议另开一个 PowerShell 窗口执行下面命令）。

快速自检：

- 浏览器打开 <http://localhost:8080/> 有页面，说明服务正常
- 或检查 8080 是否在监听：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

如果提示 `Failed to connect to localhost port 8080`，说明服务未启动或端口不是 8080（可能换成了 8081 等）。

上传并识别（推荐：Windows 使用 `curl.exe`）：

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/analyze" -F "file=@C:\path\to\image.jpg"
```

返回说明（关键字段）：

- `data.previewUrl`：图片预览相对路径
- `data.analysis`：本次识别结果（包含 `recognition` 与 `metrics`）

打开预览（把 previewUrl 拼上 host）：

```text
http://localhost:8080{previewUrl}
```

仅上传（返回 uploadId + 预览地址）：

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/upload" -F "file=@C:\path\to\image.jpg"
```

对已上传的图片再识别（uploadId 来自 upload 接口返回）：

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/analyze/{uploadId}"
```

查询统计：

```powershell
curl.exe "http://localhost:8080/api/v1/stats"
```

查询全部识别记录：

```powershell
curl.exe "http://localhost:8080/api/v1/results"
```

说明：在 PowerShell 里要用 `curl.exe`，不要用 `curl`（PowerShell 可能把 `curl` 解析为 `Invoke-WebRequest`，导致 `-X/-F` 不可用）。

接口列表：

| 方法 | 路径                              | 说明                                   |
| ---- | --------------------------------- | -------------------------------------- |
| POST | `/api/v1/upload`                  | 仅上传，返回 `uploadId` 与图片预览地址 |
| POST | `/api/v1/analyze`                 | 上传并识别（前端页面使用）             |
| POST | `/api/v1/analyze/{uploadId}`      | 对指定上传记录进行识别                 |
| GET  | `/api/v1/results/{analysisId}`    | 查询单次识别结果                       |
| GET  | `/api/v1/results`                 | 查询会话内全部识别记录                 |
| GET  | `/api/v1/stats`                   | 查询会话内汇总统计                     |
| GET  | `/api/v1/uploads/{uploadId}/file` | 图片预览                               |

## 数据与文件

- 不使用数据库：识别记录与统计数据保存在内存中（重启后清空）
- 上传图片保存到本地 `./uploads`：同内容自动去重（内容哈希一致则复用同一文件）
- 图片预览接口：`/api/v1/uploads/{uploadId}/file`

## 常见问题

### 1) 提示 “JAVA_HOME environment variable is not defined correctly”

`mvnw.cmd` 依赖 `JAVA_HOME`。可以在 PowerShell 临时设置（以实际 JDK 目录为准）：

```powershell
$env:JAVA_HOME="D:\java"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -v
```

### 2) 8080 端口被占用（Port 8080 was already in use）

换端口启动（例如 8081）：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

页面入口：

- <http://localhost:8081/>

查占用进程（获取 OwningProcess=PID）：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
```

查看 PID 对应的命令行：

```powershell
(Get-CimInstance Win32_Process -Filter "ProcessId=PID").CommandLine
```

结束进程释放端口：

```powershell
Stop-Process -Id PID -Force
```

### 3) 识别报错 404，出现 /v1/v1/chat/completions

`KIMI_BASE_URL` 不建议带 `/v1`，推荐：`https://api.moonshot.cn`。

### 4) 识别报错 “Not found the model … / Permission denied”

请使用账号可用的视觉模型，例如：`moonshot-v1-8k-vision-preview`。
