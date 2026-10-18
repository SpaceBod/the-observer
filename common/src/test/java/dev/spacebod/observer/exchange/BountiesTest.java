package dev.spacebod.observer.exchange;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.spacebod.observer.GameBootstrap;
import dev.spacebod.observer.edition.Edition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Exchange: the same notices on client and server, suited to the player, fairly priced, sensibly named. */
class BountiesTest {
	private static JsonObject exchange;
	private static JsonObject names;

	@BeforeAll
	static void load() {
		GameBootstrap.ensure();
		exchange = json("/assets/spacebod_observer/observer/exchange.json");
		names = json("/assets/spacebod_observer/observer/names.json");
	}

	private static JsonObject json(String path) {
		return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(BountiesTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8),
				JsonObject.class);
	}

	private static Edition.Exchange exchangeFor(long seed, UUID player, int tier) {
		return new Edition.Exchange(seed, 8, Map.of(player.toString(), tier), Map.of(), 7, 1.0);
	}

	@Test
	void sameSeedAndPlayerGiveTheSameNotices() {
		UUID player = UUID.randomUUID();
		assertEquals(Bounties.forPlayer(exchangeFor(7, player, 1), player), Bounties.forPlayer(exchangeFor(7, player, 1), player));
	}

	@Test
	void differentPlayersOrEditionsGetDifferentNotices() {
		UUID a = UUID.nameUUIDFromBytes("a".getBytes());
		UUID b = UUID.nameUUIDFromBytes("b".getBytes());
		Edition.Exchange both = new Edition.Exchange(7, 8, Map.of(a.toString(), 1, b.toString(), 1), Map.of(), 7, 1.0);
		assertNotEquals(Bounties.forPlayer(both, a), Bounties.forPlayer(both, b));
		assertNotEquals(Bounties.forPlayer(exchangeFor(7, a, 1), a), Bounties.forPlayer(exchangeFor(8, a, 1), a));
	}

	@Test
	void unknownPlayersGetNoNotices() {
		assertTrue(Bounties.forPlayer(exchangeFor(7, UUID.randomUUID(), 1), UUID.randomUUID()).isEmpty());
	}

	@Test
	void fourDeliveriesFromDifferentTradesAndTwoWatchJobs() {
		for (long seed = 0; seed < 200; seed++) {
			UUID player = new UUID(seed, ~seed);
			List<Bounties.Bounty> notices = Bounties.forPlayer(exchangeFor(seed, player, 3), player);
			assertEquals(Bounties.SLOTS, notices.size());
			List<Bounties.Bounty> deliveries = notices.stream().filter(n -> n.kind() == Bounties.Kind.DELIVER).toList();
			List<Bounties.Bounty> slayings = notices.stream().filter(n -> n.kind() == Bounties.Kind.SLAY).toList();
			assertEquals(4, deliveries.size());
			assertEquals(2, slayings.size());
			assertEquals(4, deliveries.stream().map(Bounties.Bounty::role).distinct().count(), notices.toString());
			assertEquals(2, slayings.stream().map(Bounties.Bounty::target).distinct().count(), notices.toString());
			for (int i = 0; i < notices.size(); i++) {
				assertEquals(i + 1, notices.get(i).slot());
			}
		}
	}

	@Test
	void newcomersAreNotSentToTheNether() {
		Map<String, Integer> tierOf = tiers();
		for (long seed = 0; seed < 300; seed++) {
			UUID player = new UUID(seed, seed * 31);
			for (Bounties.Bounty b : Bounties.forPlayer(exchangeFor(seed, player, 0), player)) {
				assertEquals(0, tierOf.get(b.target().toString()), b.toString());
			}
		}
	}

	@Test
	void rewardsPayMoreThanTheJobIsWorth() {
		Map<String, Double> value = new HashMap<>();
		for (JsonElement c : exchange.getAsJsonArray("characters")) {
			collectValues(c.getAsJsonObject().getAsJsonArray("requests"), "item", value);
		}
		collectValues(exchange.getAsJsonObject("watch").getAsJsonArray("bounties"), "entity", value);
		Map<String, Double> prizeValue = prizeValues();
		double premiumLow = exchange.getAsJsonArray("premium").get(0).getAsDouble();
		double premiumHigh = exchange.getAsJsonArray("premium").get(1).getAsDouble();
		int prizes = 0;
		for (long seed = 0; seed < 400; seed++) {
			UUID player = new UUID(seed, seed + 99);
			for (Bounties.Bounty b : Bounties.forPlayer(exchangeFor(seed, player, 3), player)) {
				double worth = b.count() * value.get(b.target().toString());
				double paid = b.reward().getPath().equals("emerald")
						? b.rewardCount()
						: b.rewardCount() * prizeValue.get(b.reward() + "|" + b.rewardEnchantment() + "|" + b.rewardLevel());
				if (!b.reward().getPath().equals("emerald")) {
					prizes++;
				}
				// A prize may land 20% under or 60% over the premium; emeralds are rounded to whole ones.
				assertTrue(paid >= Math.min(worth * premiumLow * 0.8, Math.floor(worth * premiumLow)) && paid >= 1, b + " pays " + paid + " for " + worth);
				assertTrue(paid <= Math.max(worth * premiumHigh * 1.6, 1), b + " pays " + paid + " for " + worth);
			}
		}
		assertTrue(prizes > 400, "prizes: " + prizes);
	}

	@Test
	void newcomersAreNotPaidInLaterGoods() {
		Map<String, Integer> prizeTier = new HashMap<>();
		List<JsonElement> all = new java.util.ArrayList<>();
		exchange.getAsJsonArray("commonPrizes").forEach(all::add);
		exchange.getAsJsonArray("characters").forEach(c -> c.getAsJsonObject().getAsJsonArray("prizes").forEach(all::add));
		exchange.getAsJsonObject("watch").getAsJsonArray("prizes").forEach(all::add);
		for (JsonElement prize : all) {
			JsonObject p = prize.getAsJsonObject();
			prizeTier.merge(p.get("item").getAsString() + "|" + (p.has("enchant") ? p.get("enchant").getAsString() : "") + "|"
					+ (p.has("level") ? p.get("level").getAsInt() : 0), p.get("tier").getAsInt(), Math::min);
		}
		for (long seed = 0; seed < 300; seed++) {
			UUID player = new UUID(seed, seed * 17);
			for (Bounties.Bounty b : Bounties.forPlayer(exchangeFor(seed, player, 0), player)) {
				if (!b.reward().getPath().equals("emerald")) {
					assertEquals(0, prizeTier.get(b.reward() + "|" + b.rewardEnchantment() + "|" + b.rewardLevel()), b.toString());
				}
			}
		}
	}

	@Test
	void easterEggNamesOnlyAppearInTheirOwnTrade() {
		Map<String, String> eggOwner = new HashMap<>();
		for (Map.Entry<String, JsonElement> profession : names.getAsJsonObject("professions").entrySet()) {
			for (JsonElement egg : profession.getValue().getAsJsonArray()) {
				eggOwner.put(egg.getAsString(), profession.getKey());
			}
		}
		Map<String, String> roleToProfession = new HashMap<>();
		for (JsonElement c : exchange.getAsJsonArray("characters")) {
			roleToProfession.put(c.getAsJsonObject().get("role").getAsString(), c.getAsJsonObject().get("id").getAsString());
		}
		for (JsonElement role : exchange.getAsJsonObject("watch").getAsJsonArray("roles")) {
			roleToProfession.put(role.getAsString(), "watch");
		}
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 2000; seed++) {
			UUID player = new UUID(seed * 7, seed);
			for (Bounties.Bounty b : Bounties.forPlayer(exchangeFor(seed, player, 3), player)) {
				String owner = eggOwner.get(b.who());
				if (owner != null) {
					seen.add(b.who());
					assertEquals(owner, roleToProfession.get(b.role()), b.who() + " working as " + b.role());
				}
			}
		}
		assertTrue(seen.size() > 10, "easter eggs seen: " + seen);
	}

	@Test
	void tiersFollowProgress() {
		assertEquals(0, Bounties.tier(Map.of("mined:stone", 100)));
		assertEquals(1, Bounties.tier(Map.of("crafted:iron_ingot", 3)));
		assertEquals(2, Bounties.tier(Map.of("picked_up:netherrack", 1)));
		assertEquals(3, Bounties.tier(Map.of("picked_up:end_stone", 1, "picked_up:netherrack", 1)));
	}

	private static Map<String, Integer> tiers() {
		Map<String, Integer> out = new HashMap<>();
		for (JsonElement c : exchange.getAsJsonArray("characters")) {
			for (JsonElement r : c.getAsJsonObject().getAsJsonArray("requests")) {
				out.merge(r.getAsJsonObject().get("item").getAsString(), r.getAsJsonObject().get("tier").getAsInt(), Math::min);
			}
		}
		for (JsonElement r : exchange.getAsJsonObject("watch").getAsJsonArray("bounties")) {
			out.put(r.getAsJsonObject().get("entity").getAsString(), r.getAsJsonObject().get("tier").getAsInt());
		}
		return out;
	}

	private static void collectValues(JsonArray requests, String key, Map<String, Double> value) {
		for (JsonElement r : requests) {
			// The same item asked for by two trades: their values agree closely enough for these checks.
			value.merge(r.getAsJsonObject().get(key).getAsString(), r.getAsJsonObject().get("value").getAsDouble(), Math::max);
		}
	}

	private static Map<String, Double> prizeValues() {
		Map<String, Double> out = new HashMap<>();
		List<JsonElement> all = new java.util.ArrayList<>();
		exchange.getAsJsonArray("commonPrizes").forEach(all::add);
		exchange.getAsJsonArray("characters").forEach(c -> c.getAsJsonObject().getAsJsonArray("prizes").forEach(all::add));
		exchange.getAsJsonObject("watch").getAsJsonArray("prizes").forEach(all::add);
		for (JsonElement prize : all) {
			JsonObject p = prize.getAsJsonObject();
			out.put(p.get("item").getAsString() + "|" + (p.has("enchant") ? p.get("enchant").getAsString() : "") + "|"
					+ (p.has("level") ? p.get("level").getAsInt() : 0), p.get("value").getAsDouble());
		}
		return out;
	}

	@Test
	void playersStartingOutAlwaysHaveANoticePayingEmeralds() {
		for (int tier = 0; tier <= 1; tier++) {
			for (long seed = 0; seed < 50; seed++) {
				UUID player = UUID.nameUUIDFromBytes(("p" + seed).getBytes());
				List<Bounties.Bounty> bounties = Bounties.forPlayer(exchangeFor(seed, player, tier), player);
				assertTrue(bounties.stream().anyMatch(b -> b.reward().getPath().equals("emerald")), "tier " + tier + ", seed " + seed);
			}
		}
	}
}
