package dev.spacebod.observer.news;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Everything worth knowing about one player this edition, as short numbered facts: what they did and how much,
 * how that compares with their usual week and with everyone else, and what the paper said about them before.
 * The editor reads every dossier and picks which facts each story should also use; the ids let it point at
 * facts without being able to invent any.
 *
 * @param facts in order of interest; ids are the player's letter and a number ("B4")
 */
public record Dossier(String player, List<Fact> facts) {
	public record Fact(String id, String text) {
	}

	/** A measure the dossier reports, ranks and compares with the player's usual week. */
	private record Measure(String noun, ToLongFunction<Map<String, Integer>> value, long min) {
	}

	/** Enough for the editor to find an angle; more only lengthens its prompt. */
	private static final int MAX_FACTS = 14;

	private static final List<Measure> MEASURES = List.of(
			new Measure("blocks mined", s -> sum(s, "mined:"), 50),
			new Measure("blocks placed", s -> sum(s, "placed:"), 50),
			new Measure("creatures defeated", s -> s.getOrDefault("custom:mob_kills", 0), 5),
			new Measure("deaths", s -> s.getOrDefault("custom:deaths", 0), 1),
			new Measure("items crafted", s -> sum(s, "crafted:"), 20),
			new Measure("villager trades", s -> s.getOrDefault("custom:traded_with_villager", 0), 1),
			new Measure("fish caught", s -> s.getOrDefault("custom:fish_caught", 0), 1),
			new Measure("animals bred", s -> s.getOrDefault("custom:animals_bred", 0), 1),
			new Measure("tools worn out", s -> sum(s, "broken:"), 1));

	/**
	 * @param letter     the player's id letter ("A", "B"...)
	 * @param everyone   all of this edition's active players, for rankings
	 * @param links      connections between things they did this week, most telling first
	 * @param notes      the insights' lines about them (pursuits against their usual week, firsts)
	 * @param previously what earlier editions said about them, newest first
	 */
	static Dossier of(NewsDesk.Beat beat, String letter, List<NewsDesk.Beat> everyone, List<String> links, List<String> notes,
					  List<String> previously) {
		List<String> lines = new ArrayList<>(links);
		Map<String, Integer> diff = beat.diff();
		for (Measure m : MEASURES) {
			long value = m.value().applyAsLong(diff);
			if (value < m.min()) {
				continue;
			}
			StringBuilder line = new StringBuilder(beat.name() + ": " + NewsDesk.fmt(value) + " " + m.noun());
			if (everyone.size() > 1) {
				List<NewsDesk.Beat> ranked = everyone.stream()
						.sorted(Comparator.comparingLong((NewsDesk.Beat b) -> m.value().applyAsLong(b.diff())).reversed()).toList();
				int rank = ranked.indexOf(beat) + 1;
				if (rank <= 3) {
					line.append(", ").append(ordinal(rank)).append(" of ").append(everyone.size()).append(" in town");
				}
			}
			if (beat.history().size() >= 2) {
				double usual = beat.history().stream().mapToLong(m.value()).average().orElse(0);
				line.append(", usually about ").append(NewsDesk.fmt(Math.round(usual))).append(" a week");
			}
			lines.add(line.append('.').toString());
		}
		beat.top("mined:").ifPresent(top -> lines.add("Most mined block: " + NewsDesk.blockName(top.getKey()).toLowerCase(Locale.ROOT)
				+ " (" + NewsDesk.fmt(top.getValue()) + ")."));
		beat.top("placed:").ifPresent(top -> lines.add("Favourite building block: " + NewsDesk.itemName(top.getKey()).toLowerCase(Locale.ROOT)
				+ " (" + NewsDesk.fmt(top.getValue()) + ")."));
		beat.top("killed:").ifPresent(top -> lines.add("Most defeated creature: " + NewsDesk.plural(NewsDesk.entityName(top.getKey()), top.getValue())
				.toLowerCase(Locale.ROOT) + " (" + NewsDesk.fmt(top.getValue()) + ")."));
		beat.top("killed_by:").ifPresent(top -> lines.add("Most often killed by: " + NewsDesk.entityName(top.getKey()).toLowerCase(Locale.ROOT) + "."));
		for (Ledger.Event event : beat.events()) {
			if (links.stream().anyMatch(link -> link.contains("\"" + event.text() + "\""))) {
				continue; // a connection already tells it
			}
			switch (event.kind()) {
				case "death" -> lines.add("Death notice, day " + event.day() + ": \"" + event.text() + "\".");
				case "bounty" -> lines.add(beat.name() + " " + event.detail() + " for " + event.text() + ".");
				default -> {
					if (event.kind().startsWith("advancement.")) {
						lines.add("Earned the advancement \"" + event.text() + "\" on day " + event.day() + ".");
					}
				}
			}
		}
		lines.addAll(notes);
		for (String before : previously) {
			lines.add("Previously in the Observer: " + before);
		}

		List<Fact> facts = new ArrayList<>();
		for (int i = 0; i < Math.min(lines.size(), MAX_FACTS); i++) {
			facts.add(new Fact(letter + (i + 1), lines.get(i)));
		}
		return new Dossier(beat.name(), facts);
	}

	public Fact fact(String id) {
		return facts.stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
	}

	/** The dossier as the editor reads it: one fact per line, with its id. */
	public String text() {
		StringBuilder out = new StringBuilder(player).append(":\n");
		for (Fact fact : facts) {
			out.append("  ").append(fact.id()).append(' ').append(fact.text()).append('\n');
		}
		return out.toString();
	}

	private static long sum(Map<String, Integer> stats, String prefix) {
		long total = 0;
		for (Map.Entry<String, Integer> entry : stats.entrySet()) {
			if (entry.getKey().startsWith(prefix)) {
				total += entry.getValue();
			}
		}
		return total;
	}

	private static String ordinal(int n) {
		return switch (n) {
			case 1 -> "1st";
			case 2 -> "2nd";
			case 3 -> "3rd";
			default -> n + "th";
		};
	}
}
