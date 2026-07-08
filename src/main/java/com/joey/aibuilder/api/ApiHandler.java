package com.joey.aibuilder.api;

import com.google.gson.*;
import com.joey.aibuilder.AIBuilderConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ApiHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aibuilder");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;

    public ApiHandler(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Process a full API request. Called from the HTTP server thread.
     * Dispatches to MC thread and blocks until complete or timeout.
     */
    public String handleRequest(String requestBody) {
        try {
            JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();

            // Auth check
            String token = getStringOrNull(request, "token");
            if (!AIBuilderConfig.INSTANCE.authToken.isEmpty()
                    && !AIBuilderConfig.INSTANCE.authToken.equals(token)) {
                return error("Unauthorized: invalid token");
            }

            JsonArray commands = request.getAsJsonArray("commands");
            if (commands == null || commands.isEmpty()) {
                return error("No commands provided");
            }

            int maxCmds = AIBuilderConfig.INSTANCE.maxCommandsPerRequest;
            if (commands.size() > maxCmds) {
                return error("Too many commands: " + commands.size() + " (max " + maxCmds + ")");
            }

            // Execute on MC thread
            long startTime = System.currentTimeMillis();
            CompletableFuture<JsonArray> future = new CompletableFuture<>();
            server.execute(() -> {
                JsonArray results = new JsonArray();
                for (JsonElement elem : commands) {
                    if (!elem.isJsonObject()) {
                        results.add(errorResult("unknown", "Invalid command format"));
                        continue;
                    }
                    JsonObject cmd = elem.getAsJsonObject();
                    String action = getStringOrNull(cmd, "action");
                    if (action == null) {
                        results.add(errorResult("unknown", "Missing 'action' field"));
                        continue;
                    }
                    results.add(executeCommand(action, cmd));
                }
                future.complete(results);
            });

            JsonArray results = future.get(
                    AIBuilderConfig.INSTANCE.requestTimeoutSeconds, TimeUnit.SECONDS);

            long elapsed = System.currentTimeMillis() - startTime;

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("execution_time_ms", elapsed);
            response.add("results", results);
            return GSON.toJson(response);

        } catch (JsonSyntaxException e) {
            return error("Invalid JSON: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error handling API request", e);
            return error("Internal error: " + e.getMessage());
        }
    }

    // ---- Command Dispatcher ----

    private JsonObject executeCommand(String action, JsonObject cmd) {
        try {
            if (AIBuilderConfig.INSTANCE.logCommands) {
                LOGGER.info("AI Builder executing: {}", action);
            }
            return switch (action) {
                case "place_block" -> placeBlock(cmd);
                case "break_block" -> breakBlock(cmd);
                case "get_block" -> getBlock(cmd);
                case "set_block" -> setBlock(cmd);
                case "fill" -> fill(cmd);
                case "scan" -> scan(cmd);
                case "get_player" -> getPlayer(cmd);
                case "run_command" -> runCommand(cmd);
                case "list_entities" -> listEntities(cmd);
                case "get_world_info" -> getWorldInfo(cmd);
                case "ping" -> ping();
                default -> errorResult(action, "Unknown action: " + action);
            };
        } catch (Exception e) {
            LOGGER.error("Command '{}' failed", action, e);
            return errorResult(action, e.getMessage());
        }
    }

    // ---- Action Implementations ----

    private JsonObject placeBlock(JsonObject cmd) {
        BlockPos pos = getPos(cmd);
        ServerWorld world = getWorld(cmd);
        Identifier blockId = Identifier.of(getRequiredString(cmd, "block"));

        Block block = Registries.BLOCK.get(blockId);
        if (block == Blocks.AIR && !blockId.toString().equals("minecraft:air")) {
            return errorResult("place_block", "Unknown block: " + blockId);
        }

        String mode = getStringOrNull(cmd, "mode");
        mode = (mode == null || mode.isEmpty()) ? "replace" : mode;

        // Check if pos is loaded
        if (!world.isChunkLoaded(pos)) {
            world.getChunk(pos); // force load
        }

        boolean success;
        if ("destroy".equals(mode)) {
            world.breakBlock(pos, true);
            success = true;
        }

        BlockState state = block.getDefaultState();
        success = world.setBlockState(pos, state, Block.NOTIFY_ALL);

        JsonObject result = successResult("place_block");
        result.addProperty("message", "Placed " + blockId + " at " + formatPos(pos));
        result.add("position", posToJson(pos));
        return result;
    }

    private JsonObject breakBlock(JsonObject cmd) {
        BlockPos pos = getPos(cmd);
        ServerWorld world = getWorld(cmd);
        BlockState oldState = world.getBlockState(pos);
        String oldBlock = Registries.BLOCK.getId(oldState.getBlock()).toString();

        boolean dropItems = cmd.has("drop_items") ? cmd.get("drop_items").getAsBoolean() : true;
        boolean success = world.breakBlock(pos, dropItems);

        JsonObject result = successResult("break_block");
        result.addProperty("message", success
                ? "Broke " + oldBlock + " at " + formatPos(pos)
                : "Nothing to break at " + formatPos(pos));
        result.addProperty("old_block", oldBlock);
        result.add("position", posToJson(pos));
        return result;
    }

    private JsonObject getBlock(JsonObject cmd) {
        BlockPos pos = getPos(cmd);
        ServerWorld world = getWorld(cmd);
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);

        JsonObject result = successResult("get_block");
        result.add("position", posToJson(pos));
        result.addProperty("block", id.toString());
        result.addProperty("block_state", state.toString());
        result.addProperty("is_air", block == Blocks.AIR || block == Blocks.VOID_AIR || block == Blocks.CAVE_AIR);
        result.addProperty("is_solid", state.isSolidBlock(world, pos));
        result.addProperty("hardness", block.getHardness());
        result.addProperty("light_level", world.getLightLevel(pos));
        return result;
    }

    private JsonObject setBlock(JsonObject cmd) {
        BlockPos pos = getPos(cmd);
        ServerWorld world = getWorld(cmd);
        Identifier blockId = Identifier.of(getRequiredString(cmd, "block"));
        Block block = Registries.BLOCK.get(blockId);

        if (block == Blocks.AIR && !blockId.toString().equals("minecraft:air")) {
            return errorResult("set_block", "Unknown block: " + blockId);
        }

        BlockState state = block.getDefaultState();
        world.setBlockState(pos, state, Block.NOTIFY_ALL);

        JsonObject result = successResult("set_block");
        result.addProperty("message", "Set " + blockId + " at " + formatPos(pos));
        result.add("position", posToJson(pos));
        return result;
    }

    private JsonObject fill(JsonObject cmd) {
        BlockPos from = getPos(cmd, "from_x", "from_y", "from_z");
        BlockPos to = getPos(cmd, "to_x", "to_y", "to_z");
        ServerWorld world = getWorld(cmd);
        Identifier blockId = Identifier.of(getRequiredString(cmd, "block"));
        Block block = Registries.BLOCK.get(blockId);

        if (block == Blocks.AIR && !blockId.toString().equals("minecraft:air")) {
            return errorResult("fill", "Unknown block: " + blockId);
        }

        // Validate volume
        int dx = Math.abs(to.getX() - from.getX()) + 1;
        int dy = Math.abs(to.getY() - from.getY()) + 1;
        int dz = Math.abs(to.getZ() - from.getZ()) + 1;
        long volume = (long) dx * dy * dz;
        int maxVolume = AIBuilderConfig.INSTANCE.maxFillVolume;

        if (volume > maxVolume) {
            return errorResult("fill", "Volume " + volume + " exceeds max " + maxVolume);
        }

        // Ensure correct bounds
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        // Clamp Y to world bounds
        minY = Math.max(minY, world.getBottomY());
        maxY = Math.min(maxY, world.getBottomY() + world.getHeight() - 1);

        String mode = getStringOrNull(cmd, "mode");
        mode = (mode == null || mode.isEmpty()) ? "replace" : mode;

        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState existing = world.getBlockState(mutable);
                    boolean shouldPlace = switch (mode) {
                        case "replace" -> true;
                        case "hollow" -> (x == minX || x == maxX || y == minY || y == maxY
                                || z == minZ || z == maxZ);
                        case "keep" -> existing.isAir();
                        case "destroy" -> true;
                        default -> true;
                    };

                    if (shouldPlace) {
                        if ("destroy".equals(mode)) {
                            world.breakBlock(mutable, true);
                        }
                        if (world.setBlockState(mutable, block.getDefaultState(), Block.NOTIFY_ALL)) {
                            placed++;
                        }
                    }
                }
            }
        }

        JsonObject result = successResult("fill");
        result.addProperty("message", "Filled " + placed + " blocks from " +
                formatPos(new BlockPos(minX, minY, minZ)) + " to " +
                formatPos(new BlockPos(maxX, maxY, maxZ)));
        result.addProperty("blocks_placed", placed);
        result.addProperty("volume", volume);
        result.add("from", posToJson(new BlockPos(minX, minY, minZ)));
        result.add("to", posToJson(new BlockPos(maxX, maxY, maxZ)));
        return result;
    }

    private JsonObject scan(JsonObject cmd) {
        BlockPos from = getPos(cmd, "from_x", "from_y", "from_z");
        BlockPos to = getPos(cmd, "to_x", "to_y", "to_z");
        ServerWorld world = getWorld(cmd);

        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        minY = Math.max(minY, world.getBottomY());
        maxY = Math.min(maxY, world.getBottomY() + world.getHeight() - 1);

        int dx = maxX - minX + 1;
        int dy = maxY - minY + 1;
        int dz = maxZ - minZ + 1;
        long volume = (long) dx * dy * dz;
        int maxVolume = AIBuilderConfig.INSTANCE.maxScanVolume;

        if (volume > maxVolume) {
            return errorResult("scan", "Volume " + volume + " exceeds max " + maxVolume);
        }

        // Determine output format
        String format = getStringOrNull(cmd, "format");
        format = (format == null || format.isEmpty()) ? "compact" : format;

        JsonObject data = new JsonObject();
        data.add("from", posToJson(new BlockPos(minX, minY, minZ)));
        data.add("to", posToJson(new BlockPos(maxX, maxY, maxZ)));
        data.addProperty("size", dx + "x" + dy + "x" + dz);
        data.addProperty("total_blocks", volume);

        if ("compact".equals(format)) {
            // Compact: group by block type with counts
            Map<String, Integer> counts = new LinkedHashMap<>();
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        mutable.set(x, y, z);
                        String blockId = Registries.BLOCK.getId(
                                world.getBlockState(mutable).getBlock()).toString();
                        counts.merge(blockId, 1, Integer::sum);
                    }
                }
            }
            JsonObject compact = new JsonObject();
            counts.forEach(compact::addProperty);
            data.add("blocks", compact);

        } else if ("full".equals(format)) {
            // Full: every position -> block id
            JsonObject blockMap = new JsonObject();
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        mutable.set(x, y, z);
                        String blockId = Registries.BLOCK.getId(
                                world.getBlockState(mutable).getBlock()).toString();
                        blockMap.addProperty(x + "," + y + "," + z, blockId);
                    }
                }
            }
            data.add("blocks", blockMap);

        } else if ("layers".equals(format)) {
            // Layer by layer (Y level -> 2D grid)
            JsonObject layers = new JsonObject();
            for (int y = minY; y <= maxY; y++) {
                JsonArray layer = new JsonArray();
                for (int z = minZ; z <= maxZ; z++) {
                    JsonArray row = new JsonArray();
                    for (int x = minX; x <= maxX; x++) {
                        String blockId = Registries.BLOCK.getId(
                                world.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
                        row.add(blockId);
                    }
                    layer.add(row);
                }
                layers.add(String.valueOf(y), layer);
            }
            data.add("blocks", layers);
        }

        JsonObject result = successResult("scan");
        result.add("data", data);
        result.addProperty("message", "Scanned " + volume + " blocks");
        return result;
    }

    private JsonObject getPlayer(JsonObject cmd) {
        String playerName = getStringOrNull(cmd, "player");
        ServerPlayerEntity player;

        if (playerName != null && !playerName.isEmpty()) {
            player = server.getPlayerManager().getPlayer(playerName);
            if (player == null) {
                return errorResult("get_player", "Player not found: " + playerName);
            }
        } else {
            // Get first online player
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            if (players.isEmpty()) {
                return errorResult("get_player", "No players online");
            }
            player = players.get(0);
        }

        JsonObject result = successResult("get_player");
        JsonObject data = new JsonObject();
        data.addProperty("name", player.getName().getString());
        data.addProperty("uuid", player.getUuidAsString());
        data.add("position", posToJson(player.getBlockPos()));
        data.addProperty("x", player.getX());
        data.addProperty("y", player.getY());
        data.addProperty("z", player.getZ());
        data.addProperty("yaw", player.getYaw());
        data.addProperty("pitch", player.getPitch());
        data.addProperty("dimension", player.getServerWorld().getRegistryKey().getValue().toString());
        data.addProperty("health", player.getHealth());
        data.addProperty("max_health", player.getMaxHealth());
        data.addProperty("food_level", player.getHungerManager().getFoodLevel());
        data.addProperty("gamemode", player.interactionManager.getGameMode().getName());
        data.addProperty("is_on_ground", player.isOnGround());
        data.addProperty("is_sneaking", player.isSneaking());
        data.addProperty("is_sprinting", player.isSprinting());

        // What block is the player looking at?
        var hit = player.raycast(20, 0, false);
        if (hit != null && hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            BlockPos targetPos = BlockPos.ofFloored(hit.getPos());
            data.add("looking_at", posToJson(targetPos));
            data.addProperty("looking_at_block", Registries.BLOCK.getId(
                    player.getServerWorld().getBlockState(targetPos).getBlock()).toString());
        }

        result.add("data", data);
        return result;
    }

    private JsonObject runCommand(JsonObject cmd) {
        String mcCommand = getRequiredString(cmd, "command");
        ServerCommandSource source = server.getCommandSource();

        // Check if we should use player context
        String playerName = getStringOrNull(cmd, "as_player");
        if (playerName != null && !playerName.isEmpty()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
            if (player != null) {
                source = player.getCommandSource();
            }
        }

        server.getCommandManager().executeWithPrefix(source, mcCommand);

        JsonObject resp = successResult("run_command");
        resp.addProperty("command", mcCommand);
        resp.addProperty("message", "Command executed");
        return resp;
    }

    private JsonObject listEntities(JsonObject cmd) {
        ServerWorld world = getWorld(cmd);

        // Parse bounding box
        Box box;
        if (cmd.has("from") && cmd.has("to")) {
            BlockPos from = getPos(cmd, "from_x", "from_y", "from_z");
            BlockPos to = getPos(cmd, "to_x", "to_y", "to_z");
            box = new Box(
                    Math.min(from.getX(), to.getX()),
                    Math.min(from.getY(), to.getY()),
                    Math.min(from.getZ(), to.getZ()),
                    Math.max(from.getX(), to.getX()) + 1,
                    Math.max(from.getY(), to.getY()) + 1,
                    Math.max(from.getZ(), to.getZ()) + 1
            );
        } else {
            // Default: spawn chunks
            BlockPos spawn = world.getSpawnPos();
            box = new Box(spawn).expand(100);
        }

        String typeFilter = getStringOrNull(cmd, "type");
        int maxResults = cmd.has("max") ? cmd.get("max").getAsInt() : 200;

        JsonArray entities = new JsonArray();
        int count = 0;
        for (Entity entity : world.getEntitiesByClass(Entity.class, box, e -> true)) {
            if (count >= maxResults) break;
            String entityType = net.minecraft.registry.Registries.ENTITY_TYPE.getId(
                    entity.getType()).toString();
            if (typeFilter != null && !entityType.contains(typeFilter)) continue;

            JsonObject e = new JsonObject();
            e.addProperty("type", entityType);
            e.addProperty("uuid", entity.getUuidAsString());
            if (entity.hasCustomName()) {
                e.addProperty("name", entity.getCustomName().getString());
            }
            e.addProperty("x", entity.getX());
            e.addProperty("y", entity.getY());
            e.addProperty("z", entity.getZ());
            e.addProperty("yaw", entity.getYaw());
            e.addProperty("pitch", entity.getPitch());
            e.addProperty("is_player", entity instanceof PlayerEntity);
            entities.add(e);
            count++;
        }

        JsonObject result = successResult("list_entities");
        result.addProperty("count", count);
        result.addProperty("total_in_area", count);
        result.add("entities", entities);
        return result;
    }

    private JsonObject getWorldInfo(JsonObject cmd) {
        ServerWorld world = getWorld(cmd);
        BlockPos spawn = world.getSpawnPos();

        JsonObject data = new JsonObject();
        data.addProperty("dimension", world.getRegistryKey().getValue().toString());
        data.addProperty("time", world.getTime());
        data.addProperty("time_of_day", world.getTimeOfDay());
        data.addProperty("is_raining", world.isRaining());
        data.addProperty("is_thundering", world.isThundering());
        data.addProperty("difficulty", world.getDifficulty().getName());
        data.addProperty("is_day", world.isDay());
        data.addProperty("is_night", world.isNight());
        data.addProperty("moon_phase", world.getMoonPhase());
        data.addProperty("bottom_y", world.getBottomY());
        data.addProperty("top_y", world.getBottomY() + world.getHeight() - 1);
        data.addProperty("height", world.getHeight());
        data.add("spawn", posToJson(spawn));
        data.addProperty("player_count", server.getCurrentPlayerCount());
        data.addProperty("max_players", server.getMaxPlayerCount());

        // TPS if available
        double tps = server.getTickManager().getTickRate();
        data.addProperty("tick_rate", tps);

        JsonObject result = successResult("get_world_info");
        result.add("data", data);
        return result;
    }

    private JsonObject ping() {
        JsonObject result = successResult("ping");
        result.addProperty("status", "ok");
        result.addProperty("player_count", server.getCurrentPlayerCount());
        double tps = server.getTickManager().getTickRate();
        result.addProperty("tick_rate", tps);
        return result;
    }

    // ---- Helpers ----

    private ServerWorld getWorld(JsonObject cmd) {
        String dimId = getStringOrNull(cmd, "dimension");
        if (dimId != null && !dimId.isEmpty()) {
            Identifier id = Identifier.tryParse(dimId);
            if (id != null) {
                for (ServerWorld world : server.getWorlds()) {
                    if (world.getRegistryKey().getValue().equals(id)) {
                        return world;
                    }
                }
            }
        }
        // Default: overworld
        return server.getOverworld();
    }

    private BlockPos getPos(JsonObject cmd) {
        return getPos(cmd, "x", "y", "z");
    }

    private BlockPos getPos(JsonObject cmd, String keyX, String keyY, String keyZ) {
        int x = cmd.get(keyX).getAsInt();
        int y = cmd.get(keyY).getAsInt();
        int z = cmd.get(keyZ).getAsInt();
        return new BlockPos(x, y, z);
    }

    private String getRequiredString(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return obj.get(key).getAsString();
    }

    private String getStringOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    // ---- Result Builders ----

    private JsonObject successResult(String action) {
        JsonObject r = new JsonObject();
        r.addProperty("action", action);
        r.addProperty("success", true);
        return r;
    }

    private JsonObject errorResult(String action, String message) {
        JsonObject r = new JsonObject();
        r.addProperty("action", action);
        r.addProperty("success", false);
        r.addProperty("error", message);
        return r;
    }

    private String error(String message) {
        JsonObject r = new JsonObject();
        r.addProperty("success", false);
        r.addProperty("error", message);
        return GSON.toJson(r);
    }

    private JsonObject posToJson(BlockPos pos) {
        JsonObject p = new JsonObject();
        p.addProperty("x", pos.getX());
        p.addProperty("y", pos.getY());
        p.addProperty("z", pos.getZ());
        return p;
    }

    private String formatPos(BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }
}
