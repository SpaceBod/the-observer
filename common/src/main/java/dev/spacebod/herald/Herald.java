package dev.spacebod.herald;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Loader-agnostic entry point. Both loaders' main entrypoints call {@link #init}; on a client that happens
 * before the client entrypoint runs, so {@link #config()} is always loaded by the time anything reads it.
 */
public final class Herald {
	public static final String MOD_ID = "spacebod_herald";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Defaults until {@link #init} runs. */
	private static HeraldConfig config = new HeraldConfig();
	private static Path gameDir = Path.of(".");
	private static Path configDir = Path.of("config");

	private Herald() {
	}

	public static HeraldConfig config() {
		return config;
	}

	/** Where the optional writing model lives: herald-models/ next to mods/ (client) or in the server folder. */
	public static Path modelsDir() {
		return gameDir.resolve("herald-models");
	}

	/** The loader's config folder: herald.json and the press profile, herald-press.json. */
	public static Path configDir() {
		return configDir;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void init(Path configDirectory, Path gameDirectory) {
		config = HeraldConfig.load(configDirectory);
		configDir = configDirectory;
		gameDir = gameDirectory;
		LOGGER.info("Herald loaded");
	}
}
