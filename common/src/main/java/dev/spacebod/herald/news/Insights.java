package dev.spacebod.herald.news;

import com.google.gson.Gson;
import dev.spacebod.herald.Herald;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads meaning into vanilla statistics (assets/spacebod_herald/herald/insights.json): firsts (a statistic
 * that was zero before this edition) and pursuits (a sum of statistics for one activity, compared with the
 * player's own past editions). Each becomes a candidate story and a line for the "In Brief" column.
 */
final class Insights {
	private static final String PATH = "/assets/spacebod_herald/herald/insights.json";
	/** Editions of history needed before "usual" means anything. */
	private static final int MIN_HISTORY = 2;
	private static Insights instance;

	private final List<First> firsts = new ArrayList<>();
	private final List<Pursuit> pursuits = new ArrayList<>();

	private record First(String stat, String fact, double score) {
	}

	private record Pursuit(String id, String section, String measure, List<String> stats, double divide, double min) {
	}

	private record File(List<First> firsts, List<Pursuit> pursuits) {
	}

	/** Candidate stories and the short lines for everything else worth a mention. */
	record Reading(List<Story> stories, List<String> briefs) {
	}

	static synchronized Insights get() {
		if (instance == null) {
			instance = new Insights();
		}
		return instance;
	}

	private Insights() {
		try (InputStream in = Insights.class.getResourceAsStream(PATH)) {
			if (in == null) {
				throw new IOException("missing " + PATH);
			}
			File file = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), File.class);
			firsts.addAll(file.firsts());
			pursuits.addAll(file.pursuits());
		} catch (IOException | RuntimeException e) {
			Herald.LOGGER.error("Could not load the Herald's insights", e);
		}
	}

	Reading read(NewsDesk.Beat beat) {
		List<Story> stories = new ArrayList<>();
		List<String> briefs = new ArrayList<>();
		String name = beat.name();

		// A whole life's statistics (a founding edition, a new resident) has no "before" to have a first in.
		if (!beat.lifetime()) {
			for (First first : firsts) {
				if (beat.get(first.stat()) > 0 && beat.before(first.stat()) == 0) {
					stories.add(new Story.Builder("first", "Milestones", name, first.score())
							.slot("player", name).slot("fact", first.fact())
							.fact(name + " " + first.fact() + " for the first time.")
							.build());
					briefs.add(name + " " + first.fact() + " for the first time.");
				}
			}
		}

		for (Pursuit pursuit : pursuits) {
			double value = measure(pursuit, beat.diff());
			if (value < pursuit.min()) {
				continue;
			}
			String amount = format(value, pursuit.divide());
			List<Double> past = beat.history().stream().map(week -> measure(pursuit, week)).toList();
			double usual = past.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			boolean known = past.size() >= MIN_HISTORY;
			if (known && usual < pursuit.min() / 4) {
				stories.add(new Story.Builder("pursuit.new", pursuit.section(), name, 30)
						.slot("player", name).slot("amount", amount).slot("measure", pursuit.measure()).slot("activity", pursuit.id())
						.fact(name + " took up " + pursuit.id() + " this week: " + amount + " " + pursuit.measure() + ".")
						.fact("Before this week, " + name + " hardly did any " + pursuit.id() + ".")
						.build());
			} else if (known && value >= 2 * usual) {
				String usualText = format(usual, pursuit.divide());
				double ratio = value / Math.max(usual, 0.001);
				stories.add(new Story.Builder("pursuit.record", pursuit.section(), name, 22 + 4 * Math.min(ratio, 6))
						.slot("player", name).slot("amount", amount).slot("measure", pursuit.measure()).slot("usual", usualText)
						.slot("activity", pursuit.id())
						.fact(name + " had a big week of " + pursuit.id() + ": " + amount + " " + pursuit.measure() + ".")
						.fact("In a usual week " + name + " manages about " + usualText + ".")
						.build());
			}
			briefs.add(name + ": " + amount + " " + pursuit.measure() + (known ? " (usually " + format(usual, pursuit.divide()) + ")" : "") + ".");
		}
		return new Reading(stories, briefs);
	}

	private static double measure(Pursuit pursuit, Map<String, Integer> stats) {
		long total = 0;
		for (String stat : pursuit.stats()) {
			if (stat.endsWith(":*")) {
				String prefix = stat.substring(0, stat.length() - 1);
				for (Map.Entry<String, Integer> entry : stats.entrySet()) {
					if (entry.getKey().startsWith(prefix)) {
						total += entry.getValue();
					}
				}
			} else {
				total += stats.getOrDefault(stat, 0);
			}
		}
		return pursuit.divide() > 0 ? total / pursuit.divide() : total;
	}

	private static String format(double value, double divide) {
		return NewsDesk.amount(value, divide > 0);
	}
}
