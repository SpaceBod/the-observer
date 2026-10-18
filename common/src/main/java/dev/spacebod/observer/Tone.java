package dev.spacebod.observer;

import java.util.Locale;

/** The paper's voice, chosen by the server's operators: it shapes the writing model's style and the house templates. */
public enum Tone {
	BROADSHEET("broadsheet", "The Broadsheet", "Straight news, told with a proud small-town paper's dry wit."),
	ROAST("roast", "The Roast", "No resident is safe. Cheeky, teasing and all in good fun."),
	CHRONICLE("chronicle", "The Chronicle", "Every deed a legend, every resident a hero of the age.");

	public final String id;
	public final String title;
	public final String description;

	Tone(String id, String title, String description) {
		this.id = id;
		this.title = title;
		this.description = description;
	}

	/** The tone with this id, or the broadsheet for anything unknown. */
	public static Tone of(String id) {
		for (Tone tone : values()) {
			if (tone.id.equals(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
				return tone;
			}
		}
		return BROADSHEET;
	}
}
