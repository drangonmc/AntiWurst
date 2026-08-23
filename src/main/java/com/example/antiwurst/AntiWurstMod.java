package com.example.antiwurst;

import com.example.antiwurst.server.AntiCheatService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AntiWurstMod implements ModInitializer {
    public static final String MOD_ID = "antiwurst";
    public static final Logger LOGGER = LoggerFactory.getLogger("AntiWurst");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> AntiCheatService.initialize());
        LOGGER.info("AntiWurst 2.0 loaded");
    }
}
