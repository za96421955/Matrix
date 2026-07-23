[English](README_EN.md) | [中文](README.md)

# Matrix

**Only supports Deepseek** — An AI conversation + command execution system powered by the Deepseek API.

---

## Homepage
![index.png](index.png)
### Modes
- **Plan:** Build a task plan
- **Execute:** Execute tasks directly
- **Task:** Task list (TAG + Observer architecture)
- **Goal:** Task list (Graph + Observer architecture)

## Usage Statistics
![use.png](use.png)

## Directory Structure

```
├── install/                  # Installation & deployment
│   ├── install.sh            # One-click installation script
│   ├── bin/                  # Service management scripts
│   │   ├── start.sh          # Start services (includes JDK check, process management)
│   │   ├── stop.sh           # Stop services
│   │   ├── restart.sh        # Restart services
│   │   └── proxy_server.py   # WebUI proxy server (Python3)
│   └── config/
│       └── application.yml   # Runtime configuration (port, datasource, API Key, etc.)
│
├── matrix-local/             # [Core] Standalone server
│   ├── data/
│   │   ├── schema.sql        # Database table schema SQL (SQLite)
│   │   └── matrix-local.db   # SQLite data file (auto-generated)
│   └── src/
│       ├── LocalApplication.java    # Application entry point (main class)
│       ├── config/                  # Datasource configuration (HikariCP + SQLite)
│       ├── dal/
│       │   ├── entity/
│       │   │   ├── LocalCache.java  # Local cache entity (replaces Redis)
│       │   │   └── LocalTimer.java  # Local scheduled task entity
│       │   └── mapper/
│       │       ├── LocalCacheMapper.java  # Cache CRUD (includes UPSERT)
│       │       └── LocalTimerMapper.java  # Scheduled task CRUD
│       ├── service/
│       │   └── LocalCacheService.java     # Local cache service
│       └── primary/
│           ├── SyncExecutor.java          # Local command executor (replaces MQTT remote execution)
│           ├── LocalTaskComplete.java     # Local task completion handler
│           └── SqlServiceCache.java       # Local cache management (with Hash/Set support)
│
├── matrix-client/            # Terminal client (runs on devices)
│   └── src/
│       ├── ClientApplication.java    # Application entry point
│       ├── mqtt/
│       │   ├── MqttConnection.java   # MQTT connection management
│       │   ├── MqttConsumer.java     # MQTT message consumption (receive command -> execute -> acknowledge)
│       │   ├── MqttSubscriber.java   # MQTT subscription
│       │   └── MqttRunner.java       # MQTT auto-start (switch: matrix.mqtt.enabled)
│       └── service/
│           ├── CommandExecutor.java          # Command execution interface
│           ├── impl/PCCommandExecutor.java   # PC command executor (bash/cmd/powershell)
│           ├── Fingerprint.java              # Device fingerprint interface
│           └── impl/PCFingerprintImpl.java   # PC fingerprint generation (UUID+MAC+hostname -> SHA256)
│
├── matrix-cloud/             # Cloud server (multi-module)
│   ├── matrix-cloud-gateway/ # Gateway
│   │   └── src/
│   │       ├── GatewayApplication.java      # Entry point
│   │       ├── filter/GatewayLogFilter.java # Request logging
│   │       └── filter/LimitGlobalFilter.java # Rate limiting
│   ├── matrix-cloud-service/ # Business services
│   │   └── src/
│   │       ├── ServiceApplication.java      # Entry point
│   │       ├── controller/                  # API endpoints (see below)
│   │       ├── service/                     # Business logic
│   │       │   ├── chat/                    # Conversations/messages/sessions
│   │       │   ├── user/                    # Users/clients
│   │       │   ├── agent/                   # Modes/skills
│   │       │   ├── task/                    # Task management
│   │       │   ├── app/                     # Script language executor
│   │       │   └── tool/                    # Tools
│   │       └── mqtt/                        # MQTT publish/subscribe
│   └── matrix-cloud-common/  # Common module (DTOs/constants/utilities)
│
├── matrix-view/              # Frontend interface
│   └── webui/                # React + Vite + TypeScript + Tailwind
│       ├── src/
│       │   ├── App.tsx                # Main application (includes ErrorBoundary)
│       │   ├── components/
│       │   │   ├── ChatArea.tsx       # Main chat area
│       │   │   ├── Sidebar.tsx        # Sidebar (session list)
│       │   │   ├── InputBar.tsx       # Input bar (model/thinking mode/send)
│       │   │   ├── MessageBubble.tsx  # Message bubble
│       │   │   ├── TaskAuthModal.tsx  # Task authorization modal
│       │   │   ├── ApiKeyModal.tsx    # API Key settings
│       │   │   └── ...
│       │   ├── store/                 # Zustand state management
│       │   │   ├── chatStore.ts       # Chat state (sessions/messages/streaming)
│       │   │   ├── apiKeyStore.ts     # API Key
│       │   │   ├── taskAuthStore.ts   # Task authorization
│       │   │   └── ...
│       │   └── utils/
│       │       ├── api.ts             # Chat SSE interface
│       │       └── apiClient.ts       # HTTP client wrapper
│       └── vite.config.ts            # Dev proxy to localhost:10906
│
├── settings/                  # Local configuration files
│   ├── risk-level.yml        # Risk level configuration
│   ├── skill/                # Skills directory (e.g., pdf, query-typhoon)
│   │   └── pdf/SKILL.md      # PDF skill definition
│   ├── app/                  # Script language executor configuration
│   │   ├── shell/            # sh, bash, zsh, ksh, csh
│   │   ├── script/           # python, node, lua, ruby, php, perl, R
│   │   ├── compile/          # java, go, rust, kotlin
│   │   ├── text/             # sed, awk
│   │   └── windows/          # powershell, batch, vbscript
│   └── MEMORY.md             # Service memory file
│
├── logs/                     # Log directory
│   ├── info/                 # General logs
│   └── error/                # Error logs
│
├── pom.xml                   # Maven parent POM (module aggregation)
└── wiki/                     # Documentation (reserved)
```

