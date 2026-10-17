package dev.spacebod.herald.sim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Real-world play patterns, each run as a sequence of weeks ending in the edition under test. Shared by the
 * checks ({@link ScenarioTest}) and the readable gallery ({@link GalleryTest}).
 *
 * <p>Most scenarios start with two ordinary weeks, so the players have a history: firsts need a "before",
 * and new habits and record weeks need a "usual".
 */
public final class Scenarios {
	/** Print each scenario's last edition through the writing model instead of the house templates. */
	static boolean throughPress;

	private Scenarios() {
	}

	private static SimWorld.Result last(SimWorld world) {
		return throughPress ? world.publishWithPress() : world.publish();
	}

	/** The world after the scenario's last edition, and that edition. */
	public record Run(SimWorld world, SimWorld.Result result) {
		public List<SimPlayer> players() {
			return List.copyOf(world.players.values());
		}

		public SimPlayer player(String name) {
			return world.players.get(name);
		}
	}

	public static Map<String, Supplier<Run>> all() {
		Map<String, Supplier<Run>> all = new LinkedHashMap<>();
		all.put("founding-edition", Scenarios::foundingEdition);
		all.put("redstone-engineer", Scenarios::redstoneEngineer);
		all.put("mining-expedition", Scenarios::miningExpedition);
		all.put("monster-hunt", Scenarios::monsterHunt);
		all.put("demolition", Scenarios::demolition);
		all.put("bad-week", Scenarios::badWeek);
		all.put("record-harvest", Scenarios::recordHarvest);
		all.put("nether-debut", Scenarios::netherDebut);
		all.put("quiet-week", Scenarios::quietWeek);
		all.put("busy-server", Scenarios::busyServer);
		return all;
	}

	/** Two ordinary weeks for everyone named, to give them a history. */
	private static SimWorld settled(String... names) {
		SimWorld world = new SimWorld("The Testville Herald");
		for (int week = 0; week < 2; week++) {
			world.week(w -> {
				for (String name : names) {
					w.player(name).miningTrip(180, 0).farm(30).potter(1);
				}
			});
			world.publish();
		}
		return world;
	}

	/** An existing world getting its first paper: everyone's whole life so far. */
	static Run foundingEdition() {
		SimWorld world = new SimWorld("The Testville Herald");
		world.player("Alex").miningTrip(3000, 12).redstoneBuild(40).visitNether().die("was blown up by Creeper").die("fell from a high place");
		world.player("Sam").farm(900).bake(120).trade(60).breed(40);
		world.player("Jo").monsterHunt("zombie", 150).monsterHunt("skeleton", 90).travel(40, "aviate_one_cm");
		return new Run(world, last(world));
	}

	static Run redstoneEngineer() {
		SimWorld world = settled("Alex");
		world.player("Alex").redstoneBuild(64);
		return new Run(world, last(world));
	}

	static Run miningExpedition() {
		SimWorld world = settled("Alex");
		world.player("Alex").miningTrip(1400, 5);
		return new Run(world, last(world));
	}

	static Run monsterHunt() {
		SimWorld world = settled("Alex", "Jo");
		world.player("Alex").monsterHunt("spider", 42).potter(1);
		world.player("Jo").monsterHunt("spider", 25).monsterHunt("zombie", 10);
		return new Run(world, last(world));
	}

	static Run demolition() {
		SimWorld world = settled("Alex");
		world.player("Alex").tnt(24).potter(1);
		return new Run(world, last(world));
	}

	static Run badWeek() {
		SimWorld world = settled("Alex");
		world.player("Alex").potter(3)
				.die("was blown up by Creeper")
				.die("tried to swim in lava")
				.die("fell from a high place")
				.die("was shot by Skeleton")
				.die("drowned");
		return new Run(world, last(world));
	}

	static Run recordHarvest() {
		SimWorld world = settled("Sam");
		world.week(w -> w.player("Sam").farm(40));
		world.publish();
		world.player("Sam").farm(420);
		return new Run(world, last(world));
	}

	static Run netherDebut() {
		SimWorld world = settled("Alex");
		world.player("Alex").visitNether().monsterHunt("magma_cube", 6);
		return new Run(world, last(world));
	}

	static Run quietWeek() {
		SimWorld world = settled("Alex");
		return new Run(world, last(world));
	}

	/** Five regulars, all busy at different things: the paper has to choose. */
	static Run busyServer() {
		SimWorld world = settled("Alex", "Sam", "Jo", "Kit", "Ren");
		world.player("Alex").miningTrip(1600, 3).redstoneBuild(30).die("was blown up by Creeper");
		world.player("Sam").farm(500).bake(90).trade(30);
		world.player("Jo").monsterHunt("zombie", 70).monsterHunt("spider", 30).die("was slain by Zombie");
		world.player("Kit").build("oak_planks", 900).build("glass", 200).travel(12, "boat_one_cm");
		world.player("Ren").fish(45).enchant(6).tnt(10).advancement("Hot Tourist Destinations", "Explore all Nether biomes", "challenge");
		return new Run(world, last(world));
	}
}
