package dev.spacebod.observer.news;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.Rewards;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.exchange.Bounties;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The newsroom's notebook for one world, kept in {@code <world>/spacebod_observer/}: who lives here, their
 * statistics as of the last edition, what happened to them since, and the latest edition itself (so late
 * arrivals still get their copy). Plain JSON, so it can be read or reset by hand.
 */
public final class Ledger {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String LEDGER_FILE = "ledger.json";
	private static final String EDITION_FILE = "latest_edition.json";

	/** Issue number of the last edition, counting up by one with each new edition; 0 before the first. */
	public int issue;
	/** The period the last edition covered, as "days:index", so publishing again within it reprints it. */
	public @Nullable String lastPeriod;
	/** Editions printed in this world; reprints of a week don't count. */
	public int editions;
	/** Every printing, reprints included: varies the wording of a reprint. */
	public int printings;
	/** The printing on the newsstands now ({@link #printings} as it was when the latest edition came off the press). */
	public int latestPrinting;
	/** The ledger as it was just before the last edition, so that week can be printed again from the same data. */
	public @Nullable Reopen reopen;
	/** Overworld day the next edition is due, or -1 until the newsroom has seen the world. */
	public long nextEditionDay = -1;
	/** Leaderboards printed last time, which rest this time. */
	public List<String> lastBoards = new ArrayList<>();
	/** By player UUID. */
	public Map<String, PlayerRecord> players = new LinkedHashMap<>();

	private transient Path dir;
	private transient @Nullable Edition latest;

	public static final class PlayerRecord {
		public String name = "";
		/** Statistics as of the last edition this player was in. Null: never reported on, so the first story covers their whole life. */
		public @Nullable Map<String, Integer> baseline;
		/** Statistics as of the last time they were seen (now, if online). */
		public Map<String, Integer> latest = new HashMap<>();
		/** Things the statistics cannot tell: how each death happened, which advancements were earned. */
		public List<Event> events = new ArrayList<>();
		/** Last issue this player has been handed. */
		public int lastIssueReceived;
		/** Last printing this player has been handed: a reprint of the same issue is a new copy to collect. */
		public int lastPrintingReceived;
		/** Last printing offered to this player without being handed over (turned away, or on sale at the stands): no second round when they rejoin. */
		public int lastPrintingOffered;
		/** Whether they have been told, at a newsstand, that they can collect there instead. */
		public boolean standHinted;
		/** How this player gets the paper: "dog" (brought to them) or "stand" (collected from a newsstand). */
		public String delivery = "dog";
		/** Joined since the last edition. */
		public boolean newcomer = true;
		/** This player's statistics changes in each of their last few editions, newest last: what "usual" means for them. */
		public List<Map<String, Integer>> history = new ArrayList<>();
		/** How many days each of those editions covered; missing entries were weekly. */
		public List<Integer> historyDays = new ArrayList<>();
		/** Exchange bounties handed in, as "issue:slot". */
		public List<String> bountiesDone = new ArrayList<>();
		/** What the last few editions said about this player ("No. 18: \"Alex Strikes It Rich\""), newest first. */
		public List<String> previously = new ArrayList<>();
	}

	/** Editions of history kept per player. */
	public static final int HISTORY = 8;

	/** What closing a week changed, kept so the week can be closed again. */
	public static final class Reopen {
		public Map<String, PlayerRecord> players = new LinkedHashMap<>();
		public List<String> lastBoards = new ArrayList<>();
		public boolean founding;
		public long exchangeSeed;
	}

	/**
	 * @param kind   "death", "advancement.task", "advancement.goal" or "advancement.challenge"
	 * @param text   the death message, or the advancement's title
	 * @param detail the advancement's description; empty for deaths
	 * @param key    what happened, in a form that doesn't depend on the language: the death message's translation
	 *               key ("death.attack.lava") or the advancement's id ("minecraft:end/elytra"); empty if unknown
	 */
	public record Event(String kind, String text, String detail, long day, String key) {
		public Event(String kind, String text, String detail, long day) {
			this(kind, text, detail, day, "");
		}
	}

	/** What closing a period produces: the brief for the press, and the Exchange that goes with it. */
	public record Closing(Brief brief, Edition.Exchange exchange) {
	}

