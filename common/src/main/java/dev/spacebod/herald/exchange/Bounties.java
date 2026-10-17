package dev.spacebod.herald.exchange;

import com.google.gson.Gson;
import dev.spacebod.herald.Herald;
import dev.spacebod.herald.edition.Edition;
import net.minecraft.core.registries.BuiltInRegistries;
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
	private static final String PATH = "/assets/spacebod_herald/herald/exchange.json";
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
						 Identifier reward, int rewardCount, String note) {
		/** "Hattie Fenwick, Farmer of Oakridge"; just the role when there's no town. */
		public String signature() {
			return who + ", " + role + (town.isEmpty() ? "" : " of " + town);
		}

		/** The statistic a slay bounty counts, in the ledger's "killed:zombie" form. */
		public String statKey() {
			return "killed:" + (target.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? target.getPath() : target.toString());
		}
	}

	/** @param stack how many fit in a stack, when that isn't 64 (from the data, so both sides always agree) */
	private record Pay(String item, double value, int stack) {
		int stackSize() {
			return stack > 0 ? stack : 64;
		}
	}

	/** @param per vanilla's range of items (or creatures) per emerald: [base, base with demand] */
	private record Request(String item, String entity, double[] per, int min, int max, int tier, String note) {
	}

	/** A trade: what its people ask for and pay with. Names and towns are drawn per notice ({@link Names}). */
	private record Character(String id, String role, List<String> roles, List<Request> requests, List<Request> bounties, List<Pay> pays) {
	}

	private record Board(double skew, double emeraldShare, int maxRewardStacks, int deliveries, int slayings, List<Character> characters,
						 Character watch) {
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
			out.add(bounty(out.size() + 1, c, c.role(), Kind.DELIVER, Identifier.parse(request.item()), request, random, board));
		}

		// Jobs for the Watch, each a different creature.
		Character watch = board.watch();
		List<Request> jobs = new ArrayList<>(watch.bounties().stream()
				.filter(r -> r.tier() <= tier && BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(r.entity()))).toList());
		for (int i = 0; i < board.slayings() && !jobs.isEmpty(); i++) {
			Request job = jobs.remove(random.nextInt(jobs.size()));
			String role = watch.roles().get(i % watch.roles().size());
			out.add(bounty(out.size() + 1, watch, role, Kind.SLAY, Identifier.parse(job.entity()), job, random, board));
		}
		return out;
	}

	private static Bounty bounty(int slot, Character c, String role, Kind kind, Identifier target, Request request, Random random, Board board) {
		String who = Names.person(random, c.id());
		String town = Names.town(random);
		int count = niceCount(random, request.min(), request.max());
		// Vanilla's rate, drawn towards the generous end of its range: a week's wait should be worth it.
		double rate = request.per()[0] + (request.per()[1] - request.per()[0]) * Math.pow(random.nextDouble(), board.skew());
		double worth = count / rate;
		Identifier reward = Identifier.withDefaultNamespace("emerald");
		int rewardCount = (int) Math.clamp(Math.round(worth), 1, 64L * board.maxRewardStacks());
		if (random.nextDouble() >= board.emeraldShare() && !c.pays().isEmpty()) {
			Pay pay = c.pays().get(random.nextInt(c.pays().size()));
			Optional<Item> item = item(pay.item());
			int amount = (int) Math.round(worth / pay.value());
			// Only when it comes out at roughly the right value and in a sensible number of stacks.
			if (item.isPresent() && amount >= 1 && amount <= pay.stackSize() * board.maxRewardStacks()
					&& amount * pay.value() <= worth * 1.5) {
				reward = Identifier.parse(pay.item());
				rewardCount = amount;
			}
		}
		return new Bounty(slot, who, role, town, kind, target, count, reward, rewardCount, request.note());
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
				Herald.LOGGER.error("Could not load the Exchange", e);
			}
		}
		return board;
	}
}
