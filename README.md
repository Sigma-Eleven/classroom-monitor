# 课堂专注度监测与学习行为分析助手（无数据库版）

基于 Spring Boot + Spring AI + DeepSeek（视觉/多模态）实现的课堂图片专注度与学习行为分析 Demo。  
项目不使用数据库：上传记录、识别结果、统计数据仅保存在内存中，重启后自动清空。

## 目录

- [环境要求](#环境要求)
- [快速启动](#快速启动)
- [DeepSeek 配置](#deepseek-配置不要把-key-写进仓库)
- [功能与页面](#功能与页面)
- [接口列表（后端）](#接口列表后端)
- [常见问题](#常见问题)

## 环境要求

- JDK 17+（建议 17；你本机是 Java 26 也可运行）
- Maven Wrapper（项目自带 `mvnw.cmd`，无需单独安装 Maven）

## 快速启动

在项目根目录运行：

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd spring-boot:run
```

启动成功后打开页面：

- <http://localhost:8080/>

## DeepSeek 配置（不要把 Key 写进仓库）

配置文件：

- [application.yml](src/main/resources/application.yml)

默认不启用真实 AI 调用（走模拟识别），避免未配置 Key 时启动失败。

### 启用真实 DeepSeek 调用

1. 设置环境变量（推荐，不会上传到 GitHub）：

```powershell
$env:DEEPSEEK_API_KEY="你的DeepSeekKey"
```

2. 修改 `application.yml`：

- `spring.ai.deepseek.chat.enabled: true`
- `app.ai.enabled: true`
- 如需视觉模型：把 `spring.ai.deepseek.chat.model` 改成 DeepSeek 平台实际提供的视觉模型名称

提示：如果你曾经把 Key 明文写进文件并提交过，请立刻作废旧 Key 并生成新 Key。

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

建议永久设置：Windows 环境变量中设置 `JAVA_HOME=D:\java`，并在 `Path` 增加 `%JAVA_HOME%\bin`。

### 2) 8080 端口被占用（Port 8080 was already in use）

换端口启动（例如 8081）：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

然后打开：

- <http://localhost:8081/>

### 3) IDEA 里提示 “The import xxx cannot be resolved”，但命令行能编译成功

通常是 IDEA 没有重新导入 Maven 依赖：

- 右侧 Maven 工具窗口 → Reload All Maven Projects
- Settings → Build Tools → Maven → JDK for importer / Runner JRE 选择你的 JDK（如 `D:\java`）
- 仍不行：File → Invalidate Caches / Restart

## 关于 application.yaml

项目中同时存在 `application.yml` 与 `application.yaml` 时可能造成配置理解混乱。  
建议仅保留 `application.yml`，并避免在 `application.yaml` 中放任何配置项。
