package dev.spacebod.herald.news;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Decides what is news. Compares each player's statistics now with the last edition's, adds the deaths and
 * advancements the statistics cannot describe, scores every candidate story and picks the edition's line-up:
 * the biggest story leads, and no single player or kind of story is allowed to fill the whole paper.
 */
public final class NewsDesk {
	private static final long CM_PER_KM = 100_000L;
	private static final int MAX_BRIEFS = 12;
	private static final int TICKS_PER_HOUR = 20 * 60 * 60;
	private static final String[] MOON_PHASES = {"full", "waning gibbous", "last quarter", "waning crescent", "new",
			"waxing crescent", "first quarter", "waxing gibbous"};

	private NewsDesk() {
	}

	/**
	 * One player's period, as the desk sees it.
	 *
	 * @param diff     statistics gained since the last edition
	 * @param latest   statistics now
	 * @param history  their diffs in earlier editions, oldest first
	 * @param lifetime whether {@code diff} is their whole life so far (first edition they appear in)
	 */
	public record Beat(String name, Map<String, Integer> diff, Map<String, Integer> latest, List<Map<String, Integer>> history,
					   List<Ledger.Event> events, boolean newcomer, boolean lifetime) {
		int get(String key) {
			return diff.getOrDefault(key, 0);
		}

		int sum(String prefix) {
			int total = 0;
			for (Map.Entry<String, Integer> entry : diff.entrySet()) {
				if (entry.getKey().startsWith(prefix)) {
					total += entry.getValue();
				}
			}
			return total;
		}

		/** The id with the highest count under a prefix, without the prefix. */
		Optional<Map.Entry<String, Integer>> top(String prefix) {
			return diff.entrySet().stream()
					.filter(e -> e.getKey().startsWith(prefix) && e.getValue() > 0)
					.max(Map.Entry.comparingByValue())
					.map(e -> Map.entry(e.getKey().substring(prefix.length()), e.getValue()));
		}

		/** The statistic's value at the last edition. */
		int before(String key) {
			return latest.getOrDefault(key, 0) - diff.getOrDefault(key, 0);
		}

		boolean active() {
			return get("custom:play_time") > 0 || !events.isEmpty();
		}
	}

	public static Brief compile(String paper, int issue, long day, int periodDays, boolean founding, List<Beat> beats,
								boolean raining, boolean thundering, int storyCount, List<String> lastBoards) {
		List<Beat> active = beats.stream().filter(Beat::active).toList();
		boolean crowd = active.size() > 1;

		List<Story> candidates = new ArrayList<>();
		List<String> briefs = new ArrayList<>();
		for (Beat beat : active) {
			candidates.addAll(storiesAbout(beat, crowd && !founding));
			Insights.Reading reading = Insights.get().read(beat);
			candidates.addAll(reading.stories());
			briefs.addAll(reading.briefs());
		}

		List<Story> lineUp = pick(candidates, storyCount);
		// "In Brief" gets what the stories didn't already say.
		List<String> told = lineUp.stream().flatMap(story -> story.facts().stream()).toList();
		briefs.removeIf(line -> told.stream().anyMatch(fact -> fact.equals(line)));
		List<String> obituaries = new ArrayList<>();
		for (Beat beat : active) {
			for (Ledger.Event event : beat.events) {
				if (event.kind().equals("death")) {
					obituaries.add("Day " + event.day() + ": " + event.text());
				}
			}
		}

		Leaderboards.Picked boards = Leaderboards.get().pick(beats, lastBoards, paper.hashCode() * 31L + issue);
		return new Brief(paper, issue, day, periodDays, founding, lineUp, obituaries, numbers(active),
				briefs.subList(0, Math.min(briefs.size(), MAX_BRIEFS)), boards.boards(), boards.ids(),
				weather(day, raining, thundering), active.stream().map(Beat::name).toList());
	}

