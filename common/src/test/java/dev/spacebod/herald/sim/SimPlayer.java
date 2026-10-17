package dev.spacebod.herald.sim;

import dev.spacebod.herald.news.Ledger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A simulated player: activities add the bundle of vanilla statistics that doing them for real leaves behind,
 * so the news desk is tested on what the game would actually record, never on hand-picked single numbers.
 * Statistics are cumulative, like the game's; the ledger works out what changed.
 */
public final class SimPlayer {
	private static final int TICKS_PER_HOUR = 72_000;

	final String name;
	final UUID uuid;
	final Map<String, Integer> stats = new HashMap<>();
	final List<Ledger.Event> events = new ArrayList<>();
	private final SimWorld world;

	SimPlayer(SimWorld world, String name) {
		this.world = world;
		this.name = name;
		this.uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
	}

	public String name() {
		return name;
	}

	public UUID uuid() {
		return uuid;
	}

	public SimPlayer add(String key, int amount) {
		stats.merge(key, amount, Integer::sum);
		return this;
	}

	private SimPlayer hours(double hours) {
		return add("custom:play_time", (int) (hours * TICKS_PER_HOUR)).add("custom:time_since_rest", (int) (hours * TICKS_PER_HOUR));
	}

	private SimPlayer walk(double km) {
		return add("custom:walk_one_cm", (int) (km * 70_000)).add("custom:sprint_one_cm", (int) (km * 30_000)).add("custom:jump", (int) (km * 40));
	}

	/** A trip underground: mostly stone and deepslate, the usual ores, torches placed, a pickaxe or two worn out. */
	public SimPlayer miningTrip(int blocks, int diamonds) {
		add("mined:stone", blocks * 55 / 100).add("mined:deepslate", blocks * 25 / 100).add("mined:dirt", blocks / 20)
				.add("mined:gravel", blocks / 25).add("mined:andesite", blocks / 20).add("mined:coal_ore", blocks / 40)
				.add("mined:iron_ore", blocks / 70).add("mined:deepslate_iron_ore", blocks / 90).add("mined:copper_ore", blocks / 80)
				.add("picked_up:cobblestone", blocks * 55 / 100).add("picked_up:cobbled_deepslate", blocks * 25 / 100)
				.add("picked_up:coal", blocks / 40).add("picked_up:raw_iron", blocks / 40).add("placed:torch", blocks / 25)
				.add("crafted:torch", blocks / 25).add("broken:stone_pickaxe", blocks / 250).add("custom:damage_taken", 40);
		if (diamonds > 0) {
			add("mined:deepslate_diamond_ore", diamonds).add("picked_up:diamond", diamonds);
		}
		return walk(blocks / 400.0).hours(blocks / 500.0);
	}

	/** A redstone contraption: wire, repeaters, comparators, pistons and observers, crafted and placed, in a stone shell. */
	public SimPlayer redstoneBuild(int size) {
		add("placed:redstone_wire", size * 4).add("placed:repeater", size).add("placed:comparator", size / 3)
				.add("placed:piston", size / 2).add("placed:sticky_piston", size / 4).add("placed:observer", size / 3)
				.add("placed:lever", size / 8 + 1).add("placed:redstone_lamp", size / 4).add("placed:hopper", size / 4)
				.add("placed:stone_bricks", size * 3).add("crafted:repeater", size).add("crafted:comparator", size / 3)
				.add("crafted:piston", size / 2).add("crafted:sticky_piston", size / 4).add("crafted:observer", size / 3)
				.add("crafted:redstone_lamp", size / 4).add("crafted:hopper", size / 4).add("mined:redstone_ore", size / 2)
				.add("mined:deepslate_redstone_ore", size / 3).add("custom:interact_with_crafting_table", size / 2);
		return hours(size / 12.0);
	}

