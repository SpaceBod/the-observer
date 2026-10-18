package dev.spacebod.observer.press;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The editor's note for one story: the angle to take, extra facts from the player's dossier that would make it
 * richer, an earlier edition worth recalling, and a townsperson the reporter may quote. Every part is optional.
 *
 * @param support    dossier facts, as text, that are also true this week
 * @param previously what an earlier edition said, if it relates
 * @param quote      a townsperson ("Hattie Fenwick, Farmer of Oakridge") who may be quoted
 */
record Assignment(String angle, List<String> support, @Nullable String previously, @Nullable String quote) {
	static final Assignment NONE = new Assignment("", List.of(), null, null);

	/** Just the name of the person who may be quoted ("Hattie Fenwick"). */
	@Nullable String quoteName() {
		return quote == null ? null : quote.split(",", 2)[0].strip();
	}
}