	/** Greedy: take the best story, then make the rest about the same player or of the same kind less attractive. */
	static List<Story> pick(List<Story> candidates, int count) {
		List<Story> pool = new ArrayList<>(candidates);
		Map<Story, Double> weight = new IdentityHashMap<>();
		pool.forEach(story -> weight.put(story, story.score()));
		List<Story> picked = new ArrayList<>();
		while (picked.size() < count && !pool.isEmpty()) {
			Story best = pool.stream().max(Comparator.comparingDouble(weight::get)).orElseThrow();
			pool.remove(best);
			picked.add(best);
			for (Story other : pool) {
				double w = weight.get(other);
				if (best.subject() != null && best.subject().equals(other.subject())) {
					w *= 0.75;
				}
				if (family(best.kind()).equals(family(other.kind()))) {
					w *= 0.3;
				}
				weight.put(other, w);
			}
		}
		return picked;
	}

	private static String family(String kind) {
		int dot = kind.indexOf('.');
		return dot < 0 ? kind : kind.substring(0, dot);
	}

	private static List<Story> storiesAbout(Beat beat, boolean reportNewcomers) {
		List<Story> out = new ArrayList<>();
		String name = beat.name;

		// Mishaps
		int deaths = beat.get("custom:deaths");
		List<String> causes = beat.events.stream().filter(e -> e.kind().equals("death")).map(Ledger.Event::text).toList();
		if (deaths > 0) {
			String cause = causes.isEmpty() ? "met with an accident" : stripName(causes.getLast(), name);
			double score = 25 + 12 * Math.min(deaths, 10);
			Story.Builder story = new Story.Builder(deaths == 1 ? "death" : "deaths", "Mishaps", name, score)
					.slot("player", name).slot("count", fmt(deaths)).slot("cause", cause)
					.fact(name + " died " + times(deaths) + " since the last edition.");
			for (String c : causes.subList(Math.max(0, causes.size() - 3), causes.size())) {
				story.fact("Death notice: \"" + c + "\".");
			}
			out.add(story.build());
		}

		// Achievements
		for (Ledger.Event event : beat.events) {
			if (!event.kind().startsWith("advancement.")) {
				continue;
			}
			String frame = event.kind().substring("advancement.".length());
			double score = switch (frame) {
				case "challenge" -> 75;
				case "goal" -> 50;
				default -> 22;
			};
			Story.Builder story = new Story.Builder(event.kind(), "Achievements", name, score)
					.slot("player", name).slot("title", event.text()).slot("detail", event.detail())
					.slot("eventday", Long.toString(event.day()))
					.fact(name + " earned the advancement \"" + event.text() + "\" on day " + event.day() + ".");
			if (!event.detail().isEmpty()) {
				story.fact("The advancement is described as: \"" + event.detail() + "\".");
			}
			out.add(story.build());
		}

		// Community: bounties handed in at the Exchange
		for (Ledger.Event event : beat.events) {
			if (event.kind().equals("bounty")) {
				out.add(new Story.Builder("bounty", "Community", name, 26)
						.slot("player", name).slot("who", event.text()).slot("deed", event.detail())
						.fact(name + " " + event.detail() + " for " + event.text() + ", who had asked in the Exchange.")
						.build());
			}
		}

		// Monsters: the big ones are front-page news on their own
		for (String[] boss : new String[][]{{"ender_dragon", "100"}, {"wither", "90"}, {"warden", "85"}, {"elder_guardian", "55"}}) {
			int kills = beat.get("killed:" + boss[0]);
			if (kills > 0) {
				String mob = entityName(boss[0]);
				out.add(new Story.Builder("boss", "Breaking News", name, Double.parseDouble(boss[1]))
						.slot("player", name).slot("mob", mob).slot("count", fmt(kills))
						.fact(name + " defeated the " + mob + (kills > 1 ? " " + times(kills) : "") + ".")
						.build());
			}
		}
		int mobKills = beat.get("custom:mob_kills");
		Optional<Map.Entry<String, Integer>> topMob = beat.top("killed:");
		if (mobKills >= 15 && topMob.isPresent()) {
			String mob = entityName(topMob.get().getKey());
			int mobCount = topMob.get().getValue();
			out.add(new Story.Builder("hunter", "Public Safety", name, Math.min(45, 10 + mobKills / 3.0))
					.slot("player", name).slot("count", fmt(mobKills)).slot("mob", mob).slot("mobs", plural(mob, mobCount))
					.slot("mobcount", fmt(mobCount))
					.fact(name + " defeated " + fmt(mobKills) + " hostile and other creatures.")
					.fact("Most of them were " + plural(mob, mobCount) + ": " + fmt(mobCount) + ".")
					.build());
		}

		// Industry
		int mined = beat.sum("mined:");
		int diamonds = beat.get("mined:diamond_ore") + beat.get("mined:deepslate_diamond_ore");
		int debris = beat.get("mined:ancient_debris");
		Optional<Map.Entry<String, Integer>> topBlock = beat.top("mined:");
		if ((mined >= 150 || diamonds > 0 || debris > 0) && topBlock.isPresent()) {
			double score = Math.min(40, mined / 50.0) + 12 * Math.min(diamonds, 5) + 18 * Math.min(debris, 4);
			String block = blockName(topBlock.get().getKey());
			Story.Builder story = new Story.Builder(diamonds > 0 || debris > 0 ? "miner.rare" : "miner", "Industry", name, score)
					.slot("player", name).slot("count", fmt(mined)).slot("block", block.toLowerCase(Locale.ROOT))
					.slot("blockcount", fmt(topBlock.get().getValue())).slot("diamonds", fmt(diamonds)).slot("debris", fmt(debris))
					.fact(name + " mined " + fmt(mined) + " blocks.")
					.fact("The block mined most was " + block.toLowerCase(Locale.ROOT) + ": " + fmt(topBlock.get().getValue()) + ".");
			if (diamonds > 0) {
				story.fact(name + " mined " + fmt(diamonds) + " diamond ore.");
			}
			if (debris > 0) {
				story.fact(name + " mined " + fmt(debris) + " ancient debris.");
			}
			out.add(story.build());
		}

		// Property
		int placed = beat.sum("placed:");
		Optional<Map.Entry<String, Integer>> topPlaced = beat.top("placed:");
		if (placed >= 300 && topPlaced.isPresent()) {
			String block = itemName(topPlaced.get().getKey()).toLowerCase(Locale.ROOT);
			out.add(new Story.Builder("builder", "Property", name, Math.min(40, placed / 80.0))
					.slot("player", name).slot("count", fmt(placed)).slot("block", block)
					.fact(name + " placed " + fmt(placed) + " blocks.")
					.fact("Their favourite building material was " + block + ".")
					.build());
		}

		// Travel
		Map<String, Long> modes = new LinkedHashMap<>();
		modes.put("on foot", (long) beat.get("custom:walk_one_cm") + beat.get("custom:sprint_one_cm") + beat.get("custom:crouch_one_cm"));
		modes.put("by elytra", (long) beat.get("custom:aviate_one_cm"));
		modes.put("by boat", (long) beat.get("custom:boat_one_cm"));
		modes.put("on horseback", (long) beat.get("custom:horse_one_cm"));
		modes.put("by minecart", (long) beat.get("custom:minecart_one_cm"));
		modes.put("swimming", (long) beat.get("custom:swim_one_cm") + beat.get("custom:walk_under_water_one_cm"));
		long totalCm = modes.values().stream().mapToLong(Long::longValue).sum();
		double km = totalCm / (double) CM_PER_KM;
		if (km >= 3) {
			String mode = modes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
			String kmText = String.format(Locale.ROOT, "%.1f", km);
			out.add(new Story.Builder("traveller", "Travel", name, Math.min(40, km * 3))
					.slot("player", name).slot("km", kmText).slot("mode", mode)
					.fact(name + " travelled " + kmText + " km, mostly " + mode + ".")
					.build());
		}

		// Agriculture
		int bred = beat.get("custom:animals_bred");
		int fish = beat.get("custom:fish_caught");
		int crops = beat.get("mined:wheat") + beat.get("mined:carrots") + beat.get("mined:potatoes") + beat.get("mined:beetroots")
				+ beat.get("mined:melon") + beat.get("mined:pumpkin") + beat.get("mined:sugar_cane");
		if (bred >= 6 || fish >= 5 || crops >= 40) {
			Story.Builder story = new Story.Builder("farmer", "Agriculture", name, Math.min(35, bred * 1.5 + fish * 2 + crops / 8.0))
					.slot("player", name).slot("bred", fmt(bred)).slot("fish", fmt(fish)).slot("crops", fmt(crops));
			if (bred > 0) {
				story.fact(name + " bred " + fmt(bred) + " animals.");
			}
			if (fish > 0) {
				story.fact(name + " caught " + fmt(fish) + " fish.");
			}
			if (crops > 0) {
				story.fact(name + " harvested " + fmt(crops) + " crops.");
			}
			out.add(story.build());
		}

		// Commerce
		int trades = beat.get("custom:traded_with_villager");
		if (trades >= 4) {
			out.add(new Story.Builder("trader", "Commerce", name, Math.min(35, trades * 2.0))
					.slot("player", name).slot("count", fmt(trades))
					.fact(name + " completed " + fmt(trades) + " trades with villagers.")
					.build());
		}

		// Crime
		int playerKills = beat.get("custom:player_kills");
		if (playerKills > 0) {
			out.add(new Story.Builder("feud", "Crime", name, 60)
					.slot("player", name).slot("count", fmt(playerKills))
					.fact(name + " defeated other players " + times(playerKills) + ".")
					.build());
		}

		// Defence
		int raids = beat.get("custom:raid_win");
		if (raids > 0) {
			out.add(new Story.Builder("raid", "Defence", name, 70)
					.slot("player", name).slot("count", fmt(raids))
					.fact(name + " defended a village from a raid" + (raids > 1 ? ", " + times(raids) : "") + ".")
					.build());
		}

		// Tools & trades
		int broken = beat.sum("broken:");
		Optional<Map.Entry<String, Integer>> topBroken = beat.top("broken:");
		if (broken >= 3 && topBroken.isPresent()) {
			String tool = itemName(topBroken.get().getKey()).toLowerCase(Locale.ROOT);
			out.add(new Story.Builder("breaker", "Tools & Trades", name, 12 + 3 * Math.min(broken, 8))
					.slot("player", name).slot("count", fmt(broken)).slot("tool", tool)
					.fact(name + " wore out " + fmt(broken) + " tools.")
					.fact("The most worn out was the " + tool + ".")
					.build());
		}

		// Health
		int playTicks = beat.get("custom:play_time");
		if (playTicks >= 2 * 24000 && beat.get("custom:sleep_in_bed") == 0) {
			String hours = String.format(Locale.ROOT, "%.1f", playTicks / (double) TICKS_PER_HOUR);
			out.add(new Story.Builder("insomniac", "Health", name, 18)
					.slot("player", name).slot("hours", hours)
					.fact(name + " was awake for " + hours + " hours and did not sleep in a bed once.")
					.build());
		}

		// Sport and food: small, silly, and only for slow weeks
		int jumps = beat.get("custom:jump");
		if (jumps >= 1500) {
			out.add(new Story.Builder("jumper", "Sport", name, 10)
					.slot("player", name).slot("count", fmt(jumps))
					.fact(name + " jumped " + fmt(jumps) + " times.")
					.build());
		}
		int cake = beat.get("custom:eat_cake_slice");
		if (cake >= 7) {
			out.add(new Story.Builder("cake", "Food", name, 12)
					.slot("player", name).slot("count", fmt(cake))
					.fact(name + " ate " + fmt(cake) + " slices of cake.")
					.build());
		}

		// Community
		if (reportNewcomers && beat.newcomer) {
			out.add(new Story.Builder("newcomer", "Community", name, 40)
					.slot("player", name)
					.fact(name + " is new in town and arrived since the last edition.")
					.build());
		}
		return out;
	}