	/** Hunting one kind of creature: the kills, the damage both ways, the drops picked up. */
	public SimPlayer monsterHunt(String mob, int kills) {
		add("killed:" + mob, kills).add("custom:mob_kills", kills).add("custom:damage_dealt", kills * 60).add("custom:damage_taken", kills * 12);
		switch (mob) {
			case "spider" -> add("picked_up:string", kills).add("picked_up:spider_eye", kills / 3);
			case "zombie" -> add("picked_up:rotten_flesh", kills);
			case "skeleton" -> add("picked_up:bone", kills).add("picked_up:arrow", kills);
			case "creeper" -> add("picked_up:gunpowder", kills);
			default -> {
			}
		}
		return walk(kills / 20.0).hours(kills / 40.0);
	}

	/** Blowing things up: TNT crafted, placed and lit. */
	public SimPlayer tnt(int blocks) {
		add("crafted:tnt", blocks).add("placed:tnt", blocks).add("used:flint_and_steel", blocks / 2 + 1)
				.add("picked_up:gunpowder", blocks * 5).add("custom:damage_taken", 30);
		return hours(0.5);
	}

	/** A season in the fields: sowing, harvesting, a little baking. */
	public SimPlayer farm(int seeds) {
		add("used:wheat_seeds", seeds * 6 / 10).add("used:carrot", seeds * 2 / 10).add("used:potato", seeds * 2 / 10)
				.add("mined:wheat", seeds / 2).add("mined:carrots", seeds / 6).add("mined:potatoes", seeds / 6)
				.add("crafted:bread", seeds / 12).add("used:iron_hoe", seeds / 3);
		return walk(seeds / 300.0).hours(seeds / 150.0);
	}

	public SimPlayer bake(int loaves) {
		add("crafted:bread", loaves).add("crafted:cake", loaves / 10).add("crafted:pumpkin_pie", loaves / 5);
		return hours(loaves / 60.0);
	}

	/** Travelling; mode is a custom distance statistic such as "boat_one_cm", "aviate_one_cm" or "walk_one_cm". */
	public SimPlayer travel(double km, String mode) {
		add("custom:" + mode, (int) (km * 100_000));
		return hours(km / 15);
	}

	/** A first trip through a portal: obsidian placed, the frame lit, netherrack underfoot. */
	public SimPlayer visitNether() {
		add("placed:obsidian", 10).add("used:flint_and_steel", 1).add("picked_up:netherrack", 16).add("mined:netherrack", 40)
				.add("custom:damage_taken", 60);
		return hours(1);
	}

	public SimPlayer build(String block, int count) {
		add("placed:" + block, count).add("picked_up:" + block, count / 2);
		return hours(count / 300.0);
	}

	public SimPlayer craft(String item, int count) {
		return add("crafted:" + item, count).hours(0.1);
	}

	public SimPlayer trade(int trades) {
		add("custom:traded_with_villager", trades).add("custom:talked_to_villager", trades + 2);
		return hours(trades / 20.0);
	}

	public SimPlayer fish(int fish) {
		add("custom:fish_caught", fish).add("used:fishing_rod", fish * 2);
		return hours(fish / 15.0);
	}

	public SimPlayer enchant(int items) {
		add("custom:enchant_item", items);
		return hours(0.2);
	}

	public SimPlayer breed(int animals) {
		add("custom:animals_bred", animals).add("used:wheat", animals * 2);
		return hours(animals / 30.0);
	}

	public SimPlayer sleep(int nights) {
		return add("custom:sleep_in_bed", nights);
	}

	/** Just being around: a little walking and an hour or so online. */
	public SimPlayer potter(double hours) {
		return walk(hours * 0.5).hours(hours);
	}

	/** Dying, with the game's death message ("was blown up by Creeper", "tried to swim in lava"...). */
	public SimPlayer die(String howTheyDied) {
		add("custom:deaths", 1);
		events.add(new Ledger.Event("death", name + " " + howTheyDied, "", world.day));
		return this;
	}

	/** Earning an advancement: frame is "task", "goal" or "challenge". */
	public SimPlayer advancement(String title, String description, String frame) {
		events.add(new Ledger.Event("advancement." + frame, title, description, world.day));
		return this;
	}
}
