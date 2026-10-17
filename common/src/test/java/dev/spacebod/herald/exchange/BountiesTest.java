package dev.spacebod.herald.exchange;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.spacebod.herald.GameBootstrap;
import dev.spacebod.herald.edition.Edition;
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
		exchange = json("/assets/spacebod_herald/herald/exchange.json");
		names = json("/assets/spacebod_herald/herald/names.json");
	}

	private static JsonObject json(String path) {
		return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(BountiesTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8),
				JsonObject.class);
	}

	private static Edition.Exchange exchangeFor(long seed, UUID player, int tier) {
		return new Edition.Exchange(seed, 8, Map.of(player.toString(), tier), Map.of());
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
		Edition.Exchange both = new Edition.Exchange(7, 8, Map.of(a.toString(), 1, b.toString(), 1), Map.of());
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
	void rewardsSitAtTheGenerousEndOfVanillaPrices() {
		Map<String, double[]> per = new HashMap<>();
		for (JsonElement c : exchange.getAsJsonArray("characters")) {
			collectRates(c.getAsJsonObject().getAsJsonArray("requests"), "item", per);
		}
		collectRates(exchange.getAsJsonObject("watch").getAsJsonArray("bounties"), "entity", per);
		for (long seed = 0; seed < 300; seed++) {
			UUID player = new UUID(seed, seed + 99);
			for (Bounties.Bounty b : Bounties.forPlayer(exchangeFor(seed, player, 3), player)) {
				if (!b.reward().getPath().equals("emerald")) {
					continue;
				}
				double[] range = per.get(b.target().toString());
				double most = b.count() / range[0];
				double least = b.count() / range[1];
				assertTrue(b.rewardCount() <= Math.ceil(most) && b.rewardCount() >= Math.max(1, Math.floor(least)), b + " outside " + least + ".." + most);
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

	private static void collectRates(JsonArray requests, String key, Map<String, double[]> per) {
		for (JsonElement r : requests) {
			JsonArray range = r.getAsJsonObject().getAsJsonArray("per");
			double[] rate = {range.get(0).getAsDouble(), range.get(1).getAsDouble()};
			// The same item asked for by two trades at different rates: allow either.
			per.merge(r.getAsJsonObject().get(key).getAsString(), rate, (a, b) -> new double[]{Math.min(a[0], b[0]), Math.max(a[1], b[1])});
		}
	}
}
