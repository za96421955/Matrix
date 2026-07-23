[English](README_EN.md) | [中文](README.md)

# Matrix

**Only supports Deepseek** — An AI conversation + command execution system based on the Deepseek API.

---

## Homepage
![index.png](index.png)
### Modes
- **Plan:** Build task plans
- **Execute:** Execute tasks directly
- **Tasks:** Task list (TAG + Observer architecture)
- **Goals:** Task list (Graph + Observer architecture)

## Usage Statistics
![use.png](use.png)

---

## Quick Start

### 1. Deepseek API
https://platform.deepseek.com/api_keys

### 2. One-click Install
### 2.1. Linux, MacOS
```bash
curl -fsSL https://raw.githubusercontent.com/za96421955/Matrix/latest/install/install.sh | bash
```
### China
```bash
curl -fsSL https://gitee.com/za96421955/matrix/raw/release/latest/install/install.sh | bash
```

### 2.2. Windows
```bash
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/za96421955/Matrix/latest/install/install.ps1" -OutFile "install.ps1"; powershell -ExecutionPolicy Bypass -File .\install.ps1
```
### China
```bash
Invoke-WebRequest -Uri "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/install.ps1" -OutFile "install.ps1"; powershell -ExecutionPolicy Bypass -File .\install.ps1
```
**If the system prompts that script execution is disabled, run the following first:**
```bash
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Installation process:
1. Detect DEEPSEEK_API_KEY (if not set, prompt for interactive input)
2. Download JDK21 (bundled package, auto-extract to ~/.jdks)
3. Download and merge JAR split volumes (matrix-local-1.0.2.jar)
4. Download WebUI static resources
5. Auto-start the service

After installation, use the `matrix` command to manage the service:

| Command | Function |
|---------|----------|
| `matrix start` | Start backend service + WebUI |
| `matrix stop` | Stop service |
| `matrix restart` | Restart service |
| `matrix status` | View running status |
| `matrix logs` | View backend logs |
| `matrix webui-logs` | View WebUI logs |
| `matrix update` | Update and upgrade |
| `matrix uninstall` | Uninstall |

Installation directory: `~/.matrix/local/`

---

## Directory Structure

```
├── install/                  # Installation and deployment
│   ├── install.sh            # One-click install script
│   ├── bin/                  # Service management scripts
│   │   ├── start.sh          # Start service (includes JDK check, process management)
│   │   ├── stop.sh           # Stop service
│   │   ├── restart.sh        # Restart service
│   │   └── proxy_server.py   # WebUI proxy server (Python3)
│   └── config/
│       └── application.yml   # Runtime configuration (port, datasource, API Key, etc.)
│
├── matrix-local/             # [Core] Standalone server
│   ├── data/
│   │   ├── schema.sql        # Database schema SQL (SQLite)
│   │   └── matrix-local.db   # SQLite data file (auto-generated)
│   └── src/
│       ├── LocalApplication.java    # Bootstrap entry (main class)
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
├── matrix-client/            # Terminal client (runs on device)
│   └── src/
│       ├── ClientApplication.java    # Bootstrap entry
│       ├── mqtt/
│       │   ├── MqttConnection.java   # MQTT connection management
│       │   ├── MqttConsumer.java     # MQTT message consumption (receive command -> execute -> receipt)
│       │   ├── MqttSubscriber.java   # MQTT subscription
│       │   └── MqttRunner.java       # MQTT auto-start (toggle: matrix.mqtt.enabled)
│       └── service/
│           ├── CommandExecutor.java          # Command execution interface
│           ├── impl/PCCommandExecutor.java   # PC command executor (bash/cmd/powershell)
│           ├── Fingerprint.java              # Device fingerprint interface
│           └── impl/PCFingerprintImpl.java   # PC fingerprint generator (UUID+MAC+hostname -> SHA256)
│
├── matrix-cloud/             # Cloud server (multi-module)
│   ├── matrix-cloud-gateway/ # Gateway
│   │   └── src/
│   │       ├── GatewayApplication.java      # Bootstrap
│   │       ├── filter/GatewayLogFilter.java # Request logging
│   │       └── filter/LimitGlobalFilter.java # Rate limiting
│   ├── matrix-cloud-service/ # Business services
│   │   └── src/
│   │       ├── ServiceApplication.java      # Bootstrap
│   │       ├── controller/                  # API endpoints (see below)
│   │       ├── service/                     # Business logic
│   │       │   ├── chat/                    # Conversations/Messages/Sessions
│   │       │   ├── user/                    # Users/Clients
│   │       │   ├── agent/                   # Modes/Skills
│   │       │   ├── task/                    # Task management
│   │       │   ├── app/                     # Script language executor
│   │       │   └── tool/                    # Tools
│   │       └── mqtt/                        # MQTT publish/subscribe
│   └── matrix-cloud-common/ # Common module (DTO/Constants/Utilities)
│
├── matrix-view/              # Frontend
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
│   ├── skill/                # Skill directory (e.g., pdf, query-typhoon)
│   │   └── pdf/SKILL.md      # PDF skill definition
│   ├── app/                  # Script language executor configuration
│   │   ├── shell/            # sh, bash, zsh, ksh, csh
│   │   ├── script/           # python, node, lua, ruby, php, perl, R
│   │   ├── compile/          # java, go, rust, kotlin
│   │   ├── text/             # sed, awk
│   │   └── windows/          # powershell, batch, vbscript
│   └── MEMORY.md             # Service memory file
│
├── logs/                     # Logs directory
│   ├── info/                 # General logs
│   └── error/                # Error logs
│
├── pom.xml                   # Maven parent POM (module aggregation)
└── wiki/                     # Documentation (reserved)
```

---

## Module Operation Guide

### 1. local Module (Standalone Core)

**matrix-local** is the core module of the project, integrating all features into a single application, ready to use out of the box.

#### Startup Methods

```bash
# Method 1: Use the matrix command (after installation)
matrix start

