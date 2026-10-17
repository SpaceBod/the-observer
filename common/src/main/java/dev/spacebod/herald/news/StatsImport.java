package dev.spacebod.herald.news;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.spacebod.herald.Herald;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Brings every player who has ever played in the world into the ledger, online or not, from the statistics
 * files the game already keeps (one JSON per player UUID). Without this, a server's regulars who haven't logged
 * in since the mod was added would be missing from the Founding Edition, the leaderboards and the Exchange.
 *
 * <p>Names come from the server's name cache; failing that, from the same profile lookup the game uses for
 * player heads, in the background, with a placeholder until it answers (offline-mode players keep it).
 */
final class StatsImport {
	private StatsImport() {
	}

	static void seed(Ledger ledger, MinecraftServer server) {
		Path dir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
		if (!Files.isDirectory(dir)) {
			// Worlds from before the players/ folder keep them at the top level.
			dir = server.getWorldPath(LevelResource.ROOT).resolve("stats");
			if (!Files.isDirectory(dir)) {
				return;
			}
		}
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.filter(f -> f.getFileName().toString().endsWith(".json")).toList();
		} catch (IOException e) {
			Herald.LOGGER.warn("Could not list player statistics in {}", dir, e);
			return;
		}
		int added = 0;
		for (Path file : files) {
			String name = file.getFileName().toString();
			UUID id;
			try {
				id = UUID.fromString(name.substring(0, name.length() - ".json".length()));
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (ledger.players.containsKey(id.toString())) {
				continue;
			}
			Map<String, Integer> stats = read(file);
			if (stats.isEmpty()) {
				continue;
			}
			Ledger.PlayerRecord record = new Ledger.PlayerRecord();
			record.name = server.services().nameToIdCache().get(id).map(NameAndId::name).orElse(placeholder(id));
			record.latest = stats;
			record.newcomer = false;
			ledger.players.put(id.toString(), record);
			added++;
			if (record.name.equals(placeholder(id))) {
				resolveName(server, id, record);
			}
		}
		if (added > 0) {
			Herald.LOGGER.info("Herald: added {} players from the world's statistics", added);
			ledger.save();
		}
	}

	/** The game's statistics file, flattened to the ledger's "type:id" keys (see {@link StatSnapshot}). */
	private static Map<String, Integer> read(Path file) {
		Map<String, Integer> out = new HashMap<>();
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonObject stats = root.getAsJsonObject("stats");
			if (stats == null) {
				return out;
			}
			for (Map.Entry<String, JsonElement> type : stats.entrySet()) {
				String kind = Identifier.parse(type.getKey()).getPath();
				if (kind.equals("dropped") || !type.getValue().isJsonObject()) {
					continue;
				}
				for (Map.Entry<String, JsonElement> entry : type.getValue().getAsJsonObject().entrySet()) {
					int value = entry.getValue().getAsInt();
					if (value == 0) {
						continue;
					}
					Identifier id = Identifier.parse(entry.getKey());
					String prefix = kind;
					if (kind.equals("used")) {
						// As in StatSnapshot: a block item's "used" counts blocks placed.
						prefix = BuiltInRegistries.ITEM.getOptional(id).filter(item -> item instanceof BlockItem).isPresent() ? "placed" : "used";
					}
					out.put(prefix + ":" + StatSnapshot.shortId(id), value);
				}
			}
		} catch (IOException | RuntimeException e) {
			Herald.LOGGER.warn("Could not read player statistics {}", file, e);
		}
		return out;
	}

	private static String placeholder(UUID id) {
		return "Resident " + id.toString().substring(0, 4).toUpperCase(Locale.ROOT);
	}

	private static void resolveName(MinecraftServer server, UUID id, Ledger.PlayerRecord record) {
		CompletableFuture.supplyAsync(() -> server.services().profileResolver().fetchById(id).map(GameProfile::name))
				.thenAccept(found -> found.ifPresent(name -> server.execute(() -> record.name = name)));
	}
}
