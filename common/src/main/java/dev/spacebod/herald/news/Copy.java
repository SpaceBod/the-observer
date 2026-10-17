package dev.spacebod.herald.news;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.spacebod.herald.Herald;
import dev.spacebod.herald.edition.Article;
import dev.spacebod.herald.edition.Edition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The house templates (assets/spacebod_herald/herald/copy.json). They write every edition when no model is
 * installed, and stand in for any single story the model gets wrong. Choices are seeded by issue and story,
 * so an edition reprints word for word.
 */
public final class Copy {
	private static final Pattern SLOT = Pattern.compile("\\{([a-z]+)}");
	private static final String PATH = "/assets/spacebod_herald/herald/copy.json";
	private static Copy instance;

	private final Map<String, Kind> kinds = new HashMap<>();
	private final List<Article> opinions = new ArrayList<>();
	private final List<String> classifieds = new ArrayList<>();
	private final List<String> outlooks = new ArrayList<>();
	private final List<String> almanac = new ArrayList<>();

	public record Kind(List<String> headlines, List<String> bodies) {
	}

	private record Opinion(String headline, String body) {
	}

	private record File(Map<String, Kind> stories, List<Opinion> opinions, List<String> classifieds, List<String> outlooks,
						List<String> almanac) {
	}

	public static synchronized Copy get() {
		if (instance == null) {
			instance = new Copy();
		}
		return instance;
	}

	private Copy() {
		try (InputStream in = Copy.class.getResourceAsStream(PATH)) {
			if (in == null) {
				throw new IOException("missing " + PATH);
			}
			Gson gson = new Gson();
			JsonObject json = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			File file = gson.fromJson(json, File.class);
			kinds.putAll(file.stories());
			file.opinions().forEach(o -> opinions.add(new Article("Opinion", o.headline(), o.body())));
			classifieds.addAll(file.classifieds());
			outlooks.addAll(file.outlooks());
			if (file.almanac() != null) {
				almanac.addAll(file.almanac());
			}
		} catch (IOException | RuntimeException e) {
			Herald.LOGGER.error("Could not load the Herald's house copy", e);
		}
	}

	public Kind kind(String kind) {
		return kinds.getOrDefault(kind, kinds.get("quiet"));
	}

	/** One story in house style. */
	public Article write(Story story, Brief brief, int index) {
		Kind kind = kind(story.kind());
		Random random = new Random(seed(brief, index));
		Map<String, String> slots = slots(story, brief);
		String headline = fill(kind.headlines().get(random.nextInt(kind.headlines().size())), slots);
		String body = fill(kind.bodies().get(random.nextInt(kind.bodies().size())), slots);
		return new Article(story.section(), headline, body);
	}

	/** The whole edition in house style. {@code written} holds the press's stories; missing ones and nulls are filled in here. */
	public Edition print(Brief brief, List<Article> written, boolean pressWritten) {
		List<Article> stories = new ArrayList<>();
		List<Story> lineUp = brief.stories().isEmpty() ? List.of(quiet()) : brief.stories();
		for (int i = 0; i < lineUp.size(); i++) {
			Article article = i < written.size() ? written.get(i) : null;
			stories.add(article != null ? article : write(lineUp.get(i), brief, i));
		}
		// Fillers for whatever room the news leaves: opinion columns first, then almanac items. The page layout
		// uses them in order, and only while the news pages have space.
		Random random = new Random(seed(brief, 1000));
		List<Article> fillers = new ArrayList<>();
		List<Article> columns = new ArrayList<>(opinions);
		while (fillers.size() < 4 && !columns.isEmpty()) {
			fillers.add(columns.remove(random.nextInt(columns.size())));
		}
		List<String> facts = new ArrayList<>(almanac);
		while (fillers.size() < 10 && !facts.isEmpty()) {
			fillers.add(new Article("Almanac", "Did You Know?", facts.remove(random.nextInt(facts.size()))));
		}

		List<String> ads = new ArrayList<>(classifieds);
		List<String> picked = new ArrayList<>();
		for (int i = 0; i < 3 && !ads.isEmpty(); i++) {
			picked.add(ads.remove(random.nextInt(ads.size())));
		}
		String weather = brief.weather() + (outlooks.isEmpty() ? "" : "|" + outlooks.get(random.nextInt(outlooks.size())));
		return new Edition(brief.paper(), brief.issue(), brief.day(), brief.founding(), stories, fillers, brief.obituaries(),
				brief.numbers(), brief.briefs(), brief.boards(), picked, weather, pressWritten, Edition.Exchange.NONE);
	}

	static Story quiet() {
		return new Story("quiet", "From the Editor", null, 0, Map.of(), List.of("Nothing much happened in the world since the last edition."));
	}

	static Map<String, String> slots(Story story, Brief brief) {
		Map<String, String> slots = new HashMap<>(story.slots());
		slots.put("paper", brief.paper());
		slots.put("day", Long.toString(brief.day()));
		return slots;
	}

	private static long seed(Brief brief, int index) {
		return brief.paper().hashCode() * 31L + brief.issue() * 1009L + index;
	}

	static String fill(String template, Map<String, String> slots) {
		Matcher matcher = SLOT.matcher(template);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(out, Matcher.quoteReplacement(slots.getOrDefault(matcher.group(1), "")));
		}
		matcher.appendTail(out);
		return out.toString();
	}
}