	/**
	 * Closes the period since the last edition: compiles the brief and the Exchange, then moves every player's
	 * baseline on, files the period in their history and clears their events. {@code latest} must already be up
	 * to date for online players.
	 *
	 * <p>Each new edition is the next issue number. Closing again in the same period reprints it: the ledger goes back to how it
	 * was before that week's edition (keeping anything that happened since), and the Exchange keeps its seed, so
	 * the same notices, hand-ins and kill counts carry over.
	 */
	public Closing close(String paper, long day, int periodDays, boolean raining, boolean thundering, int storyCount, long exchangeSeed) {
		long index = (day - 1) / periodDays;
		String period = periodDays + ":" + index;
		// Ledgers from before frequencies kept only the week's number.
		String previous = lastPeriod != null ? lastPeriod : "7:" + (issue - 1);
		boolean reprint = editions > 0 && period.equals(previous) && reopen != null;
		int number = reprint ? issue : issue + 1;
		boolean founding;
		long seed;
		Map<String, Map<String, Integer>> statsAtOpening = new HashMap<>();
		if (reprint) {
			founding = reopen.founding;
			seed = reopen.exchangeSeed;
			restore(reopen, statsAtOpening);
		} else {
			founding = editions == 0;
			seed = exchangeSeed;
			reopen = snapshot(founding, seed);
		}

		List<NewsDesk.Beat> beats = new ArrayList<>();
		for (PlayerRecord record : players.values()) {
			beats.add(new NewsDesk.Beat(record.name, diff(record.baseline, record.latest), record.latest, scaledHistory(record, periodDays),
					List.copyOf(record.events), record.newcomer, record.baseline == null, List.copyOf(record.previously)));
		}
		long nextPeriod = (index + 1) * periodDays + 1;
		Brief brief = NewsDesk.compile(paper, number, printings, day, periodDays, founding, beats, raining, thundering, storyCount, lastBoards);
		lastBoards = brief.boardIds();
		Edition.Exchange exchange = exchange(seed, nextPeriod, periodDays, statsAtOpening);

		for (PlayerRecord record : players.values()) {
			Map<String, Integer> changes = diff(record.baseline, record.latest);
			if (record.baseline != null && !changes.isEmpty()) {
				while (record.historyDays.size() < record.history.size()) {
					record.historyDays.addFirst(7);
				}
				record.history.add(changes);
				record.historyDays.add(periodDays);
				while (record.history.size() > HISTORY) {
					record.history.removeFirst();
					record.historyDays.removeFirst();
				}
			}
			record.baseline = new HashMap<>(record.latest);
			record.events.clear();
			record.newcomer = false;
		}
		issue = number;
		lastPeriod = period;
		printings++;
		if (!reprint) {
			editions++;
		}
		nextEditionDay = nextPeriod;
		return new Closing(brief, exchange);
	}

	/**
	 * A player's past editions, each scaled to the length of this one, so a fortnight compares fairly with a
	 * usual week (or day) after the frequency changes.
	 */
	private static List<Map<String, Integer>> scaledHistory(PlayerRecord record, int periodDays) {
		List<Map<String, Integer>> out = new ArrayList<>();
		int offset = record.history.size() - record.historyDays.size();
		for (int i = 0; i < record.history.size(); i++) {
			int days = i - offset >= 0 ? record.historyDays.get(i - offset) : 7;
			if (days == periodDays) {
				out.add(record.history.get(i));
				continue;
			}
			double scale = periodDays / (double) days;
			Map<String, Integer> scaled = new HashMap<>();
			record.history.get(i).forEach((key, value) -> scaled.put(key, (int) Math.round(value * scale)));
			out.add(scaled);
		}
		return List.copyOf(out);
	}