---

## Quick Start

### Deepseek API
https://platform.deepseek.com/api_keys

### One-Click Installation

```bash
curl -fsSL https://gitee.com/za96421955/matrix/raw/release/latest/install/install.sh | bash
```

Installation process:
1. Detect `DEEPSEEK_API_KEY` (prompts for input if not set)
2. Download JDK21 (bundled package, auto-extracted to ~/.jdks)
3. Download and merge JAR split volumes (matrix-local-1.0.2.jar)
4. Download WebUI static assets
5. Auto-start the service

After installation, use the `matrix` command to manage services:

| Command | Function |
|---------|----------|
| `matrix start` | Start backend service + WebUI |
| `matrix stop` | Stop service |
| `matrix restart` | Restart service |
| `matrix status` | Check running status |
| `matrix logs` | View backend logs |
| `matrix webui-logs` | View WebUI logs |
| `matrix update` | Update/upgrade |
| `matrix uninstall` | Uninstall |

Installation directory: `~/.matrix/local/`

---

## Module Documentation

---

### 1. local Module (Standalone Core)

**matrix-local** is the core module of the project. It consolidates all features into a single application for out-of-the-box usage.

#### How to Start

```bash
# Method 1: Using the matrix command (after installation)
matrix start

# Method 2: Run the JAR directly
java -jar matrix-local-1.0.2.jar

# Method 3: IDEA development
# Run the LocalApplication.java main class
```

#### Access After Startup

```
Backend API: http://localhost:10906/v1/
WebUI: http://localhost:10908/
```

#### Data Storage

The local module uses **SQLite** instead of MySQL + Redis. Data file location:

```
~/.matrix/local/data/matrix-local.db
```

All data is persisted automatically without the need for additional database configuration.

Database tables (auto-created, see `matrix-local/data/schema.sql` for details):

| Table | Description |
|-------|-------------|
| `tbl_user_info` | User information |
| `tbl_client_info` | Client devices |
| `tbl_session_info` | Chat sessions |
| `tbl_message_info` | Chat messages |
| `tbl_user_api_key` | API Keys |
| `tbl_task_info` | Task records |
| `tbl_local_cache` | Local cache (replaces Redis) |
| `tbl_local_timer` | Local scheduled tasks |

#### Local Cache (Replaces Redis)

The local module has a built-in cache system `LocalCacheService` based on SQLite, supporting:

- **KV Storage**: `put(key, value, ttl)` / `get(key)` / `delete(key)` / `keys(pattern)`
- **TTL Expiry**: Set expiration time in seconds (0 or negative means no expiry)
- **Hash Operations**: Via `SqlServiceCache.getHash()`, stored as JSON-serialized objects
- **Set Operations**: Via `SqlServiceCache.getSet()`, stored as JSON-serialized arrays
- **Distributed Lock**: `lock(key, ttl)` — a simple mutex lock based on cache

