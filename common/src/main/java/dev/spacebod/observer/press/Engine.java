package dev.spacebod.observer.press;

import dev.spacebod.observer.Observer;
import net.ladenthin.llama.loader.LlamaLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Loads llama.cpp's native library once per game: the Vulkan build when the GPU is wanted, otherwise (or when
 * it won't load, e.g. no Vulkan driver) the CPU build. A native library can't be unloaded, so after the first
 * edition a switch to the CPU only stops offloading, and a switch to the GPU waits for a restart.
 */
final class Engine {
	private static final String BACKEND = "net.ladenthin.llama.backend";
	private static final String TEMP_DIR = "net.ladenthin.llama.tmpdir";
	private static String loaded;

	private Engine() {
	}

	/** Loads the library if it isn't yet; the name of the one in use ("vulkan" or "cpu"). */
	static synchronized String load(boolean gpu) {
		if (loaded != null) {
			return loaded;
		}
		ownTempDir();
		if (gpu) {
			try {
				System.setProperty(BACKEND, "vulkan");
				LlamaLoader.initialize();
				loaded = "vulkan";
				return loaded;
			} catch (UnsatisfiedLinkError e) {
				Observer.LOGGER.info("The GPU build of the press can't run here ({}); using the CPU", e.getMessage());
			}
		}
		System.setProperty(BACKEND, "cpu");
		LlamaLoader.initialize();
		loaded = "cpu";
		return loaded;
	}

	/**
	 * Unpacks the native library into a folder of this game's own, so games running side by side don't overwrite
	 * each other's copy, and clears out the folders of games that have since closed.
	 */
	private static void ownTempDir() {
		Path root = Path.of(System.getProperty("java.io.tmpdir"), "observer-llama");
		try {
			Files.createDirectories(root);
			try (Stream<Path> games = Files.list(root)) {
				games.filter(dir -> !running(dir.getFileName().toString())).forEach(Engine::deleteQuietly);
			}
			Path mine = Files.createDirectories(root.resolve(Long.toString(ProcessHandle.current().pid())));
			System.setProperty(TEMP_DIR, mine.toString());
		} catch (IOException e) {
			Observer.LOGGER.debug("Unpacking the press to the shared temp folder: {}", e.getMessage());
		}
	}

	private static boolean running(String pid) {
		try {
			return ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static void deleteQuietly(Path dir) {
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					// Still in use; it goes next time.
				}
			});
		} catch (IOException e) {
			// Gone already.
		}
	}

	/** The library in use, or null before the first edition written with the model. */
	static synchronized String loaded() {
		return loaded;
	}
}
