# GugleChat

Real-time communication platform supporting text (Markdown) / voice / video / screen sharing. Web, desktop, and mobile (Android +
HarmonyOS).

## Features

- **Text Channels** — Markdown & syntax highlighting, image/video/audio preview
- **Voice Channels** — WebRTC P2P voice calls, star topology host forwarding, single-room enforcement
- **Video Calls** — Camera support, double-click card to expand
- **Screen Sharing** — Mutually exclusive with camera, preview & expand
- **File Upload** — Click to upload, inline image preview, video/audio embedded player
- **XSS Protection** — markdown-it raw HTML disabled + sanitize, backend filtering
- **Smart Host Election** — NAT type detection + bandwidth scoring, auto-select best host, manual NAT override
- **RNNoise AI Denoising** — WASM-based real-time neural network noise suppression, toggleable vs browser denoising
- **Audio Processing Pipeline** — Echo cancellation / noise suppression / RNNoise per-toggle, hot-swappable during calls
- **Voice Activity Detection** — Green ring on avatar when speaking, P2P latency & quality scores displayed
- **Microphone Controls** — Mute/unmute, volume slider, input device picker, accessible outside calls
- **Speaker Controls** — Mute/unmute, volume slider, output device picker
- **Self-Monitoring** — Hear your own mic independent of voice channels
- **Heartbeat Detection** — Auto-kick offline clients from voice rooms
- **Dark Mode** — Arco Design CSS variable theme toggle
- **Cross-Platform** — Web / Tauri Desktop / Android / HarmonyOS (planned)
- **Configurable Backend** — Frontend sets custom backend URL
- **Configurable TURN** — Settings page for TURN server

## Tech Stack

| Layer     | Technology                                  |
|-----------|---------------------------------------------|
| Backend   | Spring Boot 3.5 + Java 25                   |
| Build     | Gradle (Kotlin DSL)                         |
| Database  | PostgreSQL + Redis                          |
| ORM       | MyBatis Plus 3.5                            |
| Auth      | Spring Security + JWT (jjwt 0.13)           |
| Realtime  | STOMP over WebSocket                        |
| Media     | WebRTC (P2P + star topology + screen share) |
| Frontend  | Vue 3 + TypeScript + Vite                   |
| UI        | Arco Design                                 |
| Markdown  | markdown-it + highlight.js                  |
| Desktop   | Tauri 2.x                                   |
| Android   | Kotlin + Jetpack Compose (planned)          |
| HarmonyOS | ArkTS + ArkUI (planned)                     |

## Quick Start

### Prerequisites

- **JDK 25**, **PostgreSQL 16+**, **Redis 7+**, **Node.js 20+**

### 1. Database

```bash
psql -h <host> -p <port> -U <user> -d <database> -f docs/sql/init.sql
```

### 2. Backend

```bash
cd GugleChatBackend
# Edit application-config.yaml for DB/Redis
./gradlew bootRun
```

### 3. Frontend

```bash
cd GugleChatFrontend
npm install && npm run dev
```

> **Remote backend**: Click ⚙ on login page.

### 4. Desktop

```bash
cd GugleChatDesktop
cargo tauri dev      # Dev mode
cargo tauri build    # Package installer
```

### 5. Build

```bash
cd GugleChatBackend && ./gradlew build
cd GugleChatFrontend && npm run build
cd GugleChatDesktop && cargo tauri build
```

## API Overview

### REST API

| Method | Path                                  | Description           |
|--------|---------------------------------------|-----------------------|
| POST   | `/api/auth/register`                  | Register              |
| POST   | `/api/auth/login`                     | Login                 |
| GET    | `/api/channels`                       | All channels          |
| POST   | `/api/channels`                       | Create channel        |
| GET    | `/api/channels/{id}/messages?before=` | Message history       |
| POST   | `/api/files/upload`                   | Upload file           |
| GET    | `/api/files/{id}`                     | Download/preview file |
| GET    | `/api/channels/voice-users`           | Active voice users    |

### WebSocket (STOMP)

| Direction | Destination                  | Description      |
|-----------|------------------------------|------------------|
| SEND      | `/app/chat.send/{channelId}` | Send message     |
| SUBSCRIBE | `/topic/channel.{channelId}` | Channel messages |
| SEND      | `/app/rtc.join/{roomId}`     | Join voice room  |
| SUBSCRIBE | `/user/queue/rtc`            | RTC signaling    |
| SUBSCRIBE | `/topic/heartbeat`           | Heartbeat ping   |
| SEND      | `/app/heartbeat`             | Heartbeat pong   |

## Voice Architecture

- **Star Topology**: First joiner → 👑 host, others connect only to host
- **Smart Election**: NAT type + bandwidth, NAT1 weighted highest
- **NAT Override**: Manual NAT type selection, takes the worse of manual vs detected
- **Single-Room Enforcement**: Server prevents users from being in multiple voice channels
- **Auto Failover**: Host leaves → next best takes over; heartbeat timeout kicks offline
- **NAT Detection**: Multi-STUN server analysis, distinguishes Cone vs Symmetric NAT
- **TURN Support**: Settings page config for symmetric NAT traversal
- **RNNoise AI Denoising**: AudioWorklet + WASM, 48kHz real-time processing, mutually exclusive with browser denoising
- **Latency Display**: P2P + server latency shown on user cards, color-coded

## Configuration

### application-config.yaml

Environment-specific config (database, Redis, port):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://your-host:5432/gugle_chat  # PostgreSQL JDBC URL
    username: your-db-user
    password: your-db-password

  data:
    redis:
      host: your-redis-host        # Redis host
      port: 6379                   # Redis port
      database: 0                  # Redis DB number (0-15)
      password: your-redis-pwd     # Redis password

server:
  port: 8080                       # HTTP port
```

| Field                        | Description                       |
|------------------------------|-----------------------------------|
| `spring.datasource.url`      | PostgreSQL JDBC connection string |
| `spring.datasource.username` | Database username                 |
| `spring.datasource.password` | Database password                 |
| `spring.data.redis.host`     | Redis host address                |
| `spring.data.redis.port`     | Redis port                        |
| `spring.data.redis.database` | Redis DB index (0-15)             |
| `spring.data.redis.password` | Redis password                    |
| `server.port`                | HTTP server port                  |

### Frontend Settings

- **Backend URL**: Leave empty for proxy, set to connect directly
- **Theme**: Dark / Light toggle
- **NAT Type**: Manual override for auto-detection (Auto / NAT1-4), takes worse value
- **TURN Server**: URL, username, password for NAT traversal

## Roadmap

- [x] Phase 1: Core framework + JWT auth + text channels + Markdown
- [x] Phase 2: WebRTC voice + signaling + star topology + NAT detection
- [x] Phase 3: Screen sharing + file upload/preview + XSS protection
- [x] Phase 4: Tauri desktop client
- [ ] Phase 5: Android / HarmonyOS mobile apps
- [ ] Phase 6: SFU multi-party + message search + push notifications

## License

MIT
