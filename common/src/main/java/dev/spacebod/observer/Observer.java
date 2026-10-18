package dev.spacebod.observer;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Loader-agnostic entry point. Both loaders' main entrypoints call {@link #init}; on a client that happens
 * before the client entrypoint runs, so {@link #config()} is always loaded by the time anything reads it.
 */
public final class Observer {
	public static final String MOD_ID = "spacebod_observer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Defaults until {@link #init} runs. */
	private static ObserverConfig config = new ObserverConfig();
	private static Path gameDir = Path.of(".");
	private static Path configDir = Path.of("config");
	private static boolean development;

	private Observer() {
	}

	public static ObserverConfig config() {
		return config;
	}

	/** Where the optional writing model lives: observer-models/ next to mods/ (client) or in the server folder. */
	public static Path modelsDir() {
		return gameDir.resolve("observer-models");
	}

	/** The loader's config folder: observer.json and the press profile, observer-press.json. */
	public static Path configDir() {
		return configDir;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Whether this is a development run, which adds tools for trying the mod out (such as /observer demo). */
	public static boolean development() {
		return development;
	}

	public static void init(Path configDirectory, Path gameDirectory, boolean developmentEnvironment) {
		development = developmentEnvironment;
		config = ObserverConfig.load(configDirectory);
		configDir = configDirectory;
		gameDir = gameDirectory;
		LOGGER.info("The Observer loaded");
	}
}
