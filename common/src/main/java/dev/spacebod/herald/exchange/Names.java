package dev.spacebod.herald.exchange;

import com.google.gson.Gson;
import dev.spacebod.herald.Herald;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The town's population (assets/spacebod_herald/herald/names.json): first names, surnames, towns, and per
 * profession a few pun names drawn only now and then. Every draw takes the caller's {@link Random}, so a seeded
 * caller always meets the same people.
 */
public final class Names {
	private static final String PATH = "/assets/spacebod_herald/herald/names.json";
	private static Pool pool;

	private record Pool(double easterEggChance, List<String> first, List<String> last, List<String> towns,
						Map<String, List<String>> professions) {
	}

	private Names() {
	}

	/** Someone working as {@code profession}: usually an ordinary name, occasionally one of its easter eggs. */
	public static String person(Random random, String profession) {
		Pool p = pool();
		List<String> eggs = p.professions().getOrDefault(profession, List.of());
		// Every draw is made whichever name is used, so each call takes the same share of the caller's sequence.
		double roll = random.nextDouble();
		int egg = random.nextInt(Math.max(1, eggs.size()));
		String ordinary = p.first().get(random.nextInt(p.first().size())) + " " + p.last().get(random.nextInt(p.last().size()));
		return !eggs.isEmpty() && roll < p.easterEggChance() ? eggs.get(egg) : ordinary;
	}

	public static String town(Random random) {
		List<String> towns = pool().towns();
		return towns.get(random.nextInt(towns.size()));
	}

	private static synchronized Pool pool() {
		if (pool == null) {
			try (InputStream in = Names.class.getResourceAsStream(PATH)) {
				if (in == null) {
					throw new IOException("missing " + PATH);
				}
				pool = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Pool.class);
			} catch (IOException | RuntimeException e) {
				Herald.LOGGER.error("Could not load the Herald's names", e);
				pool = new Pool(0, List.of("Pat"), List.of("Smith"), List.of("the town"), Map.of());
			}
		}
		return pool;
	}
}