	/** The town's totals since the last edition; who led them is the leaderboards page's job. */
	private static List<String> numbers(List<Beat> beats) {
		List<String> rows = new ArrayList<>();
		row(rows, beats, "Blocks mined", b -> (long) b.sum("mined:"), NewsDesk::fmt);
		row(rows, beats, "Blocks placed", b -> (long) b.sum("placed:"), NewsDesk::fmt);
		row(rows, beats, "Creatures defeated", b -> (long) b.get("custom:mob_kills"), NewsDesk::fmt);
		row(rows, beats, "Distance", b -> (long) b.get("custom:walk_one_cm") + b.get("custom:sprint_one_cm")
						+ b.get("custom:aviate_one_cm") + b.get("custom:boat_one_cm") + b.get("custom:horse_one_cm") + b.get("custom:swim_one_cm"),
				cm -> String.format(Locale.ROOT, "%.1f km", cm / (double) CM_PER_KM));
		row(rows, beats, "Diamonds found", b -> (long) b.get("mined:diamond_ore") + b.get("mined:deepslate_diamond_ore"), NewsDesk::fmt);
		row(rows, beats, "Villager trades", b -> (long) b.get("custom:traded_with_villager"), NewsDesk::fmt);
		row(rows, beats, "Deaths", b -> (long) b.get("custom:deaths"), NewsDesk::fmt);
		return rows.subList(0, Math.min(rows.size(), 6));
	}

