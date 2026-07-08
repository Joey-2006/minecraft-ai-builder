# AI Builder Mod — API Reference v1.0.0

Base URL: `http://127.0.0.1:25566`

## Endpoints

### `POST /api/v1/build`

Main endpoint. Execute one or more building commands.

**Request:**
```json
{
  "token": "optional-auth-token",
  "commands": [
    {
      "action": "place_block",
      "x": 100, "y": 64, "z": 100,
      "block": "minecraft:stone"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "execution_time_ms": 12,
  "results": [
    {
      "action": "place_block",
      "success": true,
      "message": "Placed minecraft:stone at [100, 64, 100]",
      "position": {"x": 100, "y": 64, "z": 100}
    }
  ]
}
```

### `GET /api/v1/ping`

Health check.

**Response:**
```json
{
  "success": true,
  "results": [{"action":"ping","success":true,"status":"ok","player_count":1,"tick_rate":20.0}]
}
```

### `GET /api/v1/status`

Full status — world info + first online player.

---

## Command Reference

### `place_block`

Place a single block at position.

```json
{"action":"place_block","x":0,"y":64,"z":0,"block":"minecraft:stone"}
```

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| x, y, z | int | ✅ | — | Block position |
| block | string | ✅ | — | Block ID (e.g. `minecraft:oak_planks`) |
| dimension | string | ❌ | `minecraft:overworld` | World dimension |
| mode | string | ❌ | `replace` | `replace` or `destroy` (drops items) |

### `set_block`

Set block state directly (no mode logic).

```json
{"action":"set_block","x":0,"y":64,"z":0,"block":"minecraft:stone_bricks"}
```

Same params as `place_block` minus `mode`.

### `break_block`

Break a block at position.

```json
{"action":"break_block","x":0,"y":64,"z":0,"drop_items":true}
```

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| x, y, z | int | ✅ | — | Block position |
| drop_items | bool | ❌ | `true` | Drop items on break |
| dimension | string | ❌ | `minecraft:overworld` | Dimension |

**Response includes:** `old_block` — the block that was broken.

### `get_block`

Inspect a block at position.

```json
{"action":"get_block","x":0,"y":64,"z":0}
```

**Response data:** `block`, `block_state`, `is_air`, `is_solid`, `hardness`, `light_level`.

### `fill`

Fill a 3D region with blocks.

```json
{
  "action":"fill",
  "from_x":0,"from_y":60,"from_z":0,
  "to_x":10,"to_y":65,"to_z":10,
  "block":"minecraft:glass",
  "mode":"hollow"
}
```

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| from_x/y/z | int | ✅ | — | Region start corner |
| to_x/y/z | int | ✅ | — | Region end corner |
| block | string | ✅ | — | Block ID |
| mode | string | ❌ | `replace` | `replace`, `hollow`, `keep` (only air), `destroy` |
| dimension | string | ❌ | `minecraft:overworld` | Dimension |

**Limits:** Max 50,000 blocks volume (configurable).

### `scan`

Scan a 3D region and return block data.

```json
{
  "action":"scan",
  "from_x":-20,"from_y":55,"from_z":-20,
  "to_x":20,"to_y":85,"to_z":20,
  "format":"compact"
}
```

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| from_x/y/z | int | ✅ | — | Region start |
| to_x/y/z | int | ✅ | — | Region end |
| format | string | ❌ | `compact` | `compact` (counts by type), `full` (per-pos map), `layers` (2D grids by Y) |
| dimension | string | ❌ | `minecraft:overworld` | Dimension |

**Compact format response:**
```json
{
  "data": {
    "from": {"x":-20,"y":55,"z":-20},
    "to": {"x":20,"y":85,"z":20},
    "size": "41x31x41",
    "total_blocks": 52111,
    "blocks": {
      "minecraft:air": 25000,
      "minecraft:stone": 15000,
      "minecraft:grass_block": 5000,
      "minecraft:dirt": 5000,
      "minecraft:oak_log": 1111
    }
  }
}
```