# Method 2: Run JAR directly
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

The local module uses **SQLite** as a replacement for MySQL + Redis. Data file location:

```
~/.matrix/local/data/matrix-local.db
```

All data is automatically persisted, no additional database configuration required.

Database tables (auto-created, see `matrix-local/data/schema.sql` for details):

| Table Name | Description |
|-----------|-------------|
| `tbl_user_info` | User information |
| `tbl_client_info` | Client devices |
| `tbl_session_info` | Chat sessions |
| `tbl_message_info` | Chat messages |
| `tbl_user_api_key` | API Keys |
| `tbl_task_info` | Task records |
| `tbl_local_cache` | Local cache (replaces Redis) |
| `tbl_local_timer` | Local scheduled tasks |

#### Local Cache (Replaces Redis)

The local module has a built-in SQLite-based cache system `LocalCacheService`, supporting:

- **KV Storage**: `put(key, value, ttl)` / `get(key)` / `delete(key)` / `keys(pattern)`
- **TTL Expiration**: Set expiration time (in seconds), 0 or negative means never expire
- **Hash Operations**: Via `SqlServiceCache.getHash()`, stored as JSON serialization
- **Set Operations**: Via `SqlServiceCache.getSet()`, stored as JSON array serialization
- **Distributed Lock**: `lock(key, ttl)` simple mutex lock based on cache

> Note: In local mode, all cache is stored in the SQLite database and persists across restarts. Switch to cloud mode if Redis is needed.

#### Local Command Execution

The local module's `SyncExecutor` executes CLI commands directly on the local machine (via `PCCommandExecutor`), eliminating the need for MQTT remote calls.

- macOS/Linux: Executed using bash
- Windows: Executed using cmd.exe (with `json` format parameters, supports specifying `dir` and `command`)

#### Risk Levels

The configuration file `settings/risk-level.yml` controls the risk levels of various operations:

| Level | Meaning | Description |
|-------|---------|-------------|
| -1 | Forbidden | Dangerous operations like `shutdown`, `sudo`, `rm`, `mkfs`, etc. |
| 0 | No Risk | Safe operations like `ls`, `pwd`, `cat`, `grep`, `echo`, etc. |
| 1 | Low Risk | CLI default level, routine operations |
| 2 | Medium Risk | |
| 3 | High Risk | Default level for `skill` and `app` |

In WebUI, the authorization level dropdown controls the current session's authorization level:

| Authorization Level | Meaning |
|-------------------|---------|
| -1 | Execution forbidden |
| 0 | Safe operations only |
| 1 | Routine operations allowed |
| 2 | Sensitive operations allowed |
| 3 | Always allowed |

---

### 2. client Module (Terminal Client)

**matrix-client** is the terminal program running on the device, with the following features:

#### Core Features

| Feature | Description |
|---------|-------------|
| PC Command Execution | `PCCommandExecutor`: Executes bash/cmd/powershell commands locally |
| Device Fingerprint | `PCFingerprintImpl`: Generates unique device ID via UUID + MAC address + hostname (`ha-ce-pc-{sha256}`) |
| MQTT Remote Control | Optional feature. Receives MQTT messages to execute commands and send receipts (toggle: `matrix.mqtt.enabled=true/false`) |
| Service Registration/Heartbeat | Registers device info (name, OS info, etc.) to the server on startup, sends periodic heartbeats |

#### Device Fingerprint Generation Logic

1. Retrieve system UUID (Mac: `system_profiler SPHardwareDataType` / Linux: `/sys/class/dmi/id/product_uuid` / Windows: WMI)
2. Retrieve primary network interface MAC address
3. Concatenate and compute SHA-256 hash
4. Final ID format: `ha-ce-pc-{hash}`

#### Configuration (application.yml)

```yaml
matrix:
  client:
    name: Personal PC        # Device display name
    desc: Personal Office    # Device description
  mqtt:
    enabled: false           # MQTT enabled or not (usually disabled in local mode)
```

---

### 3. cloud Module (Cloud Server)

**matrix-cloud** is a multi-module project containing three sub-modules:

#### matrix-cloud-gateway (Gateway)

- Request logging (`GatewayLogFilter`)
- Rate limiting filter (`LimitGlobalFilter`)
- Health check: `GET /health/check`

#### matrix-cloud-service (Business Services)

