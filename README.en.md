# GugleChat

Real-time communication platform supporting text (Markdown) / voice / video chat. Available on web, desktop, and mobile (Android +
HarmonyOS).

## Features

- **Text Channels** — Text chat with Markdown & syntax highlighting
- **Voice Channels** — Voice calls + text, WebRTC P2P connections
- **Star Topology** — Auto-elects best-network user as host for audio forwarding
- **NAT Detection** — ICE-based NAT type detection, prioritizes NAT1 users as host
- **Voice Activity Detection** — Green ring on avatar when speaking, self-monitoring support
- **Audio Device Picker** — Hover mute button to select input device
- **Cross-Platform** — Web / Tauri Desktop / Android Native / HarmonyOS Native
- **Configurable Backend** — Frontend can set custom backend server address
- **Configurable TURN** — TURN server settings in Settings page

## Tech Stack

| Layer     | Technology                              |
|-----------|-----------------------------------------|
| Backend   | Spring Boot 3.5 + Java 25               |
| Build     | Gradle (Kotlin DSL)                     |
| Database  | PostgreSQL + Redis                      |
| ORM       | MyBatis Plus 3.5                        |
| Auth      | Spring Security + JWT (jjwt 0.13)       |
| Realtime  | STOMP over WebSocket                    |
| Media     | WebRTC (P2P + star topology forwarding) |
| Frontend  | Vue 3 + TypeScript + Vite               |
| UI        | Arco Design                             |
| Desktop   | Tauri 2.x (planned)                     |
| Android   | Kotlin + Jetpack Compose (planned)      |
| HarmonyOS | ArkTS + ArkUI (planned)                 |

## Project Structure

```
GugleChat/
├── GugleChatBackend/              # Spring Boot backend
│   ├── build.gradle.kts
│   └── src/main/java/dev/dubhe/gugle/chat/
│       ├── GugleChatApplication.java
│       ├── common/                # Enums, DTOs, exception handling
│       ├── auth/                  # JWT, Security, user management
│       ├── channel/               # Channel CRUD, member management
│       ├── message/               # WebSocket real-time messaging
│       ├── signaling/             # WebRTC signaling + room management
│       └── config/                # CORS config
├── GugleChatFrontend/             # Vue 3 frontend
│   └── src/
│       ├── views/                 # Login, Register, Main, Settings
│       ├── components/            # layout/Sidebar, chat/*, voice/*
│       ├── stores/                # Pinia: auth, channel, message, websocket, rtc, theme, settings
│       ├── services/              # Axios API wrappers
│       ├── router/                # Vue Router
│       └── types/                 # TypeScript types
├── GugleChatAndroid/              # Android native (planned)
├── GugleChatHarmony/              # HarmonyOS native (planned)
└── docs/
    └── sql/                       # Database init scripts
```

## Quick Start

### Prerequisites

- **JDK 25**
- **PostgreSQL 16+**
- **Redis 7+**
- **Node.js 20+**

### 1. Database

```bash
psql -h <host> -p <port> -U <user> -d <database> -f docs/sql/init.sql
```

### 2. Backend

```bash
cd GugleChatBackend

# Edit src/main/resources/application-config.yaml
# Configure database and Redis connection

./gradlew bootRun
```

Default port `8080` (overridden to `3250` via `application-config.yaml`).

### 3. Frontend

```bash
cd GugleChatFrontend
npm install
npm run dev
```

Frontend runs at `http://localhost:3000`, proxying API requests via Vite.

> **Remote backend**: Click ⚙ on the login page to set a remote URL (e.g. `http://server.ztxy666.cn:3250`).

### 4. Build

```bash
cd GugleChatBackend && ./gradlew build
cd GugleChatFrontend && npm run build
```

## API Overview

### REST API

| Method | Path                                  | Description       |
|--------|---------------------------------------|-------------------|
| POST   | `/api/auth/register`                  | Register          |
| POST   | `/api/auth/login`                     | Login             |
| GET    | `/api/auth/me`                        | Current user info |
| GET    | `/api/channels`                       | All channels      |
| POST   | `/api/channels`                       | Create channel    |
| PUT    | `/api/channels/{id}`                  | Update channel    |
| DELETE | `/api/channels/{id}`                  | Delete channel    |
| GET    | `/api/channels/{id}/members`          | Channel members   |
| POST   | `/api/channels/{id}/members`          | Add member        |
| DELETE | `/api/channels/{id}/members/{uid}`    | Remove member     |
| GET    | `/api/channels/{id}/messages?before=` | Message history   |
| PUT    | `/api/messages/{id}`                  | Edit message      |
| DELETE | `/api/messages/{id}`                  | Delete message    |

### WebSocket (STOMP)

| Direction | Destination                    | Description                   |
|-----------|--------------------------------|-------------------------------|
| SEND      | `/app/chat.send/{channelId}`   | Send message                  |
| SUBSCRIBE | `/topic/channel.{channelId}`   | Subscribe to channel          |
| SEND      | `/app/chat.edit/{messageId}`   | Edit message                  |
| SEND      | `/app/chat.delete/{messageId}` | Delete message                |
| SEND      | `/app/rtc.join/{roomId}`       | Join voice room               |
| SEND      | `/app/rtc.leave/{roomId}`      | Leave voice room              |
| SUBSCRIBE | `/user/queue/rtc`              | RTC signaling (private queue) |

## Voice Architecture

- **Star Topology**: First joiner becomes 👑 host, others connect only to host
- **Smart Election**: Auto-selects best host based on NAT type + bandwidth (NAT1 weighted highest)
- **Auto Failover**: Host leaves → next best user becomes host
- **Audio Forwarding**: Host relays received audio tracks to all other members
- **NAT Detection**: ICE candidate type analysis for network environment
- **TURN Support**: Settings page for TURN server config (symmetric NAT traversal)

## Configuration

### Backend URL (frontend configurable)

Login or Settings page → set backend URL, persisted in `localStorage`. Leave empty for Vite proxy/same-origin.

### TURN Server

Settings → Voice & Video → fill in TURN URL, username, password (deploy coturn first).

### App Config

- `application.yaml` — Main configuration
- `application-config.yaml` — Database, Redis, port

## Roadmap

- [x] Phase 1: Core framework + JWT auth + text channels + Markdown chat
- [x] Phase 2: WebRTC voice calls + signaling + star topology + NAT detection
- [ ] Phase 3: Screen sharing + file upload/preview
- [ ] Phase 4: Tauri desktop client
- [ ] Phase 5: Android / HarmonyOS mobile apps
- [ ] Phase 6: SFU multi-party + message search + push notifications

## License

MIT
