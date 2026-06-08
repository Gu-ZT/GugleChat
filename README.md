# GugleChat

实时通讯平台，支持文字（Markdown）/ 语音 / 视频 / 屏幕共享聊天。覆盖网页端、桌面客户端、移动端（Android + 鸿蒙）。

## 功能特性

- **文字频道** — Markdown 语法高亮，支持图片/视频/音频预览
- **语音频道** — WebRTC P2P 语音通话，星形拓扑主机转发
- **视频通话** — 摄像头支持，双击卡片放大
- **屏幕共享** — 与摄像头互斥，支持预览和放大
- **文件上传** — 拖拽/点击上传，图片内联渲染，视频/音频嵌入式播放
- **XSS 防护** — 前端 markdown-it 禁用 raw HTML + sanitize，后端过滤
- **智能主机选举** — NAT 类型检测 + 带宽评分，自动选最优主机
- **语音活动检测** — 说话时头像绿圈高亮，支持自我监听
- **音频设备切换** — 悬浮按钮选择输入设备，持久化存储
- **心跳检测** — 客户端离线自动踢出语音房间
- **暗黑模式** — Arco Design 主题切换
- **跨平台** — Web / Tauri 桌面 / Android 原生 / 鸿蒙原生（规划中）
- **可配置后端** — 前端/APP 自行设置后端地址
- **可配置 TURN** — 设置页面填写 TURN 服务器

## 技术栈

