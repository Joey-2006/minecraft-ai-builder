package com.joey.aibuilder;

import com.joey.aibuilder.http.AIHttpServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIBuilderMod implements ModInitializer {
    public static final String MOD_ID = "aibuilder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private AIHttpServer httpServer;
    private static AIBuilderMod instance;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("🟢 AI Builder Mod v{} initializing...",
                AIBuilderConfig.INSTANCE.getClass().getPackage().getImplementationVersion());

        // Load config
        AIBuilderConfig.load();

        // Register /aibuilder command for in-game control
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AIBuilderCommand.register(dispatcher);
        });

        // Hook server lifecycle: when a world/server starts, boot the HTTP API
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (AIBuilderConfig.INSTANCE.httpEnabled) {
                httpServer = new AIHttpServer(server);
                httpServer.start();
                LOGGER.info("🤖 AI Builder ready! HTTP API on http://{}:{}/api/v1/",
                        AIBuilderConfig.INSTANCE.httpHost,
                        AIBuilderConfig.INSTANCE.httpPort);
            }
        });

        // Shut down cleanly when the server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (httpServer != null) {
                httpServer.stop();
            }
        });

        LOGGER.info("AI Builder Mod initialized");
    }

    public AIHttpServer getHttpServer() {
        return httpServer;
    }

    public static AIBuilderMod getInstance() {
        return instance;
    }
}