	private Reopen snapshot(boolean founding, long seed) {
		Reopen saved = new Reopen();
		saved.founding = founding;
		saved.exchangeSeed = seed;
		saved.lastBoards = new ArrayList<>(lastBoards);
		for (Map.Entry<String, PlayerRecord> entry : players.entrySet()) {
			PlayerRecord from = entry.getValue();
			PlayerRecord copy = new PlayerRecord();
			copy.name = from.name;
			copy.baseline = from.baseline != null ? new HashMap<>(from.baseline) : null;
			copy.latest = new HashMap<>(from.latest);
			copy.events = new ArrayList<>(from.events);
			copy.newcomer = from.newcomer;
			copy.history = new ArrayList<>(from.history.stream().<Map<String, Integer>>map(HashMap::new).toList());
			copy.historyDays = new ArrayList<>(from.historyDays);
			copy.previously = new ArrayList<>(from.previously);
			saved.players.put(entry.getKey(), copy);
		}
		return saved;
	}

	/** Puts the week back as it was before its edition; what happened since stays. Notes each player's statistics at the time. */
	private void restore(Reopen saved, Map<String, Map<String, Integer>> statsAtOpening) {
		lastBoards = new ArrayList<>(saved.lastBoards);
		for (Map.Entry<String, PlayerRecord> entry : saved.players.entrySet()) {
			PlayerRecord record = players.get(entry.getKey());
			PlayerRecord before = entry.getValue();
			if (record == null) {
				continue;
			}
			record.baseline = before.baseline != null ? new HashMap<>(before.baseline) : null;
			record.history = new ArrayList<>(before.history.stream().<Map<String, Integer>>map(HashMap::new).toList());
			record.historyDays = new ArrayList<>(before.historyDays);
			List<Event> events = new ArrayList<>(before.events);
			events.addAll(record.events);
			record.events = events;
			record.newcomer = before.newcomer;
			record.previously = new ArrayList<>(before.previously);
			statsAtOpening.put(entry.getKey(), before.latest);
		}
	}

	/**
	 * The Exchange for an edition: its seed, and for every known player their tier and, for their slay bounties,
	 * how many of each creature they had killed when the week's notices first went up, so only kills since count.
	 *
	 * @param statsAtOpening for a reprint, each player's statistics when the week was first printed
	 */
	private Edition.Exchange exchange(long seed, long closesDay, int periodDays, Map<String, Map<String, Integer>> statsAtOpening) {
		Map<String, Integer> tiers = new HashMap<>();
		for (Map.Entry<String, PlayerRecord> entry : players.entrySet()) {
			tiers.put(entry.getKey(), Bounties.tier(statsAtOpening.getOrDefault(entry.getKey(), entry.getValue().latest)));
		}
		double rewards = Rewards.of(Observer.config().exchangeRewards).factor;
		Edition.Exchange draft = new Edition.Exchange(seed, closesDay, tiers, Map.of(), periodDays, rewards);
		Map<String, Map<String, Integer>> baselines = new HashMap<>();
		for (Map.Entry<String, PlayerRecord> entry : players.entrySet()) {
			for (Bounties.Bounty bounty : Bounties.forPlayer(draft, UUID.fromString(entry.getKey()))) {
				if (bounty.kind() == Bounties.Kind.SLAY) {
					Map<String, Integer> stats = statsAtOpening.getOrDefault(entry.getKey(), entry.getValue().latest);
					baselines.computeIfAbsent(entry.getKey(), key -> new HashMap<>()).put(bounty.statKey(), stats.getOrDefault(bounty.statKey(), 0));
				}
			}
		}
		return new Edition.Exchange(seed, closesDay, tiers, baselines, periodDays, rewards);
	}

	/**
	 * This edition with Exchange notices for {@code uuid}, worked out from their statistics now, if it was printed
	 * before they were known (a new player, or one who joined after it came out). The latest edition keeps them.
	 */
	public Edition withReader(Edition edition, String uuid, Map<String, Integer> stats) {
		Edition.Exchange exchange = edition.exchange();
		if (exchange == Edition.Exchange.NONE || exchange.tiers().containsKey(uuid)) {
			return edition;
		}
		Map<String, Integer> tiers = new HashMap<>(exchange.tiers());
		tiers.put(uuid, Bounties.tier(stats));
		Edition.Exchange draft = new Edition.Exchange(exchange.seed(), exchange.closesDay(), tiers, exchange.baselines(), exchange.days(), exchange.rewards());
		Map<String, Integer> kills = new HashMap<>();
		for (Bounties.Bounty bounty : Bounties.forPlayer(draft, UUID.fromString(uuid))) {
			if (bounty.kind() == Bounties.Kind.SLAY) {
				kills.put(bounty.statKey(), stats.getOrDefault(bounty.statKey(), 0));
			}
		}
		Map<String, Map<String, Integer>> baselines = new HashMap<>(exchange.baselines());
		baselines.put(uuid, kills);
		Edition with = edition.withExchange(new Edition.Exchange(exchange.seed(), exchange.closesDay(), tiers, baselines, exchange.days(), exchange.rewards()));
		if (latest == edition) {
			latest = with;
		}
		return with;
	}