	/** A row only when it says something: totals that print as zero ("0.0 km") are left out. */
	private static void row(List<String> rows, List<Beat> beats, String label, Function<Beat, Long> value, Function<Long, String> format) {
		long total = 0;
		for (Beat beat : beats) {
			total += value.apply(beat);
		}
		String shown = format.apply(total);
		if (total > 0 && !shown.matches("0(\\.0+)?( .*)?")) {
			rows.add(label + "|" + shown);
		}
	}

	private static String weather(long day, boolean raining, boolean thundering) {
		String today = thundering ? "Thunderstorms" : raining ? "Rain" : "Fair";
		// Day 1 starts on a full moon; tonight is the next phase.
		String moon = MOON_PHASES[(int) Math.floorMod(day, 8L)];
		return today + "|" + moon;
	}

	static String fmt(long n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	/** A measured amount: one decimal place while small ("2.5 km"), whole with separators once it's large ("18,497"). */
	static String amount(double value, boolean fractional) {
		return fractional && value < 100 ? String.format(Locale.ROOT, "%.1f", value) : fmt(Math.round(value));
	}

	private static String times(int n) {
		return n == 1 ? "once" : n == 2 ? "twice" : fmt(n) + " times";
	}

	/** "Steve was blown up by Creeper" -> "was blown up by Creeper". */
	static String stripName(String deathMessage, String name) {
		return deathMessage.startsWith(name + " ") ? deathMessage.substring(name.length() + 1) : deathMessage;
	}

	static String entityName(String shortId) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(shortId))
				.map(type -> Component.translatable(type.getDescriptionId()).getString())
				.orElse(shortId);
	}

	static String blockName(String shortId) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(shortId))
				.map(block -> Component.translatable(block.getDescriptionId()).getString())
				.orElse(shortId);
	}

	static String itemName(String shortId) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(shortId))
				.map(item -> Component.translatable(item.getDescriptionId()).getString())
				.orElse(shortId);
	}

	/** English plural for creature names, good enough for the vanilla bestiary. */
	static String plural(String name, long count) {
		if (count == 1) {
			return name;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith("man")) {
			return name.substring(0, name.length() - 3) + "men";
		}
		if (lower.endsWith("fish") || lower.endsWith("sheep") || lower.endsWith("squid")) {
			return name;
		}
		if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("s") || lower.endsWith("x")) {
			return name + "es";
		}
		if (lower.endsWith("y") && !lower.endsWith("ey") && !lower.endsWith("ay") && !lower.endsWith("oy")) {
			return name.substring(0, name.length() - 1) + "ies";
		}
		if (lower.endsWith("wolf")) {
			return name.substring(0, name.length() - 1) + "ves";
		}
		return name + "s";
	}
}
