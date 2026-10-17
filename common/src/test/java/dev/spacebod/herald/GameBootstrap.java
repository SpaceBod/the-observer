package dev.spacebod.herald;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Loads vanilla's registries and English text once per test run: the news desk looks up block, item and creature names. */
public final class GameBootstrap {
	private static boolean done;

	private GameBootstrap() {
	}

	public static synchronized void ensure() {
		if (!done) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			done = true;
		}
	}
}
