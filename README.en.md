# GugleChat

Real-time communication platform supporting text (Markdown) / voice / screen sharing / video chat. Available on web, desktop, and mobile (Android + HarmonyOS).

## Features

- **Text Channels** — Text-only chat with Markdown & syntax highlighting
- **Voice Channels** — Text + Voice + Video + Screen sharing (WebRTC)
- **File Support** — File upload, link embedding, image/video/audio preview
- **Cross-Platform** — Web / Tauri Desktop / Android Native / HarmonyOS Native
- **Configurable Backend** — Frontend/apps can set their own backend server address

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5 + Java 25 |
| Build | Gradle (Kotlin DSL) |
| Database | PostgreSQL + Redis |
| ORM | MyBatis Plus 3.5 |
| Auth | Spring Security + JWT (jjwt 0.13) |
| Realtime | STOMP over WebSocket |
| Media | WebRTC (planned) |
| Frontend | Vue 3 + TypeScript + Vite |
| UI | Arco Design |
| Desktop | Tauri 2.x (planned) |
| Android | Kotlin + Jetpack Compose (planned) |
| HarmonyOS | ArkTS + ArkUI (planned) |

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
│       ├── message/               # WebSocket, real-time messaging
│       ├── file/                  # File upload/download/preview
│       ├── signaling/             # WebRTC signaling
│       └── config/                # CORS, app config
├── GugleChatFrontend/             # Vue 3 frontend
│   └── src/
│       ├── views/                 # Pages: Login, Register, Main, Settings
│       ├── components/            # Components: layout, chat, channel
│       ├── stores/                # Pinia: auth, channel, message, websocket, settings
│       ├── services/              # Axios API wrappers
│       ├── router/                # Vue Router
│       └── types/                 # TypeScript types
├── GugleChatAndroid/              # Android native (planned)
├── GugleChatHarmony/              # HarmonyOS native (planned)
├── GugleChatDesktop/              # Tauri desktop (planned)
└── docs/
    └── sql/                       # Database init scripts
```

## Quick Start

### Prerequisites

- **JDK 25**
- **PostgreSQL 16+**
- **Redis 7+**
- **Node.js 20+**
- **Gradle** (use the bundled `gradlew`)

### 1. Database

```bash
# Connect to PostgreSQL and run the init script
psql -h <host> -p <port> -U <user> -d <database> -f docs/sql/init.sql
```

### 2. Backend

```bash
cd GugleChatBackend

# Edit src/main/resources/application-config.yaml
# Configure database and Redis connection

# Start
./gradlew bootRun
```

Default port is `8080` (can be overridden to `3250` via `application-config.yaml`).

### 3. Frontend

```bash
cd GugleChatFrontend

# Install dependencies
npm install

# Start dev server
npm run dev
```

Frontend runs at `http://localhost:3000`, proxying API requests to the backend via Vite.

> **Remote backend**: Click the ⚙ button on the login page to set a remote backend URL (e.g. `http://server.ztxy666.cn:3250`). This bypasses the local proxy and connects directly to the remote server.

### 4. Build

```bash
# Backend
cd GugleChatBackend && ./gradlew build

# Frontend
cd GugleChatFrontend && npm run build
```

## API Overview

### REST API

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register |
| POST | `/api/auth/login` | Login |
| GET | `/api/auth/me` | Current user info |
| GET | `/api/channels` | List channels |
| POST | `/api/channels` | Create channel |
| PUT | `/api/channels/{id}` | Update channel |
| DELETE | `/api/channels/{id}` | Delete channel |
| GET | `/api/channels/{id}/members` | Channel members |
| POST | `/api/channels/{id}/members` | Add member |
| DELETE | `/api/channels/{id}/members/{uid}` | Remove member |
| GET | `/api/channels/{id}/messages?before=` | Message history |
| PUT | `/api/messages/{id}` | Edit message |
| DELETE | `/api/messages/{id}` | Delete message |

### WebSocket (STOMP)

| Direction | Destination | Description |
|---|---|---|
| SEND | `/app/chat.send/{channelId}` | Send message |
| SUBSCRIBE | `/topic/channel.{channelId}` | Subscribe to channel |
| SEND | `/app/chat.edit/{messageId}` | Edit message |
| SEND | `/app/chat.delete/{messageId}` | Delete message |

## Configuration

### Backend URL (frontend configurable)

The frontend allows setting a custom backend URL via the login page or settings page, persisted in `localStorage`:
- Leave empty → uses Vite proxy (dev) or same-origin (production)
- Set a URL → all API/WebSocket requests go directly to that address

### App Config

- `GugleChatBackend/src/main/resources/application.yaml` — Main configuration
- `GugleChatBackend/src/main/resources/application-config.yaml` — Environment-specific (database, Redis, port)

## Roadmap

- [x] Phase 1: Core framework + JWT auth + text channels + Markdown chat
- [ ] Phase 2: WebRTC audio/video calls + voice channels
- [ ] Phase 3: Screen sharing + file upload/preview
- [ ] Phase 4: Tauri desktop client
- [ ] Phase 5: Android / HarmonyOS mobile apps
- [ ] Phase 6: Multi-party calls (SFU) + message search + push notifications

## License

MIT
