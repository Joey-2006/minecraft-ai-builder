# 🤖 AI Builder Mod

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.9%2B-blue)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

A Fabric mod for Minecraft 1.21.4 that exposes a **local HTTP API** for AI agents to build, scan, and control the Minecraft world. No GUI. No config. No API keys. Just drop the jar and the agent takes over.

## 🎯 What It Does

```
AI Agent (OpenClaw / curl / any HTTP client)
    │
    │  POST /api/v1/build  {"commands": [...]}
    ▼
┌──────────────────────┐
│  AI Builder Mod       │
│  HTTP :25566          │
│  ┌────────────────┐   │
│  │  Command Engine │──▶ Minecraft World
│  │  11 actions     │   │
│  └────────────────┘   │
└──────────────────────┘
```

Your AI agent sends JSON commands → the mod executes them in Minecraft → results come back as JSON.

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.4
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download `aibuilder-1.0.0.jar` from [Releases](https://github.com/Joey-2006/minecraft-ai-builder/releases)
4. Drop it into `mods/`
5. Launch Minecraft, open a world
6. Agent connects to `http://127.0.0.1:25566/api/v1/build`

## 🚀 Quick Start

```bash
# Check if mod is running
curl http://127.0.0.1:25566/api/v1/ping

# Scan the area around the player
curl -X POST http://127.0.0.1:25566/api/v1/build \
  -H "Content-Type: application/json" \
  -d '{"commands":[
    {"action":"scan","from_x":-10,"from_y":60,"from_z":-10,"to_x":10,"to_y":85,"to_z":10,"format":"compact"}
  ]}'

# Build a hollow stone house
curl -X POST http://127.0.0.1:25566/api/v1/build \
  -H "Content-Type: application/json" \
  -d '{"commands":[
    {"action":"fill","from_x":0,"from_y":63,"from_z":0,"to_x":7,"to_y":63,"to_z":7,"block":"minecraft:stone_bricks"},
    {"action":"fill","from_x":0,"from_y":64,"from_z":0,"to_x":7,"to_y":67,"to_z":7,"block":"minecraft:oak_planks","mode":"hollow"},
    {"action":"place_block","x":3,"y":64,"z":0,"block":"minecraft:oak_door"}
  ]}'
```

## 📋 Commands

| Action | Description |
|--------|-------------|
| `place_block` | Place a single block |
| `set_block` | Set block state directly |
| `break_block` | Break a block |
| `get_block` | Inspect a block |
| `fill` | Fill a 3D region (supports replace/hollow/keep/destroy) |
| `scan` | Scan a region (compact/full/layers formats) |
| `get_player` | Player position, health, gamemode, look target |
| `get_world_info` | World time, weather, difficulty, spawn, TPS |
| `list_entities` | Entities in a region with type filter |
| `run_command` | Execute a vanilla Minecraft command |
| `ping` | Health check |

Full API reference: [API-REFERENCE.md](API-REFERENCE.md)

## 🤖 Agent Integration

For OpenClaw agents, see [minecraft-builder.md](minecraft-builder.md) — drop it into your skills directory.

For any other AI/automation tool — just send HTTP requests. The mod responds to anything that speaks JSON.

## 🎮 In-Game Commands (OP level 2+)

```
/aibuilder status     Show HTTP server status
/aibuilder reload     Reload config
/aibuilder start      Start HTTP server
/aibuilder stop       Stop HTTP server
/aibuilder port 8080  Change port
```

## ⚙️ Configuration

Optional config file at `config/aibuilder.json` (auto-created on first launch):

```json
{
  "httpEnabled": true,
  "httpHost": "127.0.0.1",
  "httpPort": 25566,
  "authToken": "",
  "maxScanVolume": 200000,
  "maxFillVolume": 50000,
  "maxCommandsPerRequest": 100,
  "requestTimeoutSeconds": 30,
  "logCommands": true
}
```

Set `authToken` to require a `"token"` field in requests.

## 🔧 Building from Source

```bash
# Requires JDK 21+
./gradlew build
# Output: build/libs/aibuilder-1.0.0.jar
```

## 📁 Project Structure

```
src/main/java/com/joey/aibuilder/
├── AIBuilderMod.java         Main entry point
├── AIBuilderConfig.java      Configuration management
├── AIBuilderCommand.java     /aibuilder in-game command
├── http/AIHttpServer.java    Embedded HTTP server (port 25566)
└── api/ApiHandler.java       Command execution engine (11 actions)
```

## 🛡️ Security

- Binds to `127.0.0.1` — not exposed to LAN or internet
- Optional token authentication
- Volume limits prevent crash attacks
- All world operations run on the MC server thread — thread-safe

## 📄 License

MIT — do whatever you want with it.
