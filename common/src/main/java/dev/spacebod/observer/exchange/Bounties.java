package dev.spacebod.observer.exchange;

import com.google.gson.Gson;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.edition.Edition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The Exchange's bounties, worked out rather than stored: the same edition seed and player UUID give the same
 * bounties on the server (to check a hand-in) and on that player's client (to show the page). Rates and
 * requests come from exchange.json.
 *
 * <p>Only {@link java.util.Random} (whose sequence is fixed by the Java spec), the data file's own order and
 * registry lookups go into it, so client and server can't disagree.
 */
public final class Bounties {
	private static final String PATH = "/assets/spacebod_observer/observer/exchange.json";
	public static final int SLOTS = 6;
	private static Board board;

	public enum Kind {
		DELIVER, SLAY
	}

	/**
	 * @param slot   1-based position on the page
	 * @param target an item to deliver or a creature to slay
	 */
	public record Bounty(int slot, String who, String role, String town, Kind kind, Identifier target, int count,
						 Identifier reward, int rewardCount, String rewardEnchantment, int rewardLevel, String note) {
		/** "Hattie Fenwick, Farmer of Oakridge"; just the role when there's no town. */
		public String signature() {
			return who + ", " + role + (town.isEmpty() ? "" : " of " + town);
		}

		/** The statistic a slay bounty counts, in the ledger's "killed:zombie" form. */
		public String statKey() {
			return "killed:" + (target.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? target.getPath() : target.toString());
		}
	}

	/** @param value emeralds per item (or creature) */
	private record Request(String item, String entity, double value, int min, int max, int tier, String note) {
	}

	/**
	 * Something a character pays with.
	 *
	 * @param value   emeralds each
	 * @param max     the most given at once (1 for books, tools and armour)
	 * @param enchant an enchantment to put on it (a book's stored one), or null
	 */
	private record Prize(String item, double value, int max, String enchant, int level, int tier) {
	}

	/** A trade: what its people ask for and pay with. Names and towns are drawn per notice ({@link Names}). */
	private record Character(String id, String role, List<String> roles, List<Request> requests, List<Request> bounties, List<Prize> prizes) {
	}

	/** @param premium a reward is worth the job times a factor drawn from this range */
	private record Board(double[] premium, double emeraldShare, int maxEmeralds, int deliveries, int slayings, List<Prize> commonPrizes,
						 List<Character> characters, Character watch) {
	}

	private Bounties() {
	}

