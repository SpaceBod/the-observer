package dev.spacebod.observer;

import java.util.Locale;

/** How generous the Exchange is, set by operators; each edition's notices keep the level they were printed with. */
public enum Rewards {
	LOW("low", "Low", 0.6),
	BALANCED("balanced", "Balanced", 1.0),
	HIGH("high", "High", 1.6);

	public final String id;
	public final String title;
	/** Multiplies every reward's worth. */
	public final double factor;

	Rewards(String id, String title, double factor) {
		this.id = id;
		this.title = title;
		this.factor = factor;
	}

	public static Rewards of(String id) {
		for (Rewards rewards : values()) {
			if (rewards.id.equals(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
				return rewards;
			}
		}
		return BALANCED;
	}
}
