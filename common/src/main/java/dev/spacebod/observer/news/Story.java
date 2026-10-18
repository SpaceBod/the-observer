package dev.spacebod.observer.news;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A story before anyone has written it: what kind it is, who it is about, how newsworthy it is, and its facts
 * twice over - as named slots for the house templates, and as plain sentences for the writing model.
 *
 * @param kind    template key in copy.json ("deaths", "miner", "advancement.goal"...)
 * @param subject the player it is about; null for stories about nobody in particular
 * @param slots   template placeholders: "player" -> "Steve", "count" -> "1,204"...
 * @param facts   the same facts as sentences; the only things the model is allowed to say
 */
public record Story(String kind, String section, @Nullable String subject, double score, Map<String, String> slots,
				   List<String> facts) {
	/**
	 * This story with a connection to the rest of the player's week: one more fact, and more newsworthy for it. The
	 * house copy tells the connection with one of its own sentences of that kind, filled from the link's slots.
	 */
	Story linked(String fact, double bonus, String linkKind, Map<String, String> linkSlots) {
		List<String> more = new ArrayList<>(facts);
		more.add(fact);
		Map<String, String> withLink = new LinkedHashMap<>(slots);
		if (!withLink.containsKey("link")) {
			withLink.put("link", fact);
			withLink.put("linkkind", linkKind);
			linkSlots.forEach((k, v) -> withLink.put("link_" + k, v));
		}
		return new Story(kind, section, subject, score + bonus, Map.copyOf(withLink), List.copyOf(more));
	}

	/** This story without facts another story already tells. */
	Story without(List<String> told) {
		Map<String, String> rest = new LinkedHashMap<>(slots);
		if (told.contains(rest.get("link"))) {
			rest.keySet().removeIf(k -> k.equals("link") || k.equals("linkkind") || k.startsWith("link_"));
		}
		return new Story(kind, section, subject, score, Map.copyOf(rest), facts.stream().filter(f -> !told.contains(f)).toList());
	}

	static final class Builder {
		private final String kind;
		private final String section;
		private final @Nullable String subject;
		private final double score;
		private final Map<String, String> slots = new LinkedHashMap<>();
		private final List<String> facts = new ArrayList<>();

		Builder(String kind, String section, @Nullable String subject, double score) {
			this.kind = kind;
			this.section = section;
			this.subject = subject;
			this.score = score;
		}

		Builder slot(String name, String value) {
			slots.put(name, value);
			return this;
		}

		Builder fact(String sentence) {
			facts.add(sentence);
			return this;
		}

		Story build() {
			return new Story(kind, section, subject, score, Map.copyOf(slots), List.copyOf(facts));
		}
	}
}
