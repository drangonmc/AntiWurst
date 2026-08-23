package com.example.antiwurst.config;

import com.example.antiwurst.AntiWurstMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AntiWurstConfig(
        boolean requireClientMod,
        int heartbeatIntervalTicks,
        int heartbeatTimeoutTicks,
        double violationKickScore,
        double violationDecayPerTick,
        double maximumAttackReach,
        int maximumAttacksPerSecond,
        boolean cancelSuspiciousAttacks,
        boolean logViolations) {

    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("antiwurst-server.properties");

    public static AntiWurstConfig load() {
        Properties properties = defaults();

        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                properties.load(reader);
            } catch (IOException exception) {
                AntiWurstMod.LOGGER.error("Unable to read {}. Defaults will be used.", PATH, exception);
            }
        }

        AntiWurstConfig config = new AntiWurstConfig(
                bool(properties, "require-client-mod", true),
                integer(properties, "heartbeat-interval-ticks", 200, 40, 1200),
                integer(properties, "heartbeat-timeout-ticks", 100, 20, 600),
                decimal(properties, "violation-kick-score", 10.0, 3.0, 100.0),
                decimal(properties, "violation-decay-per-tick", 0.015, 0.0, 1.0),
                decimal(properties, "maximum-attack-reach", 4.25, 3.0, 8.0),
                integer(properties, "maximum-attacks-per-second", 22, 10, 100),
                bool(properties, "cancel-suspicious-attacks", true),
                bool(properties, "log-violations", true));
        config.saveDefaultsIfMissing(properties);
        return config;
    }

    private void saveDefaultsIfMissing(Properties properties) {
        if (Files.exists(PATH)) {
            return;
        }

        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                properties.store(writer, "AntiWurst server configuration (20 ticks = 1 second)");
            }
        } catch (IOException exception) {
            AntiWurstMod.LOGGER.error("Unable to create default configuration at {}", PATH, exception);
        }
    }

    private static Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty("require-client-mod", "true");
        properties.setProperty("heartbeat-interval-ticks", "200");
        properties.setProperty("heartbeat-timeout-ticks", "100");
        properties.setProperty("violation-kick-score", "10.0");
        properties.setProperty("violation-decay-per-tick", "0.015");
        properties.setProperty("maximum-attack-reach", "4.25");
        properties.setProperty("maximum-attacks-per-second", "22");
        properties.setProperty("cancel-suspicious-attacks", "true");
        properties.setProperty("log-violations", "true");
        return properties;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        AntiWurstMod.LOGGER.warn("Invalid boolean for '{}'; using {}", key, fallback);
        return fallback;
    }

    private static int integer(Properties properties, String key, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(properties.getProperty(key))));
        } catch (RuntimeException exception) {
            AntiWurstMod.LOGGER.warn("Invalid integer for '{}'; using {}", key, fallback);
            return fallback;
        }
    }

    private static double decimal(Properties properties, String key, double fallback, double minimum, double maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Double.parseDouble(properties.getProperty(key))));
        } catch (RuntimeException exception) {
            AntiWurstMod.LOGGER.warn("Invalid number for '{}'; using {}", key, fallback);
            return fallback;
        }
    }
}
