package dev.spacebod.observer.demo;

import dev.spacebod.observer.news.Ledger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * A pretend player: each activity adds the bundle of vanilla statistics that doing it for real leaves behind, so
 * the news desk sees what the game would actually record rather than hand-picked single numbers. Statistics are
 * cumulative, like the game's. Used by the scenario tests and by the demonstration town.
 */
public class Resident {
	private static final int TICKS_PER_HOUR = 72_000;

	public final String name;
	public final UUID uuid;
	public final Map<String, Integer> stats = new HashMap<>();
	public final List<Ledger.Event> events = new ArrayList<>();
	private final LongSupplier day;

	/** @param day the day events happen on, as the world counts it */
	public Resident(String name, LongSupplier day) {
		this.name = name;
		this.uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
		this.day = day;
	}

	public String name() {
		return name;
	}

	public UUID uuid() {
		return uuid;
	}

	public Resident add(String key, int amount) {
		stats.merge(key, amount, Integer::sum);
		return this;
	}

	protected Resident hours(double hours) {
		return add("custom:play_time", (int) (hours * TICKS_PER_HOUR)).add("custom:time_since_rest", (int) (hours * TICKS_PER_HOUR));
	}

	protected Resident walk(double km) {
		return add("custom:walk_one_cm", (int) (km * 70_000)).add("custom:sprint_one_cm", (int) (km * 30_000)).add("custom:jump", (int) (km * 40));
	}

	/** A trip underground: mostly stone and deepslate, the usual ores, torches placed, a pickaxe or two worn out. */
	public Resident miningTrip(int blocks, int diamonds) {
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
	public Resident redstoneBuild(int size) {
		add("placed:redstone", size * 4).add("placed:repeater", size).add("placed:comparator", size / 3)
				.add("placed:piston", size / 2).add("placed:sticky_piston", size / 4).add("placed:observer", size / 3)
				.add("placed:lever", size / 8 + 1).add("placed:redstone_lamp", size / 4).add("placed:hopper", size / 4)
				.add("placed:stone_bricks", size * 3).add("crafted:repeater", size).add("crafted:comparator", size / 3)
				.add("crafted:piston", size / 2).add("crafted:sticky_piston", size / 4).add("crafted:observer", size / 3)
				.add("crafted:redstone_lamp", size / 4).add("crafted:hopper", size / 4).add("mined:redstone_ore", size / 2)
				.add("mined:deepslate_redstone_ore", size / 3).add("custom:interact_with_crafting_table", size / 2);
		return hours(size / 12.0);
	}

	/** Hunting one kind of creature: the kills, the damage both ways, the drops picked up. */
	public Resident monsterHunt(String mob, int kills) {
		add("killed:" + mob, kills).add("custom:mob_kills", kills).add("custom:damage_dealt", kills * 60).add("custom:damage_taken", kills * 12);
		switch (mob) {
			case "spider" -> add("picked_up:string", kills).add("picked_up:spider_eye", kills / 3);
			case "zombie" -> add("picked_up:rotten_flesh", kills);
			case "skeleton" -> add("picked_up:bone", kills).add("picked_up:arrow", kills);
			case "creeper" -> add("picked_up:gunpowder", kills);
			case "blaze" -> add("picked_up:blaze_rod", kills / 2);
			default -> {
			}
		}
		return walk(kills / 20.0).hours(kills / 40.0);
	}

	/** Blowing things up: TNT crafted, placed and lit. */
	public Resident tnt(int blocks) {
		add("crafted:tnt", blocks).add("placed:tnt", blocks).add("used:flint_and_steel", blocks / 2 + 1)
				.add("picked_up:gunpowder", blocks * 5).add("custom:damage_taken", 30);
		return hours(0.5);
	}

	/** A season in the fields: sowing, harvesting, a little baking. */
	public Resident farm(int seeds) {
		add("used:wheat_seeds", seeds * 6 / 10).add("used:carrot", seeds * 2 / 10).add("used:potato", seeds * 2 / 10)
				.add("mined:wheat", seeds / 2).add("mined:carrots", seeds / 6).add("mined:potatoes", seeds / 6)
				.add("crafted:bread", seeds / 12).add("used:iron_hoe", seeds / 3);
		return walk(seeds / 300.0).hours(seeds / 150.0);
	}

	public Resident bake(int loaves) {
		add("crafted:bread", loaves).add("crafted:cake", loaves / 10).add("crafted:pumpkin_pie", loaves / 5);
		return hours(loaves / 60.0);
	}

	/** Travelling; mode is a custom distance statistic such as "boat_one_cm", "aviate_one_cm" or "walk_one_cm". */
	public Resident travel(double km, String mode) {
		add("custom:" + mode, (int) (km * 100_000));
		return hours(km / 15);
	}

	/** A first trip through a portal: obsidian placed, the frame lit, netherrack underfoot. */
	public Resident visitNether() {
		add("placed:obsidian", 10).add("used:flint_and_steel", 1).add("picked_up:netherrack", 16).add("mined:netherrack", 40)
				.add("custom:damage_taken", 60);
		return hours(1);
	}

	public Resident build(String block, int count) {
		add("placed:" + block, count).add("picked_up:" + block, count / 2);
		return hours(count / 300.0);
	}

	public Resident craft(String item, int count) {
		return add("crafted:" + item, count).hours(0.1);
	}

	public Resident trade(int trades) {
		add("custom:traded_with_villager", trades).add("custom:talked_to_villager", trades + 2);
		return hours(trades / 20.0);
	}

	public Resident fish(int fish) {
		add("custom:fish_caught", fish).add("used:fishing_rod", fish * 2);
		return hours(fish / 15.0);
	}

	public Resident enchant(int items) {
		add("custom:enchant_item", items);
		return hours(0.2);
	}

	public Resident breed(int animals) {
		add("custom:animals_bred", animals).add("used:wheat", animals * 2);
		return hours(animals / 30.0);
	}

	public Resident sleep(int nights) {
		return add("custom:sleep_in_bed", nights);
	}

	/** Just being around: a little walking and an hour or so online. */
	public Resident potter(double hours) {
		return walk(hours * 0.5).hours(hours);
	}

	/** Dying, with the game's death message ("was blown up by Creeper", "tried to swim in lava"...). */
	public Resident die(String howTheyDied) {
		return die(howTheyDied, "");
	}

	/** Dying, with the death message's translation key ("death.attack.flyIntoWall"). */
	public Resident die(String howTheyDied, String key) {
		add("custom:deaths", 1);
		events.add(new Ledger.Event("death", name + " " + howTheyDied, "", day.getAsLong(), key));
		return this;
	}

	/** Being killed by a creature ("zombie", "was slain by Zombie"). */
	public Resident killedBy(String mob, String howTheyDied) {
		add("killed_by:" + mob, 1);
		return die(howTheyDied, "death.attack.mob");
	}

	/** Earning an advancement: frame is "task", "goal" or "challenge". */
	public Resident advancement(String title, String description, String frame) {
		return advancement(title, description, frame, "");
	}

	/** Earning an advancement, with its id ("minecraft:end/elytra"). */
	public Resident advancement(String title, String description, String frame, String id) {
		events.add(new Ledger.Event("advancement." + frame, title, description, day.getAsLong(), id));
		return this;
	}
}
