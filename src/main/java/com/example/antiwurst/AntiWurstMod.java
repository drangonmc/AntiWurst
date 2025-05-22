package com.example.antiwurst;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person; // 正确定义
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AntiWurstMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("AntiWurst");

	@Override
	public void onInitialize() {
		FabricLoader loader = FabricLoader.getInstance();

		Optional<ModContainer> detectedMod = loader.getAllMods().stream()
				.filter(this::isWurstMod)
				.findFirst();

		if (detectedMod.isPresent() || checkCriticalClasses()) {
			handleDetection(detectedMod.orElse(null));
		}
	}

	private boolean isWurstMod(ModContainer mod) {
		return mod.getMetadata().getId().equals("wurst") ||
				mod.getMetadata().getName().contains("Wurst Client") ||
				mod.getMetadata().getAuthors().stream()
						.map(Person::getName) // 正确方法引用
						.anyMatch(name -> name.contains("Alexander01998"));
	}

	private boolean checkCriticalClasses() {
		try {
			Class.forName("net.wurstclient.WurstInitializer");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private void handleDetection(ModContainer targetMod) {
		LOGGER.error("#############################################");
		LOGGER.error("#          Wurst Client 已被删除！！！           #");
		if (targetMod != null) {
			LOGGER.error("# Mod Name: {}", targetMod.getMetadata().getName());
		}
		LOGGER.error("#############################################");
		System.exit(1);
	}
}