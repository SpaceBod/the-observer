package dev.spacebod.observer.news;

import com.google.gson.Gson;
import dev.spacebod.observer.Observer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds the threads that join a player's week together (assets/spacebod_observer/observer/links.json): a death
 * that follows from something else they did, like crashing while gliding the week they earned their elytra, or
 * being killed by the very creature they spent the week hunting. Each connection is one plain fact, given to
 * the stories it joins and to the player's dossier, so the writer can tell the two halves as one story.
 */
final class Links {
	private static final String PATH = "/assets/spacebod_observer/observer/links.json";
	private static Links instance;

	private File file = new File(List.of(), new Rivals(8, 18), new SameDay(10));

	private record Evidence(String stat, int min, double divide, String says) {
	}

	private record Rule(String id, List<String> deaths, List<String> advancements, List<Evidence> stats, String how, double bonus) {
	}

	private record Rivals(int min, double bonus) {
	}

	private record SameDay(double bonus) {
	}

	private record File(List<Rule> rules, Rivals rivals, SameDay sameDay) {
	}

	/**
	 * One connection.
	 *
	 * @param fact     the connection as a sentence, with every number and name it needs
	 * @param bonus    added to the score of each story it joins
	 * @param deaths   whether it joins the player's Mishaps story
	 * @param titles   the advancements whose stories it joins
	 * @param mob      the creature whose hunting story it joins; null for none
	 * @param kind     which of the house copy's link sentences tell it ("death", "rival", "sameday")
	 * @param slots    what those sentences fill in
	 */
	record Link(String fact, double bonus, boolean deaths, Set<String> titles, @Nullable String mob, String kind, Map<String, String> slots) {
		boolean joins(Story story) {
			if (deaths && (story.kind().equals("death") || story.kind().equals("deaths"))) {
				return true;
			}
			if (story.kind().startsWith("advancement.") && titles.contains(story.slots().get("title"))) {
				return true;
			}
			return mob != null && mob.equals(story.slots().get("mob"));
		}
	}

	static synchronized Links get() {
		if (instance == null) {
			instance = new Links();
		}
		return instance;
	}

	private Links() {
		try (InputStream in = Links.class.getResourceAsStream(PATH)) {
			if (in == null) {
				throw new IOException("missing " + PATH);
			}
			file = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), File.class);
		} catch (IOException | RuntimeException e) {
			Observer.LOGGER.error("Could not load the Observer's links", e);
		}
	}

	List<Link> find(NewsDesk.Beat beat) {
		List<Link> links = new ArrayList<>();
		String name = beat.name();
		List<Ledger.Event> deaths = beat.events().stream().filter(e -> e.kind().equals("death")).toList();
		List<Ledger.Event> advancements = beat.events().stream().filter(e -> e.kind().startsWith("advancement.")).toList();
		Set<Ledger.Event> joined = new HashSet<>();

		for (Ledger.Event death : deaths) {
			for (Rule rule : file.rules()) {
				if (!matches(death.key(), rule.deaths())) {
					continue;
				}
				String evidence = null;
				Set<String> titles = Set.of();
				Ledger.Event earned = advancements.stream().filter(a -> a.key() != null && rule.advancements().contains(a.key())).findFirst().orElse(null);
				if (earned != null) {
					evidence = name + " earned the advancement \"" + earned.text() + "\" on day " + earned.day();
					titles = Set.of(earned.text());
					joined.add(earned);
				} else {
					for (Evidence stat : rule.stats()) {
						int value = beat.get(stat.stat());
						if (value >= stat.min()) {
							long n = Math.round(value / stat.divide());
							evidence = name + " " + stat.says().replace("{{n}}", NewsDesk.fmt(n)).replace("{{s}}", n == 1 ? "" : "s");
							break;
						}
					}
				}
				if (evidence != null) {
					String when = earned != null && earned.day() == death.day() ? "The same day" : "On day " + death.day();
					links.add(new Link(evidence + ". " + when + ", they " + rule.how() + " (\"" + death.text() + "\").",
							rule.bonus(), true, titles, null, "death",
							Map.of("player", name, "how", rule.how(), "day", Long.toString(death.day()),
									"deed", earned != null ? "earned the advancement \"" + earned.text() + "\"" : evidence.substring(name.length() + 1))));
					joined.add(death);
					break;
				}
			}
		}

		for (Map.Entry<String, Integer> entry : beat.diff().entrySet()) {
			if (!entry.getKey().startsWith("killed_by:") || entry.getValue() <= 0) {
				continue;
			}
			String id = entry.getKey().substring("killed_by:".length());
			int defeated = beat.get("killed:" + id);
			if (defeated >= file.rivals().min()) {
				String mob = NewsDesk.entityName(id);
				String mobs = NewsDesk.plural(mob, 2).toLowerCase(Locale.ROOT);
				String times = NewsDesk.times(entry.getValue());
				String period = beat.lifetime() ? "so far" : "this week";
				links.add(new Link(Character.toUpperCase(mobs.charAt(0)) + mobs.substring(1) + " also killed " + name + " " + times + " " + period + ".",
						file.rivals().bonus(), true, Set.of(), mob, "rival",
						Map.of("player", name, "mobs", mobs, "times", times, "period", period)));
			}
		}

		for (Ledger.Event death : deaths) {
			if (joined.contains(death)) {
				continue;
			}
			for (Ledger.Event earned : advancements) {
				if (earned.day() == death.day() && !joined.contains(earned)) {
					links.add(new Link("Day " + death.day() + " brought " + name + " both the advancement \"" + earned.text()
							+ "\" and a death (\"" + death.text() + "\").", file.sameDay().bonus(), true, Set.of(earned.text()), null, "sameday",
							Map.of("player", name, "title", earned.text(), "cause", NewsDesk.stripName(death.text(), name), "day", Long.toString(death.day()),
									"deed", "earned the advancement \"" + earned.text() + "\"")));
					joined.add(death);
					joined.add(earned);
					break;
				}
			}
		}
		return links;
	}

	/** A key matches a pattern that is the key itself or a parent of it ("death.fell" matches "death.fell.accident.ladder"). */
	private static boolean matches(String key, List<String> patterns) {
		if (key == null || key.isEmpty()) {
			return false;
		}
		for (String pattern : patterns) {
			if (key.equals(pattern) || key.startsWith(pattern + ".")) {
				return true;
			}
		}
		return false;
	}
}