Full REST API with path prefix `/v1`.

##### Chat Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/chat/submit` | Submit a conversation, returns sessionId, async backend processing |
| POST | `/v1/chat/completions` | SSE streaming conversation (retrieve real-time inference content) |

**Submit conversation request body example:**

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
| GET | `/v1/session/page/{pageNum}/{pageSize}` | Paginated session list query |
| GET | `/v1/session/{sessionId}` | Get session details |
| GET | `/v1/session/isCompletions/{sessionId}` | Check if session is generating |
| GET | `/v1/session/stop/{sessionId}` | Stop generation |
| PUT | `/v1/session/updateTitle/{sessionId}` | Rename session |
| PUT | `/v1/session/updateAgent/{sessionId}` | Update session-bound skill |
| PUT | `/v1/session/updateAuthLevel/{sessionId}` | Update session authorization level |
| DELETE | `/v1/session/{sessionId}` | Delete session |

##### Message Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/message/page/{sessionId}/{pageNum}/{pageSize}` | Paginated message query (all) |
| GET | `/v1/message/page/chat/{sessionId}/{pageNum}/{pageSize}` | Paginated message query (chat only) |
| GET | `/v1/message/{sessionId}/{messageId}` | Get single message |
| DELETE | `/v1/message/{sessionId}/{messageId}` | Delete single message |

##### User Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/user/checkApiKey` | Validate if API Key is valid |
| GET | `/v1/user/getAuthLevel` | Get user default authorization level |
| DELETE | `/v1/user/ak` | Delete API Key |
| POST | `/v1/user/ak/enable` | Enable API Key |
| POST | `/v1/user/ak/disable` | Disable API Key |

##### Client Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/client/list` | Get online device list |
| POST | `/v1/client/register/{clientId}` | Device registration |
| POST | `/v1/client/heartbeat/{clientId}` | Heartbeat report |
| POST | `/v1/client/checkOnline/{clientId}` | Check device online status |

##### Agent Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/agent/list` | Get available skill list |
| POST | `/v1/agent/call` | SSE skill invocation |

##### Task Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/task/{taskId}` | Get task details |
| POST | `/v1/task/ack/{taskId}` | Task acknowledgment |
| GET | `/v1/task/waitingAuthList` | Get pending authorization task list |
| POST | `/v1/task/auth/{taskId}` | User authorize/reject |

#### matrix-cloud-common (Common Module)

Provides globally shared DTOs, constants, enums, and utility classes for use by both service and client modules.

---

### 4. webui Module (Frontend)

**matrix-view/webui** is a React + TypeScript + Vite frontend application.

#### Development Mode

```bash
cd matrix-view/webui
npm install
npm run dev
```

- Development server port: `10908`
- Vite auto-proxies `/v1/*` to `localhost:10906`

#### Production Mode

WebUI static files are located at `~/.matrix/local/webui/`, served by `proxy_server.py` (Python3).

The Python proxy is responsible for:
1. Serving static files
2. Reverse proxying `/v1/*` to the backend `localhost:10906`

#### WebUI Interface Features

| Area | Feature |
|------|---------|
| Sidebar | Session list, create/delete/rename sessions |
| Input Bar | Text input, model selection (flash/pro), deep thinking toggle, thinking depth (normal/deep), output length (4096~32768) |
| Toolbar | API Key settings, Markdown rendering toggle, message filter (all/chat), pending authorization tasks, refresh |
| Authorization Level | Control the command execution permission for the current session |
| Skill Selection | Select specific skill in skill mode |
| Mode Switch | Auto / Plan / Execute / Tasks / Goals |
| Project Path | Set project absolute path in programming mode, auto-record history |
| Terminal Selection | Select target device for command execution |
| Message Actions | Delete single message, load more historical messages |

#### Message Streaming Process

1. Frontend sends message to `POST /v1/chat/submit`
2. Backend returns `sessionId` and processes asynchronously
3. Frontend polls `GET /v1/session/isCompletions/{sessionId}` to check completion
4. When complete, retrieve results via `GET /v1/message/page/chat/{sessionId}/1/50`
5. SSE mode can retrieve real-time streaming content directly via `/v1/chat/completions`

---

## Configuration Guide

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DEEPSEEK_API_KEY` | Deepseek API Key | ✅ Required |

### Application Configuration (application.yml)

| Configuration Item | Default Value | Description |
|-------------------|---------------|-------------|
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
| `query-typhoon` | Query the latest global tropical cyclone (typhoon/hurricane) warning bulletins |

Skills are configured in the `settings/skill/` directory. Each skill is a subdirectory containing a `SKILL.md` definition file.

---

## Notes

1. **Local mode is for single-machine use only**, does not rely on external middleware such as Nacos, Redis, MySQL, etc.
2. **First startup** automatically creates the SQLite database and table structure
3. **WebUI requires Python3** environment to run the proxy server
4. **API Key** is passed via the `DEEPSEEK_API_KEY` environment variable. The install script automatically writes it to the shell configuration file
5. **Risk levels** are configured in `settings/risk-level.yml` and can be adjusted as needed
6. **`Skills` and `Risk Levels` can be managed during interaction**
