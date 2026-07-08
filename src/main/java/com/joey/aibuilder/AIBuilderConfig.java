package com.joey.aibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AIBuilderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("aibuilder");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("aibuilder.json");

    public static AIBuilderConfig INSTANCE = new AIBuilderConfig();

    // -- Config fields --
    public boolean httpEnabled = true;
    public String httpHost = "127.0.0.1";
    public int httpPort = 25566;
    public String authToken = "";
    public int maxScanVolume = 200000;
    public int maxFillVolume = 50000;
    public int maxCommandsPerRequest = 100;
    public int requestTimeoutSeconds = 30;
    public boolean logCommands = true;

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, AIBuilderConfig.class);
                LOGGER.info("Loaded AI Builder config from {}", CONFIG_PATH);
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        save(); // ensure file exists with defaults
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