**Full format:** Key-value map: `"x,y,z": "minecraft:block_id"`  
**Layers format:** Nested arrays: `layers["85"] = [[row], [row], ...]`

**Limits:** Max 200,000 blocks volume (configurable).

### `get_player`

Get current player position and state.

```json
{"action":"get_player"}
```

Optional: `"player":"PlayerName"` to target a specific player.

**Response data:**
```json
{
  "name": "Steve",
  "position": {"x":100,"y":64,"z":100},
  "x": 100.5, "y": 64.0, "z": 100.3,
  "yaw": 45.0, "pitch": 0.0,
  "dimension": "minecraft:overworld",
  "health": 20.0, "max_health": 20.0,
  "food_level": 20,
  "gamemode": "creative",
  "is_on_ground": true,
  "looking_at": {"x":105,"y":65,"z":100},
  "looking_at_block": "minecraft:stone"
}
```

### `get_world_info`

Get world state information.

```json
{"action":"get_world_info"}
```

**Response data:** `time`, `time_of_day`, `is_raining`, `is_thundering`, `difficulty`, `is_day`, `is_night`, `moon_phase`, `bottom_y`, `height`, `spawn`, `player_count`, `max_players`, `tick_rate`.

### `list_entities`

List entities in a region.

```json
{
  "action":"list_entities",
  "from_x":-50,"from_y":0,"from_z":-50,
  "to_x":50,"to_y":255,"to_z":50,
  "type":"villager",
  "max":50
}
```

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| from_x/y/z | int | ❌ | spawn ±100 | Region start |
| to_x/y/z | int | ❌ | spawn ±100 | Region end |
| type | string | ❌ | — | Entity type filter (substring match) |
| max | int | ❌ | 200 | Max results |
| dimension | string | ❌ | overworld | Dimension |

### `run_command`

Execute a vanilla Minecraft command with server-level permissions.

```json
{"action":"run_command","command":"/give @p minecraft:diamond 64"}
```

Optional: `"as_player":"PlayerName"` to execute as a specific player.

⚠️ Commands run with **server-level** permissions. Use with caution.

### `ping`

Simple health check.

```json
{"action":"ping"}
```

---

## Best Practices

1. **Always scan first** — understand terrain before building
2. **Batch commands** — send multiple actions in one request (max 100)
3. **Use compact scan** — unless you need per-block detail
4. **Check player position** — `get_player` to orient builds relative to the player
5. **Respect volume limits** — break large fills into multiple commands
6. **Block IDs use `minecraft:` prefix** — e.g. `minecraft:stone`, `minecraft:oak_planks`

## Common Block IDs

```
minecraft:stone, minecraft:stone_bricks, minecraft:cobblestone
minecraft:oak_planks, minecraft:oak_log, minecraft:oak_stairs, minecraft:oak_slab
minecraft:glass, minecraft:glass_pane
minecraft:quartz_block, minecraft:quartz_pillar, minecraft:quartz_stairs
minecraft:black_concrete, minecraft:white_concrete, minecraft:pink_concrete
minecraft:obsidian, minecraft:glowstone, minecraft:sea_lantern
minecraft:iron_trapdoor, minecraft:oak_fence, minecraft:oak_door
minecraft:air (to clear blocks)
minecraft:end_stone, minecraft:purple_concrete, minecraft:purple_stained_glass
```

## Error Handling

All errors return:
```json
{
  "success": false,
  "error": "Human-readable error message"
}
```

Individual command failures return:
```json
{
  "action": "fill",
  "success": false,
  "error": "Volume 123000 exceeds max 50000"
}
```

Common errors:
- `401` — Invalid auth token
- `400` — Malformed JSON
- `Volume exceeds max` — Reduce area size
- `Unknown block: xxx` — Check block ID spelling
- `Player not found` — Player offline
