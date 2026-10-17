package dev.spacebod.herald.press;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that shapes the writing model, in one file a server owner can tune: config/herald-press.json,
 * written from the built-in defaults (assets/spacebod_herald/herald/press.json) on first use and re-read for
 * every edition.
 */
public final class PressProfile {
	static final String DEFAULT_PATH = "/assets/spacebod_herald/herald/press.json";
	static final String FILE_NAME = "herald-press.json";
	private static final Logger LOGGER = LoggerFactory.getLogger("spacebod_herald");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int version = 1;
	public String persona = "";
	/** System prompt lines; {{paper}} is the paper's name. */
	public List<String> system = new ArrayList<>();
	/** User message lines per story: {{section}}, {{brief}}, {{facts}}, {{#subject}}...{{/subject}}. */
	public List<String> request = new ArrayList<>();
	/** What each kind of story is about, by story kind; "default" for the rest. */
	public Map<String, String> briefs = Map.of();
	/** Few-shot examples, sent as earlier user/assistant turns. */
	public List<Example> examples = new ArrayList<>();
	public Sampling sampling = new Sampling();
	/** JSON Schema for the reply; llama.cpp turns it into a grammar, and replies are validated against it again. */
	public JsonObject schema = new JsonObject();
	public Checks checks = new Checks();

	public static final class Example {
		public String section = "";
		public String brief = "";
		public String subject = "";
		public List<String> facts = new ArrayList<>();
		public String headline = "";
		public String story = "";
	}

	public static final class Sampling {
		public float temperature = 0.5f;
		public float topP = 0.8f;
		public int topK = 20;
		public float minP = 0f;
		public float presencePenalty = 1.5f;
		public int maxTokens = 260;
		public int contextSize = 2048;
		/** Drafts per story before falling back to the house template. */
		public int attempts = 3;
		/** Let reasoning models think before answering. Off: their notes would eat the reply budget. */
		public boolean thinking = false;
	}

	public static final class Checks {
		public int minWords = 20;
		public int maxWords = 120;
		/** Every number in the reply must appear in the facts. */
		public boolean numbersFromFacts = true;
		/** The story's subject must be named. */
		public boolean subjectNamed = true;
		/** No other player of this world may be named unless the facts do. */
		public boolean noOtherPlayers = true;
		/** Quoted text must be quoted from the facts. */
		public boolean noInventedQuotes = true;
		/** Mostly Latin letters; catches a model drifting into another script. */
		public boolean englishOnly = true;
		/** Share of repeated three-word sequences above which a reply counts as looping. */
		public double maxRepeatedTrigramShare = 0.15;
		/** A run of this many words copied from the prompt or an example counts as copying. 0: off. */
		public int copyGuardWords = 6;
		/** Words never allowed (whole words, any case). */
		public List<String> forbiddenWords = new ArrayList<>();
		/** Words allowed only when the facts contain them: things small models like to invent. */
		public List<String> groundedWords = new ArrayList<>();
		/** Phrases never allowed anywhere (case-insensitive, whole words). */
		public List<String> bannedPhrases = new ArrayList<>();
	}

	public String brief(String kind) {
		String brief = briefs.get(kind);
		return brief != null ? brief : briefs.getOrDefault("default", "");
	}

	/** The server's profile, created from the defaults if missing; the defaults if it can't be read. */
	public static PressProfile load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(configDir);
				Files.writeString(file, defaultsText());
			}
			PressProfile profile = GSON.fromJson(Files.readString(file), PressProfile.class);
			if (profile == null || profile.system.isEmpty() || profile.request.isEmpty()) {
				throw new IOException("missing system or request");
			}
			PressProfile defaults = defaults();
			if (profile.version < defaults.version) {
				LOGGER.warn("{} is older than this version of Herald's defaults; delete it to get the new ones", file);
			}
			// Story kinds added since the file was written still get a brief.
			Map<String, String> briefs = new HashMap<>(defaults.briefs);
			briefs.putAll(profile.briefs);
			profile.briefs = briefs;
			return profile;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read {}, using the built-in press profile", file, e);
			return defaults();
		}
	}

	public static PressProfile defaults() {
		try {
			return GSON.fromJson(defaultsText(), PressProfile.class);
		} catch (IOException e) {
			throw new IllegalStateException("Herald's built-in press profile is missing", e);
		}
	}

	private static String defaultsText() throws IOException {
		try (InputStream in = PressProfile.class.getResourceAsStream(DEFAULT_PATH)) {
			if (in == null) {
				throw new IOException("missing " + DEFAULT_PATH);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