	/** Remembers each story's headline against its player, for the next editions' "previously" facts. */
	public void recordPrinted(Brief brief, Edition edition) {
		for (int i = 0; i < brief.stories().size() && i < edition.stories().size(); i++) {
			String subject = brief.stories().get(i).subject();
			if (subject == null) {
				continue;
			}
			for (PlayerRecord record : players.values()) {
				if (record.name.equals(subject)) {
					record.previously.addFirst("No. " + edition.issue() + ": \"" + edition.stories().get(i).headline() + "\"");
					while (record.previously.size() > 3) {
						record.previously.removeLast();
					}
				}
			}
		}
	}

	/** Statistics gained since {@code baseline}; all of them if there is none. */
	static Map<String, Integer> diff(@Nullable Map<String, Integer> baseline, Map<String, Integer> latest) {
		Map<String, Integer> out = new HashMap<>();
		for (Map.Entry<String, Integer> entry : latest.entrySet()) {
			int before = baseline != null ? baseline.getOrDefault(entry.getKey(), 0) : 0;
			int delta = entry.getValue() - before;
			if (delta > 0) {
				out.put(entry.getKey(), delta);
			}
		}
		return out;
	}

	public PlayerRecord player(String uuid, String name) {
		PlayerRecord record = players.computeIfAbsent(uuid, key -> new PlayerRecord());
		record.name = name;
		return record;
	}

	public @Nullable Edition latestEdition() {
		return latest;
	}

	public void setLatestEdition(Edition edition) {
		latest = edition;
		latestPrinting = printings;
	}

	/** Whether this player already has the copy on the newsstands now. */
	public boolean collected(PlayerRecord record) {
		return latest != null && record.lastPrintingReceived >= latestPrinting;
	}

	public static Ledger load(Path dir) {
		Ledger ledger = new Ledger();
		Path file = dir.resolve(LEDGER_FILE);
		try {
			if (Files.exists(file)) {
				Ledger read = GSON.fromJson(Files.readString(file), Ledger.class);
				if (read != null) {
					ledger = read;
				}
			}
			Path editionFile = dir.resolve(EDITION_FILE);
			if (Files.exists(editionFile)) {
				JsonElement json = JsonParser.parseString(Files.readString(editionFile));
				ledger.latest = Edition.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(Observer.LOGGER::warn).orElse(null);
			}
		} catch (IOException | RuntimeException e) {
			Observer.LOGGER.warn("Could not read the Observer's ledger in {}, starting a fresh one", dir, e);
		}
		if (ledger.players == null) {
			ledger.players = new LinkedHashMap<>();
		}
		if (ledger.lastBoards == null) {
			ledger.lastBoards = new ArrayList<>();
		}
		for (PlayerRecord record : ledger.players.values()) {
			// Ledgers written before these fields existed.
			if (record.history == null) {
				record.history = new ArrayList<>();
			}
			if (record.bountiesDone == null) {
				record.bountiesDone = new ArrayList<>();
			}
			if (record.previously == null) {
				record.previously = new ArrayList<>();
			}
		}
		ledger.dir = dir;
		return ledger;
	}

	public void save() {
		if (dir == null) {
			return;
		}
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(LEDGER_FILE), GSON.toJson(this));
			if (latest != null) {
				JsonElement json = Edition.CODEC.encodeStart(JsonOps.INSTANCE, latest).getOrThrow();
				Files.writeString(dir.resolve(EDITION_FILE), GSON.toJson(json));
			}
		} catch (IOException | RuntimeException e) {
			Observer.LOGGER.warn("Could not save the Observer's ledger in {}", dir, e);
		}
	}
}
