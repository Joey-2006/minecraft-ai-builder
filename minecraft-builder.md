# Minecraft AI Builder Skill

Control Minecraft 1.21.4 (Fabric) via the AI Builder mod's local HTTP API.
Use this skill whenever the user asks you to build, scan, or manipulate the Minecraft world.

## Prerequisites

- **Mod jar**: `aibuilder-1.0.0.jar` in Minecraft `mods/` folder
- **Minecraft running**: Fabric 1.21.4 with a world loaded
- **API base**: `http://127.0.0.1:25566`

## Quick Check

Before any operation, verify the mod is reachable:
```bash
curl -s http://127.0.0.1:25566/api/v1/ping
```
Expected: `{"success":true,"results":[{"action":"ping","success":true,"status":"ok","player_count":...}]}`

If unreachable → tell the user to start Minecraft and load a world.

## API Endpoints

### POST `/api/v1/build` — Main endpoint

All commands go through this single endpoint.

**Request:**
```json
{
  "commands": [
    {"action": "<name>", "<params>": "..."}
  ]
}
```

**Response:**
```json
{
  "success": true,
  "execution_time_ms": 45,
  "results": [
    {"action": "place_block", "success": true, "message": "...", "position": {...}}
  ]
}
```

## Command Reference

### `place_block` — Place a single block
```json
{"action":"place_block","x":0,"y":64,"z":0,"block":"minecraft:stone"}
```
Optional: `"dimension":"minecraft:overworld"` (default), `"mode":"destroy"` (breaks existing)

### `set_block` — Set block state directly
```json
{"action":"set_block","x":0,"y":64,"z":0,"block":"minecraft:oak_planks"}
```

### `break_block` — Break a block
```json
{"action":"break_block","x":0,"y":64,"z":0,"drop_items":true}
```

### `get_block` — Inspect a block
```json
{"action":"get_block","x":0,"y":64,"z":0}
```
Returns: block id, state, hardness, light level, solid/air flag.

### `fill` — Fill a region
```json
{
  "action":"fill",
  "from_x":0,"from_y":60,"from_z":0,
  "to_x":10,"to_y":65,"to_z":10,
  "block":"minecraft:stone_bricks",
  "mode":"hollow"
}
```
Modes: `replace` (default), `hollow` (shell only), `keep` (only air blocks), `destroy` (drops items).
**Max volume: 50,000 blocks** (configurable).

### `scan` — Scan a region
```json
{
  "action":"scan",
  "from_x":-20,"from_y":55,"from_z":-20,
  "to_x":20,"to_y":85,"to_z":20,
  "format":"compact"
}
```
Formats:
- `compact` — counts by block type (best for understanding terrain)
- `full` — per-position map
- `layers` — 2D grids by Y level

**Max volume: 200,000 blocks** (configurable).
Always scan first before building to understand the terrain.

### `get_player` — Player info
```json
{"action":"get_player"}
```
Returns: position (x,y,z as floats), dimension, health, food, gamemode, yaw, pitch, what block they're looking at.

### `get_world_info` — World status
```json
{"action":"get_world_info"}
```
Returns: time, weather, difficulty, spawn position, player count, TPS, bottom_y (min build height), height (world height).

### `list_entities` — Entities in area
```json
{"action":"list_entities","from_x":-50,"from_y":0,"from_z":-50,"to_x":50,"to_y":255,"to_z":50,"type":"villager","max":50}
```

### `run_command` — Execute vanilla command
```json
{"action":"run_command","command":"/give @p minecraft:diamond 1"}
```

### `ping` — Health check
```json
{"action":"ping"}
```

## Building Pattern

Always follow this workflow:

1. **Scan** — Understand the terrain first
   ```
   Scan around the player to see what's there
   ```

2. **Plan** — Decide what to build and calculate coordinates
   - Use player position as reference point
   - World bottom Y and height from `get_world_info`

3. **Build** — Execute fill/place_block commands
   - Floors first (from_y = ground level)
   - Then walls (hollow fill)
   - Then details (place_block)

4. **Report** — Summarize what was built

## Important Notes

- **Y coordinate**: Ground is typically around 62-64 in overworld. Use `get_player` or `scan` to find actual ground level.
- **Dimension**: Defaults to overworld. Use `"dimension":"minecraft:overworld"` explicitly if needed.
- **Block IDs**: Always use `minecraft:` prefix. Common blocks:
  - `minecraft:stone`, `minecraft:stone_bricks`
  - `minecraft:oak_planks`, `minecraft:oak_log`
  - `minecraft:glass`, `minecraft:glass_pane`
  - `minecraft:oak_door` (bottom half), `minecraft:oak_door` with upper state
  - `minecraft:torch` (needs wall or floor)
  - `minecraft:air` (to clear blocks)
- **Volume limits**: fill max 50K, scan max 200K. Break large builds into multiple commands.
- **Batch commands**: Send multiple commands in one request for efficiency.
- **All operations are synchronous on MC thread** — safe to chain commands.

## curl Template

```bash
curl -s -X POST http://127.0.0.1:25566/api/v1/build \
  -H "Content-Type: application/json" \
  -d '{"commands":[...]}'
```
