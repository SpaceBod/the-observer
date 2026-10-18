package dev.spacebod.observer.news;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A player's vanilla statistics flattened to "type:id" keys ("custom:deaths", "mined:diamond_ore",
 * "placed:oak_planks"...), non-zero values only. The paper reports the difference between two snapshots, so most
 * of the news needs no hooks of its own.
 */
public final class StatSnapshot {
	private StatSnapshot() {
	}

	public static Map<String, Integer> capture(ServerPlayer player) {
		ServerStatsCounter stats = player.getStats();
		Map<String, Integer> out = new HashMap<>();
		collect(out, stats, "custom", Stats.CUSTOM, BuiltInRegistries.CUSTOM_STAT, id -> true);
		collect(out, stats, "mined", Stats.BLOCK_MINED, BuiltInRegistries.BLOCK, block -> true);
		collect(out, stats, "killed", Stats.ENTITY_KILLED, BuiltInRegistries.ENTITY_TYPE, type -> true);
		collect(out, stats, "killed_by", Stats.ENTITY_KILLED_BY, BuiltInRegistries.ENTITY_TYPE, type -> true);
		collect(out, stats, "broken", Stats.ITEM_BROKEN, BuiltInRegistries.ITEM, item -> true);
		collect(out, stats, "crafted", Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM, item -> true);
		collect(out, stats, "picked_up", Stats.ITEM_PICKED_UP, BuiltInRegistries.ITEM, item -> true);
		// "used" on a block item counts blocks placed, so it is kept apart from every other use (seeds sown, eyes thrown...).
		collect(out, stats, "placed", Stats.ITEM_USED, BuiltInRegistries.ITEM, (Item item) -> item instanceof BlockItem);
		collect(out, stats, "used", Stats.ITEM_USED, BuiltInRegistries.ITEM, (Item item) -> !(item instanceof BlockItem));
		return out;
	}

	private static <T> void collect(Map<String, Integer> out, ServerStatsCounter stats, String prefix, StatType<T> type,
									Registry<T> registry, Predicate<T> filter) {
		for (T value : registry) {
			if (!filter.test(value)) {
				continue;
			}
			int count = stats.getValue(type, value);
			if (count != 0) {
				out.put(prefix + ":" + shortId(registry.getKey(value)), count);
			}
		}
	}

	/** "minecraft:zombie" -> "zombie"; other namespaces keep theirs. */
	static String shortId(Identifier id) {
		return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
	}

	static Identifier fullId(String shortId) {
		return Identifier.parse(shortId);
	}
}
