package dev.spacebod.observer.news;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.edition.Article;
import dev.spacebod.observer.edition.Edition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The house templates (assets/spacebod_observer/observer/copy.json). They write every edition when no model is
 * installed, and stand in for any single story the model gets wrong. Choices are seeded by issue and story,
 * so an edition reprints word for word.
 */
public final class Copy {
	private static final Pattern SLOT = Pattern.compile("\\{([a-z]+)}");
	private static final String PATH = "/assets/spacebod_observer/observer/copy.json";
	private static final String TRIVIA_PATH = "/assets/spacebod_observer/observer/trivia.json";
	/** Facts chosen for each edition: enough for the column and any space the news leaves. */
	private static final int TRIVIA_PER_EDITION = 40;
	private static Copy instance;

	private final Map<String, Kind> kinds = new HashMap<>();
	private final List<Article> opinions = new ArrayList<>();
	private final List<String> classifieds = new ArrayList<>();
	private final List<String> outlooks = new ArrayList<>();
	private final List<String> trivia = new ArrayList<>();
	private final Map<String, List<String>> links = new HashMap<>();
	/** The Roast's and the Chronicle's own templates, by tone id and story kind. */
	private final Map<String, Map<String, Kind>> toneKinds = new HashMap<>();

	public record Kind(List<String> headlines, List<String> bodies) {
	}

	private record Trivia(Map<String, List<String>> topics) {
	}

	private record Opinion(String headline, String body) {
	}

	private record File(Map<String, Kind> stories, List<Opinion> opinions, List<String> classifieds, List<String> outlooks,
						Map<String, List<String>> links, Map<String, Map<String, Kind>> tones) {
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
			if (file.links() != null) {
				links.putAll(file.links());
			}
			if (file.tones() != null) {
				toneKinds.putAll(file.tones());
			}
		} catch (IOException | RuntimeException e) {
			Observer.LOGGER.error("Could not load the Observer's house copy", e);
		}
		try (InputStream in = Copy.class.getResourceAsStream(TRIVIA_PATH)) {
			if (in == null) {
				throw new IOException("missing " + TRIVIA_PATH);
			}
			Trivia file = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Trivia.class);
			file.topics().forEach((topic, facts) -> facts.forEach(fact -> trivia.add(topic + "|" + fact)));
		} catch (IOException | RuntimeException e) {
			Observer.LOGGER.error("Could not load the Observer's trivia", e);
		}
	}

	/** The template for a kind of story in the paper's current tone, or the house's own if that tone has none. */
	public Kind kind(String kind, Tone tone) {
		Kind toned = toneKinds.getOrDefault(tone.id, Map.of()).get(kind);
		return toned != null ? toned : kind(kind);
	}

	public Kind kind(String kind) {
		return kinds.getOrDefault(kind, kinds.get("quiet"));
	}

	/** One story in house style. */
	public Article write(Story story, Brief brief, int index) {
		Kind kind = kind(story.kind(), Tone.of(Observer.config().tone));
		Random random = new Random(seed(brief, index));
		Map<String, String> slots = slots(story, brief);
		String headline = fill(kind.headlines().get(random.nextInt(kind.headlines().size())), slots);
		String body = fill(kind.bodies().get(random.nextInt(kind.bodies().size())), slots);
		// A death story already tells the death: its connection is told from the other side.
		String linkKind = slots.getOrDefault("linkkind", "");
		boolean deathStory = story.kind().equals("death") || story.kind().equals("deaths");
		List<String> told = deathStory && links.containsKey(linkKind + ".death") ? links.get(linkKind + ".death") : links.get(linkKind);
		if (told != null && !told.isEmpty()) {
			Map<String, String> link = new HashMap<>(slots);
			slots.forEach((k, v) -> {
				if (k.startsWith("link_")) {
					link.put(k.substring(5), v);
				}
			});
			body += " " + fill(told.get(random.nextInt(told.size())), link);
		}
		return new Article(story.section(), headline, body);
	}

	/** The whole edition in house style. {@code written} holds the press's stories; missing ones and nulls are filled in here. */
	public Edition print(Brief brief, List<Article> written, boolean pressWritten) {
		return print(brief, written, pressWritten, List.of());
	}

	/** As {@link #print(Brief, List, boolean)}, with the press's letters and columns ahead of the house fillers. */
	public Edition print(Brief brief, List<Article> written, boolean pressWritten, List<Article> pressFillers) {
		List<Article> stories = new ArrayList<>();
		List<Story> lineUp = brief.stories().isEmpty() ? List.of(quiet()) : brief.stories();
		for (int i = 0; i < lineUp.size(); i++) {
			Article article = i < written.size() ? written.get(i) : null;
			stories.add(article != null ? article : write(lineUp.get(i), brief, i));
		}
		// Fillers for whatever room the news leaves: the press's letters and columns, then the house's opinion
		// columns. The page layout uses them in order, and only while the news pages have space; after them come
		// the trivia facts, which also fill the "Did You Know?" column.
		Random random = new Random(seed(brief, 1000));
		List<Article> fillers = new ArrayList<>(pressFillers);
		List<Article> columns = new ArrayList<>(opinions);
		while (fillers.size() < 4 + pressFillers.size() && !columns.isEmpty()) {
			fillers.add(columns.remove(random.nextInt(columns.size())));
		}
		List<String> facts = new ArrayList<>(trivia);
		Collections.shuffle(facts, random);
		List<String> pickedTrivia = List.copyOf(facts.subList(0, Math.min(TRIVIA_PER_EDITION, facts.size())));

		List<String> ads = new ArrayList<>(classifieds);
		List<String> picked = new ArrayList<>();
		for (int i = 0; i < 3 && !ads.isEmpty(); i++) {
			picked.add(ads.remove(random.nextInt(ads.size())));
		}
		String weather = brief.weather() + (outlooks.isEmpty() ? "" : "|" + outlooks.get(random.nextInt(outlooks.size())));
		Frequency frequency = Frequency.ofDays(brief.periodDays());
		return new Edition(brief.paper(), brief.issue(), brief.day(), brief.founding(), reword(stories, frequency), reword(fillers, frequency),
				brief.obituaries(), brief.numbers(), brief.players(), brief.boards(), picked.stream().map(frequency::reword).toList(),
				frequency.reword(weather), pressWritten, Edition.Exchange.NONE, pickedTrivia, Observer.config().tone);
	}

	private static List<Article> reword(List<Article> articles, Frequency frequency) {
		return articles.stream().map(a -> new Article(a.section(), frequency.reword(a.headline()), frequency.reword(a.body()))).toList();
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
		return brief.paper().hashCode() * 31L + brief.printing() * 1009L + index;
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
