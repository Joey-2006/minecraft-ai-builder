package com.joey.aibuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AIBuilderCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("aibuilder")
                .requires(source -> source.hasPermissionLevel(2)) // op level 2+
                .then(literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(literal("reload")
                        .executes(ctx -> reload(ctx.getSource())))
                .then(literal("start")
                        .executes(ctx -> start(ctx.getSource())))
                .then(literal("stop")
                        .executes(ctx -> stop(ctx.getSource())))
                .then(literal("port")
                        .then(argument("port", IntegerArgumentType.integer(1024, 65535))
                                .executes(ctx -> setPort(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "port")))));

        dispatcher.register(root);
    }

    private static int status(ServerCommandSource source) {
        var mod = AIBuilderMod.getInstance();
        if (mod == null || mod.getHttpServer() == null) {
            source.sendFeedback(() -> Text.literal("§cAI Builder HTTP server is not running"), false);
        } else {
            String state = mod.getHttpServer().isRunning() ? "§arunning" : "§cstopped";
            source.sendFeedback(() -> Text.literal("§eAI Builder HTTP server is " + state +
                    " on port §6" + AIBuilderConfig.INSTANCE.httpPort), false);
        }
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        AIBuilderConfig.load();
        source.sendFeedback(() -> Text.literal("§aAI Builder config reloaded"), true);
        return 1;
    }

    private static int start(ServerCommandSource source) {
        var mod = AIBuilderMod.getInstance();
        if (mod != null && mod.getHttpServer() != null) {
            mod.getHttpServer().start();
            source.sendFeedback(() -> Text.literal("§aHTTP server started on port " +
                    AIBuilderConfig.INSTANCE.httpPort), true);
        } else {
            source.sendFeedback(() -> Text.literal("§cServer not available"), false);
        }
        return 1;
    }

    private static int stop(ServerCommandSource source) {
        var mod = AIBuilderMod.getInstance();
        if (mod != null && mod.getHttpServer() != null) {
            mod.getHttpServer().stop();
            source.sendFeedback(() -> Text.literal("§eHTTP server stopped"), true);
        } else {
            source.sendFeedback(() -> Text.literal("§cServer not available"), false);
        }
        return 1;
    }

    private static int setPort(ServerCommandSource source, int port) {
        AIBuilderConfig.INSTANCE.httpPort = port;
        AIBuilderConfig.save();
        source.sendFeedback(() -> Text.literal("§aPort set to §6" + port +
                "§a. Restart the HTTP server for changes to take effect."), true);
        return 1;
    }
}
