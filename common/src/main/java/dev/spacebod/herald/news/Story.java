package dev.spacebod.herald.news;

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