| 层         | 技术                                |
|-----------|-----------------------------------|
| 后端框架      | Spring Boot 3.5 + Java 25         |
| 构建工具      | Gradle (Kotlin DSL)               |
| 数据库       | PostgreSQL + Redis                |
| ORM       | MyBatis Plus 3.5                  |
| 认证        | Spring Security + JWT (jjwt 0.13) |
| 实时消息      | STOMP over WebSocket              |
| 音视频       | WebRTC（P2P + 星形拓扑转发 + 屏幕共享）       |
| 前端        | Vue 3 + TypeScript + Vite         |
| UI 组件     | Arco Design                       |
| Markdown  | markdown-it + highlight.js        |
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
│       ├── common/                # 枚举、DTO、异常处理、XSS 过滤
│       ├── auth/                  # JWT、Security、用户管理
│       ├── channel/               # 频道 CRUD、成员管理
│       ├── message/               # WebSocket 实时消息
│       ├── file/                  # 文件上传/下载
│       ├── signaling/             # WebRTC 信令 + 心跳检测
│       └── config/                # CORS 配置
├── GugleChatFrontend/             # Vue 3 前端
│   └── src/
│       ├── views/                 # Login, Register, Main, Settings
│       ├── components/            # layout, chat, voice, file
│       ├── stores/                # Pinia: auth, channel, message, websocket, rtc, theme, settings
│       ├── services/              # Axios API + 文件上传
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
# 编辑 src/main/resources/application-config.yaml 配置数据库和 Redis
./gradlew bootRun
```

### 3. 前端

```bash
cd GugleChatFrontend
npm install
npm run dev
```

> **连接远程后端**：登录页点击 ⚙ 设置远程地址。

### 4. 构建

```bash
cd GugleChatBackend && ./gradlew build
cd GugleChatFrontend && npm run build
```

## API 概览

### REST API

| 方法     | 路径                                    | 说明       |
|--------|---------------------------------------|----------|
| POST   | `/api/auth/register`                  | 用户注册     |
| POST   | `/api/auth/login`                     | 用户登录     |
| GET    | `/api/auth/me`                        | 当前用户信息   |
| GET    | `/api/channels`                       | 所有频道     |
| POST   | `/api/channels`                       | 创建频道     |
| PUT    | `/api/channels/{id}`                  | 编辑频道     |
| DELETE | `/api/channels/{id}`                  | 删除频道     |
| GET    | `/api/channels/{id}/members`          | 频道成员     |
| POST   | `/api/channels/{id}/members`          | 添加成员     |
| DELETE | `/api/channels/{id}/members/{uid}`    | 移除成员     |
| GET    | `/api/channels/{id}/messages?before=` | 历史消息     |
| PUT    | `/api/messages/{id}`                  | 编辑消息     |
| DELETE | `/api/messages/{id}`                  | 删除消息     |
| POST   | `/api/files/upload`                   | 上传文件     |
| GET    | `/api/files/{id}`                     | 下载/预览文件  |
| GET    | `/api/channels/voice-users`           | 当前语音房间用户 |

### WebSocket (STOMP)

| 方向        | 目标                           | 说明     |
|-----------|------------------------------|--------|
| SEND      | `/app/chat.send/{channelId}` | 发送消息   |
| SUBSCRIBE | `/topic/channel.{channelId}` | 订阅频道消息 |
| SEND      | `/app/rtc.join/{roomId}`     | 加入语音房间 |
| SEND      | `/app/rtc.leave/{roomId}`    | 离开语音房间 |
| SUBSCRIBE | `/user/queue/rtc`            | RTC 信令 |
| SUBSCRIBE | `/topic/heartbeat`           | 心跳检测   |
| SEND      | `/app/heartbeat`             | 心跳响应   |

## 语音通话架构

- **星形拓扑**：首个人成为 👑 主机，其他人只连主机
- **智能选举**：NAT 类型 + 带宽自动选举（NAT1 > NAT2-3 > NAT4）
- **自动切换**：主机离开自动选新主机，心跳超时自动踢出
- **NAT 检测**：多 STUN 服务器检测，区分 Cone NAT / Symmetric NAT
- **TURN 支持**：设置页填写 TURN 服务器穿透对称 NAT

## 配置说明

### application.yml（主配置）

Spring Boot 主配置文件，包含 MyBatis Plus、JWT、文件上传等应用级配置。

### application-config.yaml（环境配置）

部署到不同环境时修改此文件：

```yaml
spring:
  datasource:
    # PostgreSQL 连接 URL
    # 格式: jdbc:postgresql://<主机>:<端口>/<数据库>
    url: jdbc:postgresql://your-host:5432/gugle_chat
    username: your-db-user
    password: your-db-password

  data:
    redis:
      host: your-redis-host        # Redis 主机
      port: 6379                   # Redis 端口
      database: 0                  # 数据库编号 (0-15)
      password: your-redis-pwd     # Redis 密码（无密码可删除此行）

server:
  port: 8080                       # HTTP 端口
```

| 字段                           | 说明                  |
|------------------------------|---------------------|
| `spring.datasource.url`      | PostgreSQL JDBC 连接串 |
| `spring.datasource.username` | 数据库用户名              |
| `spring.datasource.password` | 数据库密码               |
| `spring.data.redis.host`     | Redis 主机地址          |
| `spring.data.redis.port`     | Redis 端口            |
| `spring.data.redis.database` | Redis DB 编号 (0-15)  |
| `spring.data.redis.password` | Redis 密码            |
| `server.port`                | 后端 HTTP 服务端口        |

### 前端配置

Settings 页面可配置：

- **后端地址**：留空使用代理，填入直连
- **主题**：暗黑 / 明亮
- **TURN 服务器**：URL、用户名、密码（部署 coturn 后填写）

## 开发路线

- [x] Phase 1: 基础框架 + JWT 认证 + 文字频道 + Markdown
- [x] Phase 2: WebRTC 语音 + 信令 + 星形拓扑 + NAT 检测
- [x] Phase 3: 屏幕共享 + 文件上传/预览 + XSS 防护
- [ ] Phase 4: Tauri 桌面客户端
- [ ] Phase 5: Android / 鸿蒙移动端
- [ ] Phase 6: SFU 多人通话 + 消息搜索 + 推送通知

## License

MIT