> Note: In local mode, all cache data is stored in the SQLite database and persists across restarts. Switch to cloud mode if Redis is required.

#### Local Command Execution

The local module's `SyncExecutor` executes CLI commands directly on the local machine (via `PCCommandExecutor`), without requiring MQTT remote calls.

- macOS/Linux: executes using bash
- Windows: executes using cmd.exe (supports specifying `dir` and `command` when using `json` format parameters)

#### Risk Levels

The `settings/risk-level.yml` configuration file controls the risk level of various operations:

| Level | Meaning | Description |
|-------|---------|-------------|
| -1 | Forbidden | Dangerous operations like `shutdown`, `sudo`, `rm`, `mkfs`, etc. |
| 0 | No Risk | Safe operations like `ls`, `pwd`, `cat`, `grep`, `echo`, etc. |
| 1 | Low Risk | Default level for CLI, regular operations |
| 2 | Medium Risk | |
| 3 | High Risk | Default level for `skill` and `app` |

In the WebUI, you can control the authorization level of the current session via the authorization level dropdown:

| Authorization Level | Meaning |
|--------------------|---------|
| -1 | Execution forbidden |
| 0 | Safe operations only |
| 1 | Regular operations allowed |
| 2 | Sensitive operations allowed |
| 3 | Always allow |

---

### 2. client Module (Terminal Client)

**matrix-client** is a terminal program that runs on devices. Key features:

#### Core Features

| Feature | Description |
|---------|-------------|
| PC Command Execution | `PCCommandExecutor`: executes bash/cmd/powershell commands locally |
| Device Fingerprint | `PCFingerprintImpl`: generates a unique device ID using UUID + MAC address + hostname (`ha-ce-pc-{sha256}`) |
| MQTT Remote Control | Optional. Receives MQTT messages to execute commands and send acknowledgments (switch: `matrix.mqtt.enabled=true/false`) |
| Service Registration/Heartbeat | Registers device info (device name, OS info, etc.) with the server on startup and sends periodic heartbeats |

#### Device Fingerprint Generation Logic

1. Retrieve system UUID (Mac: `system_profiler SPHardwareDataType` / Linux: `/sys/class/dmi/id/product_uuid` / Windows: WMI)
2. Retrieve primary network card MAC address
3. Concatenate and SHA-256 hash
4. Final ID format: `ha-ce-pc-{hash}`

#### Configuration (application.yml)

```yaml
matrix:
  client:
    name: My PC             # Device display name
    desc: Personal Office    # Device description
  mqtt:
    enabled: false          # Whether MQTT is enabled (usually disabled in local mode)
```

---

### 3. cloud Module (Cloud Server)

**matrix-cloud** is a multi-module project with three sub-modules:

#### matrix-cloud-gateway (Gateway)

- Request logging (`GatewayLogFilter`)
- Rate limiting (`LimitGlobalFilter`)
- Health check: `GET /health/check`

#### matrix-cloud-service (Business Services)

Full REST API with path prefix `/v1`.

##### Chat Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/chat/submit` | Submit a conversation, returns sessionId, processes asynchronously |
| POST | `/v1/chat/completions` | SSE streaming conversation (get real-time inference content) |

**Example submit conversation request body:**

```json
{
  "messages": [{"role": "user", "content": "Hello"}],
  "model": "deepseek-v4-flash",
  "agent": "plan_agent",
  "pattern": "auto",
  "sessionId": 1,
  "clientId": "ha-ce-pc-xxx",
  "itemPath": "/Users/xxx/project",
  "thinking": {"type": "enabled"},
  "reasoning_effort": "high",
  "max_tokens": 8192
}
```

##### Session Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/session/page/{pageNum}/{pageSize}` | Paginated session list |
| GET | `/v1/session/{sessionId}` | Get session details |
| GET | `/v1/session/isCompletions/{sessionId}` | Check if session is still generating |
| GET | `/v1/session/stop/{sessionId}` | Stop generation |
| PUT | `/v1/session/updateTitle/{sessionId}` | Rename session |
| PUT | `/v1/session/updateAgent/{sessionId}` | Update session's bound skill |
| PUT | `/v1/session/updateAuthLevel/{sessionId}` | Update session authorization level |
| DELETE | `/v1/session/{sessionId}` | Delete session |

