# 课堂专注度监测与学习行为分析助手

基于 Spring Boot + DeepSeek（视觉/多模态）实现的课堂图片专注度与学习行为分析 Demo。  
项目不使用数据库：识别记录与统计数据保存在内存中（重启后清空）；上传图片保存到本地 `./uploads`（相同内容自动去重）。

## 目录

- [环境要求](#环境要求)
- [快速启动](#快速启动)
- [API 调用](#api-调用)
- [DeepSeek 配置](#deepseek-配置)
- [功能与页面](#功能与页面)
- [接口列表（后端）](#接口列表后端)
- [常见问题](#常见问题)

## 环境要求

- JDK 17+
- Maven Wrapper（项目包含 `mvnw.cmd`）

## 快速启动

在项目根目录运行：

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd spring-boot:run
```

启动成功后打开页面：

- <http://localhost:8080/>

也可以打包后运行：

```powershell
.\mvnw.cmd -DskipTests clean package
java -jar .\target\classroom-monitor.jar
```

## API 调用

后端启动后，接口统一前缀：`/api/v1`（默认端口 8080）。

上传并识别：

```powershell
curl -X POST "http://localhost:8080/api/v1/analyze" -F "file=@C:\path\to\image.jpg"
```

仅上传（返回 uploadId + 预览地址）：

```powershell
curl -X POST "http://localhost:8080/api/v1/upload" -F "file=@C:\path\to\image.jpg"
```

## DeepSeek 配置

配置文件：

- [application.yml](src/main/resources/application.yml)

默认关闭真实 AI 调用（走模拟识别）。

### 启用真实 DeepSeek 调用

1. 设置环境变量 `DEEPSEEK_API_KEY`：

```powershell
$env:DEEPSEEK_API_KEY="你的DeepSeekKey"
```

2. 修改 `application.yml`：

- `spring.ai.deepseek.chat.enabled: true`
- `app.ai.enabled: true`
- 如需视觉模型：把 `spring.ai.deepseek.chat.model` 改成 DeepSeek 平台实际提供的视觉模型名称

如果密钥曾被提交到代码仓库，需要在平台侧作废旧密钥并更换新密钥。

## 功能与页面

打开首页（静态页面）：

- <http://localhost:8080/>

支持：

- 上传课堂图片
- 调用 AI 识别（或模拟识别）
- 展示专注度评分、抬头率、行为占比图表、课堂小结

## 接口列表（后端）

统一前缀：`/api/v1`

| 方法 | 路径                              | 说明                                   |
| ---- | --------------------------------- | -------------------------------------- |
| POST | `/api/v1/upload`                  | 仅上传，返回 `uploadId` 与图片预览地址 |
| POST | `/api/v1/analyze`                 | 上传并识别（前端页面使用）             |
| POST | `/api/v1/analyze/{uploadId}`      | 对指定上传记录进行识别                 |
| GET  | `/api/v1/results/{analysisId}`    | 查询单次识别结果                       |
| GET  | `/api/v1/results`                 | 查询会话内全部识别记录                 |
| GET  | `/api/v1/stats`                   | 查询会话内汇总统计                     |
| GET  | `/api/v1/uploads/{uploadId}/file` | 图片预览                               |

## 常见问题

### 1) 提示 “JAVA_HOME environment variable is not defined correctly”

`mvnw.cmd` 依赖 `JAVA_HOME`。你可以在 PowerShell 临时设置（以你的 JDK 目录为准）：

```powershell
$env:JAVA_HOME="D:\java"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -v
```

也可以在 Windows 环境变量中设置 `JAVA_HOME`，并在 `Path` 增加 `%JAVA_HOME%\bin`。

### 2) 8080 端口被占用（Port 8080 was already in use）

换端口启动（例如 8081）：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

然后打开：

- <http://localhost:8081/>

停止服务（释放端口）：回到运行 `mvn spring-boot:run` 的终端，按 `Ctrl + C`。

### 3) IDEA 里提示 “The import xxx cannot be resolved”，但命令行能编译成功

通常是 IDEA 没有重新导入 Maven 依赖：

- 右侧 Maven 工具窗口 → Reload All Maven Projects
- Settings → Build Tools → Maven → JDK for importer / Runner JRE 选择你的 JDK（如 `D:\java`）
- 仍不行：File → Invalidate Caches / Restart

## 关于 application.yaml

项目中同时存在 `application.yml` 与 `application.yaml` 时可能造成配置理解混乱。  
本项目使用 `application.yml`。
