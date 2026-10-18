package dev.spacebod.observer.news;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.edition.Edition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The leaderboards pages: each edition picks the most interesting handful from a pool of candidate rankings
 * (insights.json "leaderboards", plus one per creature anyone killed). The pool is run twice: since the last
 * edition for the Leaderboard page, and over all time for the Hall of Fame.
 */
final class Leaderboards {
	private static final String PATH = "/assets/spacebod_observer/observer/insights.json";
	private static final int ROWS = 3;
	private static Leaderboards instance;

	private final Spec spec;

	private record Candidate(String id, String title, String unit, List<String> stats, double weight, double min, double divide) {
	}

	private record MobBoard(String title, String unit, double weight, double min) {
	}

	/** @param count boards per page */
	private record Spec(int count, List<Candidate> boards, MobBoard mobBoard) {
	}

	/** A board as picked, with the ids to remember so it rests next time. */
	record Picked(List<Edition.Board> boards, List<String> ids) {
	}

	private record Scored(String id, Edition.Board board, double score) {
	}

	static synchronized Leaderboards get() {
		if (instance == null) {
			instance = new Leaderboards();
		}
		return instance;
	}

	private Leaderboards() {
		Spec loaded = new Spec(0, List.of(), new MobBoard("", "", 0, 0));
		try (InputStream in = Leaderboards.class.getResourceAsStream(PATH)) {
			if (in == null) {
				throw new IOException("missing " + PATH);
			}
			Gson gson = new Gson();
			JsonObject json = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			loaded = gson.fromJson(json.get("leaderboards"), Spec.class);
		} catch (IOException | RuntimeException e) {
			Observer.LOGGER.error("Could not load the Observer's leaderboards", e);
		}
		spec = loaded;
	}

	/**
	 * @param beats      every active player this edition
	 * @param lastShown  board ids from the previous edition
	 */
	Picked pick(List<NewsDesk.Beat> beats, List<String> lastShown, long seed) {
		Random random = new Random(seed);
		List<Scored> scored = new ArrayList<>();
		List<Candidate> candidates = new ArrayList<>(spec.boards());
		candidates.addAll(mobCandidates(beats));
		List<Scored> edition = new ArrayList<>();
		List<Scored> allTime = new ArrayList<>();
		for (Candidate candidate : candidates) {
			score(candidate, beats, false, lastShown, random, edition);
			score(candidate, beats, true, lastShown, random, allTime);
		}
		// This edition's leaders on one page, the Hall of Fame (all time) on the other.
		List<Edition.Board> boards = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		for (List<Scored> list : List.of(edition, allTime)) {
			list.sort(Comparator.comparingDouble(Scored::score).reversed());
			for (int i = 0; i < Math.min(spec.count(), list.size()); i++) {
				boards.add(list.get(i).board());
				ids.add(list.get(i).id());
			}
		}
		return new Picked(boards, ids);
	}

	private void score(Candidate c, List<NewsDesk.Beat> beats, boolean allTime, List<String> lastShown, Random random, List<Scored> out) {
		List<Map.Entry<String, Double>> values = new ArrayList<>();
		double total = 0;
		for (NewsDesk.Beat beat : beats) {
			double v = measure(c, allTime ? beat.latest() : beat.diff());
			if (v >= c.min()) {
				values.add(Map.entry(beat.name(), v));
				total += v;
			}
		}
		if (values.isEmpty()) {
			return;
		}
		values.sort(Map.Entry.<String, Double>comparingByValue().reversed());
		String id = (allTime ? "all:" : "") + c.id();
		double score = c.weight() * (1 + Math.log10(1 + total / Math.max(1, c.min())));
		if (values.size() >= 2) {
			score *= 2; // a real race beats a one-horse board
		}
		if (lastShown.contains(id)) {
			score *= 0.5;
		}
		score *= 0.8 + 0.4 * random.nextDouble();

		List<Edition.Row> rows = new ArrayList<>();
		for (int i = 0; i < Math.min(ROWS, values.size()); i++) {
			rows.add(new Edition.Row(values.get(i).getKey(), format(values.get(i).getValue(), c.divide())));
		}
		out.add(new Scored(id, new Edition.Board(c.title(), c.unit(), rows, allTime), score));
	}

	/** One "Top Zombie Hunter" candidate per creature anyone killed. */
	private List<Candidate> mobCandidates(List<NewsDesk.Beat> beats) {
		Set<String> mobs = new java.util.TreeSet<>();
		for (NewsDesk.Beat beat : beats) {
			for (String key : beat.latest().keySet()) {
				if (key.startsWith("killed:")) {
					mobs.add(key.substring("killed:".length()));
				}
			}
		}
		MobBoard template = spec.mobBoard();
		List<Candidate> out = new ArrayList<>();
		for (String mob : mobs) {
			String name = NewsDesk.entityName(mob);
			out.add(new Candidate("mob:" + mob, template.title().replace("{mob}", name),
					template.unit().replace("{mobs}", NewsDesk.plural(name, 2).toLowerCase(Locale.ROOT)),
					List.of("killed:" + mob), template.weight(), template.min(), 0));
		}
		return out;
	}

	private static double measure(Candidate c, Map<String, Integer> stats) {
		long total = 0;
		for (String stat : c.stats()) {
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
		return c.divide() > 0 ? total / c.divide() : total;
	}

	private static String format(double value, double divide) {
		return NewsDesk.amount(value, divide > 0);
	}
}