##### Message Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/message/page/{sessionId}/{pageNum}/{pageSize}` | Paginated messages (all) |
| GET | `/v1/message/page/chat/{sessionId}/{pageNum}/{pageSize}` | Paginated messages (conversation only) |
| GET | `/v1/message/{sessionId}/{messageId}` | Get single message |
| DELETE | `/v1/message/{sessionId}/{messageId}` | Delete single message |

##### User Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/user/checkApiKey` | Validate API Key |
| GET | `/v1/user/getAuthLevel` | Get user's default authorization level |
| DELETE | `/v1/user/ak` | Delete API Key |
| POST | `/v1/user/ak/enable` | Enable API Key |
| POST | `/v1/user/ak/disable` | Disable API Key |

##### Client Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/client/list` | List online devices |
| POST | `/v1/client/register/{clientId}` | Device registration |
| POST | `/v1/client/heartbeat/{clientId}` | Heartbeat report |
| POST | `/v1/client/checkOnline/{clientId}` | Check device online status |

##### Agent Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/agent/list` | List available skills |
| POST | `/v1/agent/call` | SSE skill invocation |

##### Task Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/task/{taskId}` | Get task details |
| POST | `/v1/task/ack/{taskId}` | Task acknowledgment |
| GET | `/v1/task/waitingAuthList` | List tasks awaiting authorization |
| POST | `/v1/task/auth/{taskId}` | Authorize/reject task |

#### matrix-cloud-common (Common Module)

Provides globally shared DTOs, constants, enums, and utility classes used by both service and client modules.

---

### 4. webui Module (Frontend)

**matrix-view/webui** is a React + TypeScript + Vite frontend application.

#### Development Mode

```bash
cd matrix-view/webui
npm install
npm run dev
```

- Dev server port: `10908`
- Vite auto-proxies `/v1/*` to `localhost:10906`

#### Production Mode

WebUI static files are located at `~/.matrix/local/webui/`, served by `proxy_server.py` (Python3).

The Python proxy is responsible for:
1. Serving static files
2. Reverse-proxying `/v1/*` to the backend at `localhost:10906`

#### WebUI Interface Features

| Area | Function |
|------|----------|
| Sidebar | Session list, create/delete/rename sessions |
| Input Bar | Text input, model selection (flash/pro), deep thinking toggle, thinking depth (general/deep), output length (4096~32768) |
| Toolbar | API Key settings, Markdown rendering toggle, message filter (all/conversation), tasks awaiting authorization, refresh |
| Authorization Level | Controls command execution permissions for the current session |
| Skill Selection | Select specific skills in skill mode |
| Mode Switch | Auto / Plan / Execute / Task / Graph |
| Project Path | Set project absolute path in programming mode, auto-saves history |
| Terminal Selection | Select target device for command execution |
| Message Operations | Delete single message, load more historical messages |

#### Message Streaming

1. Frontend sends message to `POST /v1/chat/submit`
2. Backend returns `sessionId` and processes asynchronously
3. Frontend polls `GET /v1/session/isCompletions/{sessionId}` to check completion
4. When complete, fetch results via `GET /v1/message/page/chat/{sessionId}/1/50`
5. SSE mode can get real-time streaming content directly via `/v1/chat/completions`

---

## Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DEEPSEEK_API_KEY` | Deepseek API Key | Yes |

### Application Configuration (application.yml)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `10906` | Backend API port |
| `matrix.client.name` | `Personal PC` | Device display name |
| `matrix.client.desc` | `Personal Office` | Device description |
| `matrix.service.api-key` | `${DEEPSEEK_API_KEY}` | Deepseek API Key |

### WebUI Ports

| Service | Port | Description |
|---------|------|-------------|
| Backend API | `10906` | HTTP API |
| WebUI | `10908` | Frontend interface + API proxy |

---

## Available Skills

| Skill | Description |
|-------|-------------|
| `query-typhoon` | Query global tropical cyclone (typhoon/hurricane) latest warning bulletins |

Skills are configured under the `settings/skill/` directory. Each skill is a subdirectory containing a `SKILL.md` definition file.

---

## Notes

1. **Local mode is for standalone use only.** It does not depend on external middleware such as Nacos, Redis, or MySQL.
2. **The SQLite database and tables are auto-created on first startup.**
3. **WebUI requires Python3** to run the proxy server.
4. **API Key** is passed via the `DEEPSEEK_API_KEY` environment variable. The installation script automatically writes it to your shell configuration file.
5. **Risk levels** are configured in `settings/risk-level.yml` and can be adjusted as needed.
6. **Skills and risk levels can be managed during interactions.**
