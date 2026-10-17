package dev.spacebod.herald.press;

import dev.spacebod.herald.Herald;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The writing models the Herald offers to download, and where an installed one is found. Any GGUF chat model
 * dropped into herald-models/ works; these two are the tested ones. Both are Qwen3.5 (Apache-2.0) in 4-bit
 * GGUF form, downloaded straight from Hugging Face and checked against its published SHA-256.
 */
public final class ModelCatalog {
	private ModelCatalog() {
	}

	public enum Choice {
		/** Runs on any machine that runs Minecraft: about 0.8 GB of extra memory while writing. */
		STANDARD("standard", "Qwen3.5 0.8B", "Qwen3.5-0.8B-Q4_K_M.gguf", "unsloth/Qwen3.5-0.8B-GGUF", 532_517_120L,
				"bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"),
		/** Better writing for machines with 16 GB of RAM or more: about 1.6 GB while writing. */
		QUALITY("quality", "Qwen3.5 2B", "Qwen3.5-2B-Q4_K_M.gguf", "unsloth/Qwen3.5-2B-GGUF", 1_280_835_840L,
				"aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223");

		public final String id;
		public final String label;
		public final String fileName;
		public final String repo;
		public final long bytes;
		public final String sha256;

		Choice(String id, String label, String fileName, String repo, long bytes, String sha256) {
			this.id = id;
			this.label = label;
			this.fileName = fileName;
			this.repo = repo;
			this.bytes = bytes;
			this.sha256 = sha256;
		}

		public URI downloadUri() {
			return URI.create("https://huggingface.co/" + repo + "/resolve/main/" + fileName);
		}

		public URI pageUri() {
			return URI.create("https://huggingface.co/" + repo);
		}

		public String sizeText() {
			return bytes >= 1_000_000_000L
					? String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1e9)
					: String.format(java.util.Locale.ROOT, "%d MB", Math.round(bytes / 1e6));
		}

		public boolean installed() {
			return Files.isRegularFile(Herald.modelsDir().resolve(fileName));
		}
	}

	/** The model the press will use: the one named in the config, otherwise the newest .gguf in herald-models/. */
	public static Optional<Path> installed() {
		Path dir = Herald.modelsDir();
		String named = Herald.config().model;
		if (!named.isBlank()) {
			Path file = dir.resolve(named);
			return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
		}
		if (!Files.isDirectory(dir)) {
			return Optional.empty();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gguf"))
					.filter(Files::isRegularFile)
					.max(Comparator.comparingLong(ModelCatalog::modified));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** Suggested download for this machine, by total physical memory. */
	public static Choice recommended() {
		long ram = totalMemory();
		return ram >= 15L * 1024 * 1024 * 1024 ? Choice.QUALITY : Choice.STANDARD;
	}

	private static long totalMemory() {
		try {
			if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
				return os.getTotalMemorySize();
			}
		} catch (RuntimeException | LinkageError ignored) {
			// Not every runtime exposes it; assume a modest machine.
		}
		return 0;
	}

	private static long modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}
}
