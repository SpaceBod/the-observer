package dev.spacebod.herald.press;

import dev.spacebod.herald.Herald;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads one of the catalogue's models into herald-models/, only ever because a person asked for it (the
 * singleplayer prompt or {@code /herald model download}). One download at a time; it streams to a .part
 * file, checks the SHA-256 Hugging Face publishes, and only then takes the real name.
 */
public final class ModelDownload {
	private static volatile @Nullable Progress current;

	private ModelDownload() {
	}

	/** A running or finished download. {@link #error} is set if it failed. */
	public static final class Progress {
		public final ModelCatalog.Choice choice;
		public volatile long received;
		public volatile boolean done;
		public volatile @Nullable String error;

		Progress(ModelCatalog.Choice choice) {
			this.choice = choice;
		}

		public float fraction() {
			return Math.min(1f, received / (float) choice.bytes);
		}
	}

	public static @Nullable Progress current() {
		return current;
	}

	public static boolean running() {
		Progress p = current;
		return p != null && !p.done;
	}

	/** Starts a download, or returns the one already running. */
	public static synchronized CompletableFuture<Progress> start(ModelCatalog.Choice choice) {
		if (running()) {
			return CompletableFuture.completedFuture(current);
		}
		Progress progress = new Progress(choice);
		current = progress;
		return CompletableFuture.supplyAsync(() -> {
			try {
				download(progress);
			} catch (Exception e) {
				progress.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				Herald.LOGGER.warn("Model download failed", e);
			} finally {
				progress.done = true;
			}
			return progress;
		}, runnable -> {
			Thread thread = new Thread(runnable, "Herald model download");
			thread.setDaemon(true);
			thread.start();
		});
	}

	private static void download(Progress progress) throws IOException, InterruptedException, NoSuchAlgorithmException {
		ModelCatalog.Choice choice = progress.choice;
		Path dir = Herald.modelsDir();
		Files.createDirectories(dir);
		Path target = dir.resolve(choice.fileName);
		Path part = dir.resolve(choice.fileName + ".part");
		Herald.LOGGER.info("Downloading {} ({}) from {}", choice.label, choice.sizeText(), choice.downloadUri());

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		HttpRequest request = HttpRequest.newBuilder(choice.downloadUri())
				.header("User-Agent", "Herald-Minecraft-mod")
				.GET()
				.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("HTTP " + response.statusCode());
		}

		MessageDigest sha = MessageDigest.getInstance("SHA-256");
		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
			byte[] buffer = new byte[1 << 16];
			int n;
			while ((n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				sha.update(buffer, 0, n);
				progress.received += n;
			}
		}
		String hash = HexFormat.of().formatHex(sha.digest());
		if (!hash.equalsIgnoreCase(choice.sha256)) {
			Files.deleteIfExists(part);
			throw new IOException("the download was damaged (checksum mismatch); please try again");
		}
		Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
		Herald.LOGGER.info("Model ready: {}", target);
	}
}
