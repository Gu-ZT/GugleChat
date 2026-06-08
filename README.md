# GugleChat

实时通讯平台，支持文字（Markdown）/ 语音 / 视频聊天。覆盖网页端、桌面客户端、移动端（Android + 鸿蒙）。

## 功能特性

- **文字频道** — 文字聊天，支持 Markdown 语法高亮
- **语音频道** — 语音通话 + 文字，WebRTC P2P 连接
- **星形拓扑** — 自动选举网络最优用户为主机转发语音
- **NAT 检测** — 基于 ICE 检测 NAT 类型，优先 NAT1 用户当主机
- **语音活动检测** — 说话时头像绿圈高亮，支持自我监听
- **音频设备切换** — 悬浮闭麦按钮选择输入设备
- **跨平台** — Web / Tauri 桌面 / Android 原生 / 鸿蒙原生
- **可配置后端** — 前端可自行设置后端服务地址
- **可配置 TURN** — 设置页面填写 TURN 服务器信息

## 技术栈

| 层         | 技术                                |
|-----------|-----------------------------------|
| 后端框架      | Spring Boot 3.5 + Java 25         |
| 构建工具      | Gradle (Kotlin DSL)               |
| 数据库       | PostgreSQL + Redis                |
| ORM       | MyBatis Plus 3.5                  |
| 认证        | Spring Security + JWT (jjwt 0.13) |
| 实时消息      | STOMP over WebSocket              |
| 音视频       | WebRTC（P2P + 星形拓扑转发）              |
| 前端        | Vue 3 + TypeScript + Vite         |
| UI 组件     | Arco Design                       |
| 桌面端       | Tauri 2.x（规划中）                    |
| Android   | Kotlin + Jetpack Compose（规划中）     |
| HarmonyOS | ArkTS + ArkUI（规划中）                |

## 项目结构

```
GugleChat/
├── GugleChatBackend/              # Spring Boot 后端
│   ├── build.gradle.kts
│   └── src/main/java/dev/dubhe/gugle/chat/
│       ├── GugleChatApplication.java
│       ├── common/                # 枚举、DTO、异常处理
│       ├── auth/                  # JWT、Security、用户管理
│       ├── channel/               # 频道 CRUD、成员管理
│       ├── message/               # WebSocket 实时消息
│       ├── signaling/             # WebRTC 信令 + 房间管理
│       └── config/                # CORS 配置
├── GugleChatFrontend/             # Vue 3 前端
│   └── src/
│       ├── views/                 # Login, Register, Main, Settings
│       ├── components/            # layout/Sidebar, chat/*, voice/*
│       ├── stores/                # Pinia: auth, channel, message, websocket, rtc, theme, settings
│       ├── services/              # Axios API 封装
│       ├── router/                # Vue Router
│       └── types/                 # TypeScript 类型
├── GugleChatAndroid/              # Android 原生（规划中）
├── GugleChatHarmony/              # 鸿蒙原生（规划中）
└── docs/
    └── sql/                       # 数据库初始化脚本
```

## 快速开始

### 环境要求

- **JDK 25**
- **PostgreSQL 16+**
- **Redis 7+**
- **Node.js 20+**

### 1. 数据库

```bash
psql -h <host> -p <port> -U <user> -d <database> -f docs/sql/init.sql
```

### 2. 后端

```bash
cd GugleChatBackend

# 编辑 src/main/resources/application-config.yaml
# 配置数据库和 Redis 连接

./gradlew bootRun
```

默认端口 `8080`（可被 `application-config.yaml` 覆盖为 `3250`）。

### 3. 前端

```bash
cd GugleChatFrontend
npm install
npm run dev
```

前端运行在 `http://localhost:3000`，通过 Vite 代理转发 API 请求到后端。

> **连接远程后端**：登录页点击 ⚙ 按钮设置远程后端地址（如 `http://server.ztxy666.cn:3250`），即可直连远程服务器。

### 4. 构建

```bash
cd GugleChatBackend && ./gradlew build
cd GugleChatFrontend && npm run build
```

## API 概览

### REST API

| 方法     | 路径                                    | 说明     |
|--------|---------------------------------------|--------|
| POST   | `/api/auth/register`                  | 用户注册   |
| POST   | `/api/auth/login`                     | 用户登录   |
| GET    | `/api/auth/me`                        | 当前用户信息 |
| GET    | `/api/channels`                       | 所有频道   |
| POST   | `/api/channels`                       | 创建频道   |
| PUT    | `/api/channels/{id}`                  | 编辑频道   |
| DELETE | `/api/channels/{id}`                  | 删除频道   |
| GET    | `/api/channels/{id}/members`          | 频道成员   |
| POST   | `/api/channels/{id}/members`          | 添加成员   |
| DELETE | `/api/channels/{id}/members/{uid}`    | 移除成员   |
| GET    | `/api/channels/{id}/messages?before=` | 历史消息   |
| PUT    | `/api/messages/{id}`                  | 编辑消息   |
| DELETE | `/api/messages/{id}`                  | 删除消息   |

### WebSocket (STOMP)

| 方向        | 目标                             | 说明           |
|-----------|--------------------------------|--------------|
| SEND      | `/app/chat.send/{channelId}`   | 发送消息         |
| SUBSCRIBE | `/topic/channel.{channelId}`   | 订阅频道消息       |
| SEND      | `/app/chat.edit/{messageId}`   | 编辑消息         |
| SEND      | `/app/chat.delete/{messageId}` | 删除消息         |
| SEND      | `/app/rtc.join/{roomId}`       | 加入语音房间       |
| SEND      | `/app/rtc.leave/{roomId}`      | 离开语音房间       |
| SUBSCRIBE | `/user/queue/rtc`              | RTC 信令（私人队列） |

## 语音通话架构

- **星形拓扑**：第一个加入者成为 👑 主机，后续加入者只连接主机
- **智能选举**：基于 NAT 类型 + 带宽自动选最优主机（NAT1 权重最高）
- **自动切换**：主机离开时自动选举新主机
- **语音转发**：主机将收到的音轨转发给所有其他成员
- **NAT 检测**：通过 ICE candidate 类型判断网络环境
- **TURN 支持**：设置页面填写 TURN 服务器，用于对称 NAT 穿透

## 配置说明

### 后端地址（前端可配置）

登录页或设置页可设置后端地址，保存到 `localStorage`：

- 留空 → Vite 代理或同源
- 设置地址 → 所有 API/WebSocket 直连

### TURN 服务器

设置页 → Voice & Video → 填写 TURN 地址、用户名、密码（部署 coturn 后使用）。

### 应用配置

- `application.yaml` — 主配置
- `application-config.yaml` — 数据库、Redis、端口等

## 开发路线

- [x] Phase 1: 基础框架 + JWT 认证 + 文字频道 + Markdown 聊天
- [x] Phase 2: WebRTC 语音通话 + 信令服务 + 星形拓扑 + NAT 检测
- [ ] Phase 3: 屏幕共享 + 文件上传/预览
- [ ] Phase 4: Tauri 桌面客户端
- [ ] Phase 5: Android / 鸿蒙移动端
- [ ] Phase 6: 多人通话 SFU + 消息搜索 + 推送通知

## License

MIT
