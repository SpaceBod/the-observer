package dev.spacebod.herald;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/herald.json. Editable by hand; the server's copy decides how the paper is written and delivered. */
public final class HeraldConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "herald.json";

	/** Where this config was loaded from; transient so Gson leaves it out of the file. */
	private transient Path path;

	// The paper
	/** In-game days between editions. Each edition covers everything since the one before. */
	public int daysPerEdition = 7;
	/** Masthead. Empty: "The <world name> Herald". */
	public String paperName = "";
	/** Stories per edition, the lead included. */
	public int storiesPerEdition = 8;

	// Delivery
	/** A delivery dog carries the paper over. Off, or when no route to the player is found: it goes straight into the inventory. */
	public boolean deliveryDog = true;

	// The press (optional on-device writing model)
	/** Write stories with the model in herald-models/ when one is installed. Off: house templates only. */
	public boolean usePress = true;
	/** File name of the model to use. Empty: the newest .gguf in herald-models/. */
	public String model = "";
	/** CPU threads for the model. 0: half the cores, at most 4, so the game keeps running smoothly. */
	public int pressThreads = 0;
	/** Singleplayer: offer to download a model when the world opens and none is installed. */
	public boolean offerModelDownload = true;

	public static HeraldConfig load(Path configDir) {
		Path path = configDir.resolve(FILE_NAME);
		HeraldConfig config = new HeraldConfig();
		if (Files.exists(path)) {
			try {
				HeraldConfig read = GSON.fromJson(Files.readString(path), HeraldConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (IOException | RuntimeException e) {
				Herald.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		config.path = path;
		config.save();
		return config;
	}

	public void validate() {
		daysPerEdition = Math.clamp(daysPerEdition, 1, 100);
		storiesPerEdition = Math.clamp(storiesPerEdition, 1, 12);
		pressThreads = Math.clamp(pressThreads, 0, 64);
		if (paperName == null) {
			paperName = "";
		}
		if (model == null) {
			model = "";
		}
	}

	public int threads() {
		return pressThreads > 0 ? pressThreads : Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
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
			Herald.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