	/** This player's bounties in this edition; empty if they weren't known when it was printed. */
	public static List<Bounty> forPlayer(Edition.Exchange exchange, UUID player) {
		Integer tier = exchange.tiers().get(player.toString());
		Board board = board();
		if (tier == null || board == null) {
			return List.of();
		}
		Random random = new Random(exchange.seed() ^ (player.getMostSignificantBits() * 0x9E3779B97F4A7C15L) ^ player.getLeastSignificantBits());
		List<Bounty> out = new ArrayList<>();

		List<Character> open = new ArrayList<>();
		for (Character c : board.characters()) {
			if (c.requests().stream().anyMatch(r -> r.tier() <= tier && item(r.item()).isPresent())) {
				open.add(c);
			}
		}
		// Deliveries, each from a different trade.
		for (int i = 0; i < board.deliveries() && !open.isEmpty(); i++) {
			Character c = open.remove(random.nextInt(open.size()));
			List<Request> fits = c.requests().stream().filter(r -> r.tier() <= tier && item(r.item()).isPresent()).toList();
			Request request = fits.get(random.nextInt(fits.size()));
			// A player starting out always has one notice paying emeralds: the price of next week's paper.
			boolean emeralds = i == 0 && tier <= 1;
			out.add(bounty(out.size() + 1, c, c.role(), Kind.DELIVER, Identifier.parse(request.item()), request, tier, random, board, exchange, emeralds));
		}

		// Jobs for the Watch, each a different creature.
		Character watch = board.watch();
		List<Request> jobs = new ArrayList<>(watch.bounties().stream()
				.filter(r -> r.tier() <= tier && BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(r.entity()))).toList());
		for (int i = 0; i < board.slayings() && !jobs.isEmpty(); i++) {
			Request job = jobs.remove(random.nextInt(jobs.size()));
			String role = watch.roles().get(i % watch.roles().size());
			out.add(bounty(out.size() + 1, watch, role, Kind.SLAY, Identifier.parse(job.entity()), job, tier, random, board, exchange, false));
		}
		return out;
	}

	private static Bounty bounty(int slot, Character c, String role, Kind kind, Identifier target, Request request, int tier, Random random,
								 Board board, Edition.Exchange exchange, boolean payInEmeralds) {
		String who = Names.person(random, c.id());
		String town = Names.town(random);
		// The asks are sized for a week; a daily paper's are smaller, a fortnightly one's bigger.
		double size = Math.clamp(exchange.days() / 7.0, 0.25, 2.0);
		int min = Math.max(1, (int) Math.round(request.min() * size));
		int count = niceCount(random, min, Math.max(min, (int) Math.round(request.max() * size)));
		// Generous on purpose: a week's work should pay better than the villagers do.
		double worth = count * request.value() * (board.premium()[0] + random.nextDouble() * (board.premium()[1] - board.premium()[0])) * exchange.rewards();

		// A prize that comes out near the target, or emeralds.
		List<Prize> prizes = new ArrayList<>(c.prizes());
		prizes.addAll(board.commonPrizes());
		List<Prize> fits = new ArrayList<>();
		for (Prize prize : prizes) {
			if (prize.tier() <= tier && item(prize.item()).isPresent()) {
				double given = prizeCount(prize, worth) * prize.value();
				if (given >= worth * 0.8 && given <= worth * 1.6) {
					fits.add(prize);
				}
			}
		}
		boolean emeralds = random.nextDouble() < board.emeraldShare() || fits.isEmpty() || payInEmeralds;
		Prize prize = fits.isEmpty() ? null : fits.get(random.nextInt(fits.size()));
		if (emeralds || prize == null) {
			int amount = (int) Math.clamp(Math.round(worth), 1, board.maxEmeralds());
			return new Bounty(slot, who, role, town, kind, target, count, Identifier.withDefaultNamespace("emerald"), amount, "", 0, request.note());
		}
		return new Bounty(slot, who, role, town, kind, target, count, Identifier.parse(prize.item()), prizeCount(prize, worth),
				prize.enchant() == null ? "" : prize.enchant(), prize.level(), request.note());
	}

	private static int prizeCount(Prize prize, double worth) {
		int most = prize.max() > 0 ? prize.max() : 64;
		return (int) Math.clamp(Math.round(worth / prize.value()), 1, most);
	}

	/** A count a person would actually ask for: multiples of 16, 8 or 4 once it's large. */
	private static int niceCount(Random random, int min, int max) {
		int raw = min + random.nextInt(Math.max(1, max - min + 1));
		int step = max >= 128 ? 16 : max >= 32 ? 8 : max >= 12 ? 4 : 1;
		return Math.clamp(Math.round(raw / (float) step) * step, min, max);
	}

	/** How far along a player is, from their statistics: 0 starting out, 1 has iron, 2 has been to the Nether, 3 the End. */
	public static int tier(Map<String, Integer> stats) {
		if (stats.getOrDefault("picked_up:end_stone", 0) > 0 || stats.getOrDefault("mined:end_stone", 0) > 0) {
			return 3;
		}
		if (stats.getOrDefault("picked_up:netherrack", 0) > 0 || stats.getOrDefault("mined:netherrack", 0) > 0) {
			return 2;
		}
		if (stats.getOrDefault("crafted:iron_ingot", 0) > 0 || stats.getOrDefault("picked_up:iron_ingot", 0) > 0
				|| stats.getOrDefault("picked_up:raw_iron", 0) > 0) {
			return 1;
		}
		return 0;
	}

	/** Good enough English plurals for item and creature names ("Emeralds", "Endermen", "Witches"; "Bricks" stays). */
	public static String plural(String name, int count) {
		if (count == 1) {
			return name;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith("man")) {
			return name.substring(0, name.length() - 3) + "men";
		}
		if (lower.endsWith("s") || lower.endsWith("fish") || lower.endsWith("sheep") || lower.endsWith("wheat") || lower.endsWith("flesh")
				|| lower.endsWith("coal") || lower.endsWith("stone") || lower.endsWith("dust") || lower.endsWith("wool") || lower.endsWith("glass")) {
			return name;
		}
		if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("x")) {
			return name + "es";
		}
		if (lower.endsWith("y") && !lower.endsWith("ey") && !lower.endsWith("ay")) {
			return name.substring(0, name.length() - 1) + "ies";
		}
		return name + "s";
	}

	/** {@code count} townsfolk of different trades, as "Name, Role of Town", for the paper to quote. */
	public static List<String> cast(long seed, int count) {
		Board board = board();
		List<String> out = new ArrayList<>();
		if (board == null) {
			return out;
		}
		Random random = new Random(seed);
		List<Character> trades = new ArrayList<>(board.characters());
		for (int i = 0; i < count && !trades.isEmpty(); i++) {
			Character c = trades.remove(random.nextInt(trades.size()));
			out.add(Names.person(random, c.id()) + ", " + c.role() + " of " + Names.town(random));
		}
		return out;
	}

	/** The reward as people say it: "6 Emeralds", "Diamond Chestplate", "Book of Fortune III". */
	public static String rewardText(Bounty bounty) {
		if (!bounty.rewardEnchantment().isEmpty()) {
			Identifier id = Identifier.parse(bounty.rewardEnchantment());
			String name = Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath()).getString();
			String level = bounty.rewardLevel() > 1 ? " " + Component.translatable("enchantment.level." + bounty.rewardLevel()).getString() : "";
			return "Book of " + name + level;
		}
		String name = item(bounty.reward().toString()).map(item -> Component.translatable(item.getDescriptionId()).getString())
				.orElse(bounty.reward().getPath());
		return bounty.rewardCount() == 1 ? name : bounty.rewardCount() + " " + plural(name, bounty.rewardCount());
	}

	private static Optional<Item> item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
	}

	private static synchronized Board board() {
		if (board == null) {
			try (InputStream in = Bounties.class.getResourceAsStream(PATH)) {
				if (in == null) {
					throw new IOException("missing " + PATH);
				}
				board = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Board.class);
			} catch (IOException | RuntimeException e) {
				Observer.LOGGER.error("Could not load the Exchange", e);
			}
		}
		return board;
	}
}
