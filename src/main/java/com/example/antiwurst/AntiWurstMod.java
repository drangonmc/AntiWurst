package com.example.antiwurst;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class AntiWurstMod implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("AntiWurst");

	@Override
	public void onInitializeClient() {
		LOGGER.info("AntiWurst 反作弊已加载");

		// 注册服务器加入事件
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			LOGGER.info("正在加入服务器，开始检测外挂...");
			checkForWurst();
		});
	}

	/**
	 * 执行Wurst外挂检测
	 */
	private void checkForWurst() {
		FabricLoader loader = FabricLoader.getInstance();
		Optional<ModContainer> detectedMod = loader.getAllMods().stream()
				.filter(this::isWurstMod)
				.findFirst();

		if (detectedMod.isPresent() || checkCriticalClasses()) {
			handleDetection(detectedMod.orElse(null));
		} else {
			LOGGER.info("未检测到Wurst外挂，允许加入服务器");
		}
	}

	/**
	 * 判断是否为Wurst Mod
	 */
	private boolean isWurstMod(ModContainer mod) {
		String modId = mod.getMetadata().getId();
		String modName = mod.getMetadata().getName();
		String description = mod.getMetadata().getDescription(); // 直接获取字符串

		// 检查作者
		boolean isAuthor = mod.getMetadata().getAuthors().stream()
				.map(Person::getName)
				.anyMatch(name -> name.contains("Alexander01998"));

		// 多维度检测
		return modId.equals("wurst") ||
				modName.contains("Wurst Client") ||
				isAuthor ||
				(description != null && description.contains("Wurst"));
	}

	/**
	 * 关键类检测
	 */
	private boolean checkCriticalClasses() {
		try {
			Class.forName("net.wurstclient.WurstInitializer");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/**
	 * 处理检测结果
	 */
	private void handleDetection(ModContainer targetMod) {
		LOGGER.error("#############################################");
		LOGGER.error("#        检测到 Wurst 外挂程序!            #");
		LOGGER.error("# 您的游戏客户端已被强制终止                #");

		if (targetMod != null) {
			LOGGER.error("# 危险Mod: {}", targetMod.getMetadata().getName());
			LOGGER.error("# 文件路径: {}", targetMod.getRootPaths().iterator().next());
		}

		LOGGER.error("#############################################");

		// 强制终止游戏
		System.exit(1);
	}
}
