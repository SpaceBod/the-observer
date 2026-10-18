package dev.spacebod.observer.demo;

import dev.spacebod.observer.news.Ledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A demonstration town for trying the paper out (and for screenshots): five residents, each with a few ordinary
 * weeks behind them and an eventful week just gone, written into the ledger so the next edition has plenty to
 * report. Only offered in a development environment.
 */
public final class DemoTown {
	/** Ordinary weeks of history before the one the paper reports. */
	private static final int PAST_WEEKS = 3;

	private DemoTown() {
	}

	private record Character(String name, Consumer<Resident> usualWeek, Consumer<Resident> thisWeek, List<String> previously) {
	}

	private static List<Character> cast() {
		List<Character> cast = new ArrayList<>();
		cast.add(new Character("SpaceBod",
				r -> r.miningTrip(300, 0).trade(12).potter(2),
				r -> r.visitNether().monsterHunt("blaze", 36).add("mined:ancient_debris", 4).add("killed:ghast", 3)
						.advancement("We Need to Go Deeper", "Build, light and enter a Nether Portal", "task", "minecraft:story/enter_the_nether")
						.redstoneBuild(96).craft("observer", 12)
						.trade(150).add("custom:raid_win", 1).add("killed:pillager", 28).add("killed:ravager", 2).add("custom:bell_ring", 6)
						.advancement("Hero of the Village", "Successfully defend a village from a raid", "challenge", "minecraft:adventure/hero_of_the_village"),
				List.of("No. 2: \"SpaceBod Wires Up the Town\"")));
		cast.add(new Character("TofuWarrior",
				r -> r.farm(160).fish(15).monsterHunt("zombie", 20).die("was slain by Zombie"),
				r -> r.farm(1400).breed(64).bake(90).fish(220).travel(8, "boat_one_cm")
						.advancement("A Balanced Diet", "Eat everything that is edible", "challenge", "minecraft:husbandry/balanced_diet")
						.monsterHunt("zombie", 140).monsterHunt("skeleton", 30)
						.killedBy("zombie", "was slain by Zombie").killedBy("zombie", "was slain by Zombie").killedBy("zombie", "was slain by Zombie")
						.die("fell from a high place", "death.fell.accident.generic").die("was blown up by Creeper", "death.attack.explosion.player")
						.die("drowned", "death.attack.drown"),
				List.of("No. 3: \"TofuWarrior Respawns Again\"")));
		cast.add(new Character("CaptainSparklez",
				r -> r.miningTrip(400, 0).potter(1),
				r -> r.miningTrip(3600, 16).advancement("Diamonds!", "Acquire diamonds", "task", "minecraft:story/mine_diamond")
						.die("tried to swim in lava", "death.attack.lava"),
				List.of("No. 3: \"CaptainSparklez Digs Deeper\"")));
		cast.add(new Character("Grian",
				r -> r.build("cobblestone", 300).travel(6, "walk_one_cm").potter(2),
				r -> r.build("cobblestone", 2600).build("stone_bricks", 1900).build("oak_planks", 1100).build("glass", 400)
						.travel(48, "aviate_one_cm").add("used:firework_rocket", 160).add("picked_up:elytra", 1)
						.advancement("Sky's the Limit", "Find Elytra", "goal", "minecraft:end/elytra")
						.die("experienced kinetic energy", "death.attack.flyIntoWall"),
				List.of()));
		cast.add(new Character("Technoblade",
				r -> r.monsterHunt("skeleton", 25).monsterHunt("enderman", 15).potter(1),
				r -> r.monsterHunt("wither_skeleton", 22).add("killed:wither", 1).add("picked_up:nether_star", 1)
						.advancement("Withering Heights", "Summon the Wither", "task", "minecraft:nether/summon_wither")
						.monsterHunt("enderman", 40).add("killed:ender_dragon", 1).add("picked_up:end_stone", 64)
						.advancement("Free the End", "Good luck", "goal", "minecraft:end/kill_dragon"),
				List.of()));
		return cast;
	}

	/** Writes the town into the ledger as if its last edition came out a week before {@code today}. */
	public static void seed(Ledger ledger, long today) {
		long[] day = {Math.max(1, today - 7L * (PAST_WEEKS + 1))};
		for (Character character : cast()) {
			Resident resident = new Resident(character.name(), () -> day[0]);
			Ledger.PlayerRecord record = ledger.player(resident.uuid().toString(), resident.name());
			record.history = new ArrayList<>();
			record.historyDays = new ArrayList<>();
			Map<String, Integer> before = new HashMap<>(resident.stats);
			for (int week = 0; week < PAST_WEEKS; week++) {
				character.usualWeek().accept(resident);
				record.history.add(changes(before, resident.stats));
				record.historyDays.add(7);
				before = new HashMap<>(resident.stats);
			}
			resident.events.clear();
			record.baseline = new HashMap<>(resident.stats);
			day[0] = Math.max(1, today - 3);
			character.thisWeek().accept(resident);
			record.latest = new HashMap<>(resident.stats);
			record.events = new ArrayList<>(resident.events);
			record.previously = new ArrayList<>(character.previously());
			record.newcomer = false;
			record.lastIssueReceived = Math.max(1, ledger.issue);
		}

		// The town has had a few editions already, so this one isn't its founding edition.
		ledger.editions = Math.max(ledger.editions, PAST_WEEKS);
		ledger.issue = Math.max(ledger.issue, PAST_WEEKS);
		ledger.lastPeriod = "demo";
		ledger.reopen = null;
	}

	private static Map<String, Integer> changes(Map<String, Integer> before, Map<String, Integer> after) {
		Map<String, Integer> out = new HashMap<>();
		after.forEach((key, value) -> {
			int change = value - before.getOrDefault(key, 0);
			if (change != 0) {
				out.put(key, change);
			}
		});
		return out;
	}
}
