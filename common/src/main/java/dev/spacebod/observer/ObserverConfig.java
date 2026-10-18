package dev.spacebod.observer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/observer.json. Editable by hand; the server's copy decides how the paper is written and delivered. */
public final class ObserverConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "observer.json";

	/** Where this config was loaded from; transient so Gson leaves it out of the file. */
	private transient Path path;

	// The paper
	/** How often the paper comes out: "daily", "weekly" or "fortnightly". Each edition covers the time since the one before. */
	public String frequency = "weekly";
	/** Publish an edition on schedule. Off: only when an operator publishes one. */
	public boolean autoPublish = true;
	/** Stories per edition, the lead included. */
	public int storiesPerEdition = 8;

	// Delivery
	/**
	 * Players may choose to have a delivery dog bring the paper (falling back to the inventory when no route is
	 * found). Off: everyone collects their copy from a newsstand. Only operators can change it in game.
	 */
	public boolean deliveryDog = true;
	/** Emeralds for a copy of an edition, paid to the Pawstman or at a newsstand, spares included. 0: free. */
	public int paperPrice = 1;
	/** Each player's first copy is on the house, so a new player can read the paper before they have emeralds. */
	public boolean firstCopyFree = true;

	// The Exchange
	/** How generous the Exchange's rewards are from the next edition on: "low", "balanced" or "high". */
	public String exchangeRewards = "balanced";

	// The paper's voice
	/** "broadsheet" (straight news, dry wit), "roast" (no resident is safe) or "chronicle" (every deed a legend). */
	public String tone = "broadsheet";

	// The press (optional on-device writing model)
	/** Write stories with the model in observer-models/ when one is installed. Off: house templates only. */
	public boolean usePress = true;
	/** File name of the model to use. Empty: the newest .gguf in observer-models/. */
	public String model = "";
	/** Where the model runs. "gpu": the graphics card (Vulkan, or Metal on Apple Silicon), falling back to the CPU. "cpu": the CPU only. */
	public String pressDevice = "gpu";
	/** CPU threads for the model. 0: half the cores, at most 8, so the game keeps running smoothly. */
	public int pressThreads = 0;
	/** Singleplayer: offer to download a model when the world opens and none is installed. */
	public boolean offerModelDownload = true;

	public static ObserverConfig load(Path configDir) {
		Path path = configDir.resolve(FILE_NAME);
		ObserverConfig config = new ObserverConfig();
		if (Files.exists(path)) {
			try {
				ObserverConfig read = GSON.fromJson(Files.readString(path), ObserverConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (IOException | RuntimeException e) {
				Observer.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		config.path = path;
		config.save();
		return config;
	}

	public void validate() {
		frequency = Frequency.of(frequency).id;
		storiesPerEdition = Math.clamp(storiesPerEdition, 1, 12);
		pressThreads = Math.clamp(pressThreads, 0, 64);
		if (model == null) {
			model = "";
		}
		pressDevice = "cpu".equalsIgnoreCase(pressDevice) ? "cpu" : "gpu";
		tone = Tone.of(tone).id;
		paperPrice = Math.clamp(paperPrice, 0, 64);
		exchangeRewards = Rewards.of(exchangeRewards).id;
	}

	public int threads() {
		return pressThreads > 0 ? pressThreads : Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 8);
	}

	public Frequency frequency() {
		return Frequency.of(frequency);
	}

	public boolean gpu() {
		return pressDevice.equals("gpu");
	}

	public void save() {
		validate();
		if (path == null) {
			return; // defaults before init; nothing to write to yet
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			Observer.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
