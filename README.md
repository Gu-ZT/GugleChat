# GugleChat

实时通讯平台，支持文字（Markdown）/ 语音 / 屏幕共享 / 视频聊天。覆盖网页端、桌面客户端、移动端（Android + 鸿蒙）。

## 功能特性

- **文字频道** — 仅文字聊天，支持 Markdown 语法高亮
- **语音频道** — 文字 + 语音 + 视频 + 屏幕共享（WebRTC）
- **文件支持** — 上传文件、嵌入链接、图片/视频/音频预览
- **跨平台** — Web / Tauri 桌面 / Android 原生 / 鸿蒙原生
- **可配置后端** — 前端/APP 端可自行设置后端服务地址

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5 + Java 25 |
| 构建工具 | Gradle (Kotlin DSL) |
| 数据库 | PostgreSQL + Redis |
| ORM | MyBatis Plus 3.5 |
| 安全 | Spring Security + JWT (jjwt 0.13) |
| 实时消息 | STOMP over WebSocket |
| 音视频 | WebRTC（计划中） |
| 前端 | Vue 3 + TypeScript + Vite |
| UI 组件 | Arco Design |
| 桌面端 | Tauri 2.x（计划中） |
| Android | Kotlin + Jetpack Compose（计划中） |
| HarmonyOS | ArkTS + ArkUI（计划中） |

## 项目结构

```
GugleChat/
├── GugleChatBackend/              # Spring Boot 后端
│   ├── build.gradle.kts
│   └── src/main/java/dev/dubhe/gugle/chat/
│       ├── GugleChatApplication.java
│       ├── common/                # 公共：枚举、DTO、异常处理
│       ├── auth/                  # 认证：JWT、Security、用户管理
│       ├── channel/               # 频道：CRUD、成员管理
│       ├── message/               # 消息：WebSocket、实时收发
│       ├── file/                  # 文件：上传、下载、预览
│       ├── signaling/             # WebRTC 信令
│       └── config/                # CORS 等配置
├── GugleChatFrontend/             # Vue 3 前端
│   └── src/
│       ├── views/                 # 页面：Login, Register, Main, Settings
│       ├── components/            # 组件：layout, chat, channel
│       ├── stores/                # Pinia：auth, channel, message, websocket, settings
│       ├── services/              # Axios API 封装
│       ├── router/                # Vue Router
│       └── types/                 # TypeScript 类型
├── GugleChatAndroid/              # Android 原生（规划中）
├── GugleChatHarmony/              # 鸿蒙原生（规划中）
├── GugleChatDesktop/              # Tauri 桌面（规划中）
└── docs/
    └── sql/                       # 数据库初始化脚本
```

## 快速开始

### 环境要求

- **JDK 25**
- **PostgreSQL 16+**
- **Redis 7+**
- **Node.js 20+**
- **Gradle** (使用项目自带的 `gradlew`)

### 1. 数据库

```bash
# 连接 PostgreSQL 执行建表脚本
psql -h <host> -p <port> -U <user> -d <database> -f docs/sql/init.sql
```

### 2. 后端

```bash
cd GugleChatBackend

# 编辑 src/main/resources/application-config.yaml
# 配置数据库和 Redis 连接信息

# 启动
./gradlew bootRun
```

服务默认端口 `8080`（可被 `application-config.yaml` 覆盖为 `3250`）。

### 3. 前端

```bash
cd GugleChatFrontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端运行在 `http://localhost:3000`，通过 Vite 代理转发 API 请求到后端。

> **连接远程后端**：在登录页点击 ⚙ 按钮，设置后端地址（如 `http://server.ztxy666.cn:3250`），即可直连远程服务器，无需本地启动后端。

### 4. 构建

```bash
# 后端
cd GugleChatBackend && ./gradlew build

# 前端
cd GugleChatFrontend && npm run build
```

## API 概览

### REST API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/me` | 当前用户信息 |
| GET | `/api/channels` | 频道列表 |
| POST | `/api/channels` | 创建频道 |
| PUT | `/api/channels/{id}` | 编辑频道 |
| DELETE | `/api/channels/{id}` | 删除频道 |
| GET | `/api/channels/{id}/members` | 频道成员 |
| POST | `/api/channels/{id}/members` | 添加成员 |
| DELETE | `/api/channels/{id}/members/{uid}` | 移除成员 |
| GET | `/api/channels/{id}/messages?before=` | 历史消息 |
| PUT | `/api/messages/{id}` | 编辑消息 |
| DELETE | `/api/messages/{id}` | 删除消息 |

### WebSocket (STOMP)

| 方向 | 目标 | 说明 |
|---|---|---|
| SEND | `/app/chat.send/{channelId}` | 发送消息 |
| SUBSCRIBE | `/topic/channel.{channelId}` | 订阅频道消息 |
| SEND | `/app/chat.edit/{messageId}` | 编辑消息 |
| SEND | `/app/chat.delete/{messageId}` | 删除消息 |

## 配置说明

### 后端 URL（前端可配置）

前端在登录页或设置页可自行设置后端地址，保存在 `localStorage` 中：
- 留空 → 使用 Vite 代理（开发）或同源（生产）
- 设置地址 → 所有 API/WebSocket 直连该地址

### 应用配置

- `GugleChatBackend/src/main/resources/application.yaml` — 主配置
- `GugleChatBackend/src/main/resources/application-config.yaml` — 环境相关配置（数据库、Redis、端口）

## 开发计划

- [x] Phase 1: 基础框架 + JWT 认证 + 文字频道 + Markdown 聊天
- [ ] Phase 2: WebRTC 音视频通话 + 语音频道
- [ ] Phase 3: 屏幕共享 + 文件上传/预览
- [ ] Phase 4: Tauri 桌面客户端
- [ ] Phase 5: Android / 鸿蒙移动端
- [ ] Phase 6: 多人通话 (SFU) + 消息搜索 + 推送通知

## License

MIT
