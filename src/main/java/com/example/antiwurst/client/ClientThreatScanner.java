package com.example.antiwurst.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ClientThreatScanner {
    private static final List<String> WURST_CLASSES = List.of(
            "net.wurstclient.WurstClient",
            "net.wurstclient.WurstInitializer",
            "net.wurstclient.hack.Hack",
            "net.wurstclient.hack.HackList");

    ScanResult scan() {
        List<String> reasons = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (looksLikeWurst(mod)) {
                reasons.add("suspicious mod metadata: " + safe(mod.getMetadata().getName()));
            }
        }

        ClassLoader classLoader = ClientThreatScanner.class.getClassLoader();
        for (String className : WURST_CLASSES) {
            String resource = className.replace('.', '/') + ".class";
            if (classLoader.getResource(resource) != null) {
                reasons.add("known Wurst class: " + className);
            }
        }
        return new ScanResult(reasons.isEmpty(), List.copyOf(reasons));
    }

    private boolean looksLikeWurst(ModContainer mod) {
        String id = normalized(mod.getMetadata().getId());
        String name = normalized(mod.getMetadata().getName());
        String description = normalized(mod.getMetadata().getDescription());
        boolean knownAuthor = mod.getMetadata().getAuthors().stream()
                .map(Person::getName)
                .map(ClientThreatScanner::normalized)
                .anyMatch(author -> author.contains("alexander01998"));
        return id.equals("wurst") || id.startsWith("wurst-") || name.contains("wurst client")
                || description.contains("wurst client") || knownAuthor;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[\\r\\n]", " ");
    }

    record ScanResult(boolean clean, List<String> reasons) {
        String summary() {
            String value = clean ? "clean" : reasons.get(0);
            return value.length() <= 160 ? value : value.substring(0, 160);
        }
    }
}
