package dev.spacebod.herald.news;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.spacebod.herald.Herald;
import dev.spacebod.herald.edition.Edition;
import dev.spacebod.herald.exchange.Bounties;
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
 * The newsroom's notebook for one world, kept in {@code <world>/spacebod_herald/}: who lives here, their
 * statistics as of the last edition, what happened to them since, and the latest edition itself (so late
 * arrivals still get their copy). Plain JSON, so it can be read or reset by hand.
 */
public final class Ledger {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String LEDGER_FILE = "ledger.json";
	private static final String EDITION_FILE = "latest_edition.json";

	/** Number of the last edition printed; 0 before the first. */
	public int issue;
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
		/** Joined since the last edition. */
		public boolean newcomer = true;
		/** This player's statistics changes in each of their last few editions, newest last: what "usual" means for them. */
		public List<Map<String, Integer>> history = new ArrayList<>();
		/** Exchange bounties handed in, as "issue:slot". */
		public List<String> bountiesDone = new ArrayList<>();
	}

	/** Editions of history kept per player. */
	public static final int HISTORY = 8;

	/**
	 * @param kind   "death", "advancement.task", "advancement.goal" or "advancement.challenge"
	 * @param text   the death message, or the advancement's title
	 * @param detail the advancement's description; empty for deaths
	 */
	public record Event(String kind, String text, String detail, long day) {
	}

	/** What closing a period produces: the brief for the press, and the Exchange that goes with it. */
	public record Closing(Brief brief, Edition.Exchange exchange) {
	}

	/**
	 * Closes the period since the last edition: compiles the brief and the Exchange, then moves every player's
	 * baseline on, files the period in their history and clears their events. {@code latest} must already be up
	 * to date for online players.
	 */
	public Closing close(String paper, long day, int periodDays, boolean raining, boolean thundering, int storyCount, long exchangeSeed) {
		List<NewsDesk.Beat> beats = new ArrayList<>();
		for (PlayerRecord record : players.values()) {
			beats.add(new NewsDesk.Beat(record.name, diff(record.baseline, record.latest), record.latest, List.copyOf(record.history),
					List.copyOf(record.events), record.newcomer, record.baseline == null));
		}
		int next = issue + 1;
		Brief brief = NewsDesk.compile(paper, next, day, periodDays, issue == 0, beats, raining, thundering, storyCount, lastBoards);
		lastBoards = brief.boardIds();
		Edition.Exchange exchange = exchange(exchangeSeed, day + periodDays);

		for (PlayerRecord record : players.values()) {
			Map<String, Integer> period = diff(record.baseline, record.latest);
			if (record.baseline != null && !period.isEmpty()) {
				record.history.add(period);
				while (record.history.size() > HISTORY) {
					record.history.removeFirst();
				}
			}
			record.baseline = new HashMap<>(record.latest);
			record.events.clear();
			record.newcomer = false;
		}
		issue = next;
		nextEditionDay = day + periodDays;
		return new Closing(brief, exchange);
	}

	/**
	 * The Exchange for an edition: its seed, and for every known player their tier and, for their slay bounties,
	 * how many of each creature they had killed by now, so only kills from here on count.
	 */
	private Edition.Exchange exchange(long seed, long closesDay) {
		Map<String, Integer> tiers = new HashMap<>();
		for (Map.Entry<String, PlayerRecord> entry : players.entrySet()) {
			tiers.put(entry.getKey(), Bounties.tier(entry.getValue().latest));
		}
		Edition.Exchange draft = new Edition.Exchange(seed, closesDay, tiers, Map.of());
		Map<String, Map<String, Integer>> baselines = new HashMap<>();
		for (Map.Entry<String, PlayerRecord> entry : players.entrySet()) {
			for (Bounties.Bounty bounty : Bounties.forPlayer(draft, UUID.fromString(entry.getKey()))) {
				if (bounty.kind() == Bounties.Kind.SLAY) {
					baselines.computeIfAbsent(entry.getKey(), key -> new HashMap<>())
							.put(bounty.statKey(), entry.getValue().latest.getOrDefault(bounty.statKey(), 0));
				}
			}
		}
		return new Edition.Exchange(seed, closesDay, tiers, baselines);
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
				ledger.latest = Edition.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(Herald.LOGGER::warn).orElse(null);
			}
		} catch (IOException | RuntimeException e) {
			Herald.LOGGER.warn("Could not read the Herald's ledger in {}, starting a fresh one", dir, e);
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
			Herald.LOGGER.warn("Could not save the Herald's ledger in {}", dir, e);
		}
	}
}
