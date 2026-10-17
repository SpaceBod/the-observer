package dev.spacebod.herald.news;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Version overlay: an advancement's display info, if it is one worth reporting (shown in chat when earned).
 * DisplayInfo became a record in 26.3, so its accessors differ per series.
 */
final class AdvancementNews {
	/** @param frame "task", "goal" or "challenge" */
	record Info(String frame, String title, String description) {
	}

	private AdvancementNews() {
	}

	static @Nullable Info of(AdvancementHolder advancement) {
		DisplayInfo display = advancement.value().display().orElse(null);
		if (display == null || !display.announceToChat()) {
			return null;
		}
		return new Info(display.type().name().toLowerCase(Locale.ROOT), display.title().getString(), display.description().getString());
	}
}
