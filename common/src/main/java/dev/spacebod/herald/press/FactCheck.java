package dev.spacebod.herald.press;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.spacebod.herald.news.Story;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sub-editor: every reply the model writes must pass structure, sanitising, form, policy, grounding and
 * copying checks, in that order, before it can be printed. The grammar already forces the structure; it is
 * checked again so a hand-edited profile can't print garbage. The first failed check names the reason, which
 * the press counts for {@code /herald status}.
 */
final class FactCheck {
	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]{2,})\"");
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_']+");

	private FactCheck() {
	}

	record Draft(String headline, String story) {
	}

	record Verdict(@Nullable Draft draft, String reason) {
		static Verdict pass(Draft draft) {
			return new Verdict(draft, "ok");
		}

		static Verdict fail(String reason) {
			return new Verdict(null, reason);
		}
	}

	/**
	 * @param players   everyone in this edition
	 * @param copyGuard word n-grams from the system prompt and examples, see {@link #ngrams}
	 */
	static Verdict check(String reply, Story story, List<String> players, PressProfile profile, Set<String> copyGuard) {
		PressProfile.Checks checks = profile.checks;

		// 1. Structure
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(reply.strip());
			if (!parsed.isJsonObject()) {
				return Verdict.fail("not a JSON object");
			}
			json = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			return Verdict.fail("not JSON");
		}
		String schemaProblem = schemaProblem(json, profile.schema);
		if (schemaProblem != null) {
			return Verdict.fail("schema: " + schemaProblem);
		}

		// 2. Sanitising
		String headline = Sanitizer.headline(json.get("headline").getAsString());
		String body = Sanitizer.clean(json.get("story").getAsString());
		String text = (headline + "\n" + body).toLowerCase(Locale.ROOT);

		// 3. Form
		List<String> words = words(body);
		if (words.size() < checks.minWords || words.size() > checks.maxWords) {
			return Verdict.fail("length");
		}
		if (!body.matches("(?s).*[.!?][\"']?$")) {
			return Verdict.fail("unfinished sentence");
		}
		if (checks.englishOnly && latinShare(text) < 0.95) {
			return Verdict.fail("not English");
		}
		if (repeatedTrigramShare(words) > checks.maxRepeatedTrigramShare) {
			return Verdict.fail("repetition");
		}

		// 4. Policy
		for (String phrase : checks.bannedPhrases) {
			if (containsWords(text, phrase.toLowerCase(Locale.ROOT))) {
				return Verdict.fail("banned phrase");
			}
		}
		for (String word : checks.forbiddenWords) {
			if (containsWords(text, word.toLowerCase(Locale.ROOT))) {
				return Verdict.fail("forbidden word '" + word + "'");
			}
		}

		// 5. Grounding
		String facts = (String.join(" ", story.facts()) + " " + (story.subject() != null ? story.subject() : "")).toLowerCase(Locale.ROOT);
		if (checks.subjectNamed && story.subject() != null) {
			String subject = story.subject().toLowerCase(Locale.ROOT);
			if (!containsWords(text, subject)) {
				return Verdict.fail("subject not named");
			}
			// "Lucas" for "Luca": a misspelt name is a wrong name.
			if (Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(subject) + "[\\p{L}\\p{N}_]").matcher(text).find()) {
				return Verdict.fail("subject misspelt");
			}
		}
		if (checks.noOtherPlayers) {
			for (String player : players) {
				String name = player.toLowerCase(Locale.ROOT);
				if (!facts.contains(name) && containsWords(text, name)) {
					return Verdict.fail("other player named");
				}
			}
		}
		if (checks.numbersFromFacts && !numbersGrounded(text, facts)) {
			return Verdict.fail("number not in facts");
		}
		for (String word : checks.groundedWords) {
			String w = word.toLowerCase(Locale.ROOT);
			if (containsWords(text, w) && !containsWords(facts, w)) {
				return Verdict.fail("ungrounded '" + word + "'");
			}
		}
		if (checks.noInventedQuotes) {
			Matcher quoted = QUOTED.matcher(text);
			while (quoted.find()) {
				if (!facts.contains(quoted.group(1).strip())) {
					return Verdict.fail("invented quote");
				}
			}
		}

		// 6. Copying
		if (checks.copyGuardWords > 0) {
			Set<String> mine = ngrams(text, checks.copyGuardWords);
			// Runs that are themselves facts (a player's name, an advancement title) don't count as copying.
			mine.removeAll(ngrams(facts, checks.copyGuardWords));
			for (String gram : mine) {
				if (copyGuard.contains(gram)) {
					return Verdict.fail("copied from prompt");
				}
			}
		}
		return Verdict.pass(new Draft(headline, body));
	}

	/** Validates the subset of JSON Schema the profile uses: type, required, properties, additionalProperties, min/maxLength. */
	static @Nullable String schemaProblem(JsonObject value, JsonObject schema) {
		JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
		if (schema.has("required")) {
			for (JsonElement name : schema.getAsJsonArray("required")) {
				if (!value.has(name.getAsString())) {
					return "missing " + name.getAsString();
				}
			}
		}
		if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean()) {
			for (String key : value.keySet()) {
				if (!properties.has(key)) {
					return "unexpected " + key;
				}
			}
		}
		for (String key : properties.keySet()) {
			if (!value.has(key)) {
				continue;
			}
			JsonObject rule = properties.getAsJsonObject(key);
			JsonElement field = value.get(key);
			if ("string".equals(rule.has("type") ? rule.get("type").getAsString() : null)) {
				if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
					return key + " is not a string";
				}
				int length = field.getAsString().length();
				if (rule.has("minLength") && length < rule.get("minLength").getAsInt()) {
					return key + " too short";
				}
				if (rule.has("maxLength") && length > rule.get("maxLength").getAsInt()) {
					return key + " too long";
				}
			}
		}
		return null;
	}

	/** Every run of {@code n} consecutive words, lower-cased and space-joined. */
	static Set<String> ngrams(String text, int n) {
		List<String> words = words(text.toLowerCase(Locale.ROOT));
		Set<String> out = new HashSet<>();
		for (int i = 0; i + n <= words.size(); i++) {
			out.add(String.join(" ", words.subList(i, i + n)));
		}
		return out;
	}

	private static List<String> words(String text) {
		Matcher m = WORD.matcher(text);
		List<String> out = new java.util.ArrayList<>();
		while (m.find()) {
			out.add(m.group());
		}
		return out;
	}

	private static double repeatedTrigramShare(List<String> words) {
		if (words.size() < 6) {
			return 0;
		}
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i + 3 <= words.size(); i++) {
			counts.merge(String.join(" ", words.subList(i, i + 3)).toLowerCase(Locale.ROOT), 1, Integer::sum);
		}
		int repeats = counts.values().stream().mapToInt(c -> c - 1).sum();
		return repeats / (double) (words.size() - 2);
	}

	private static double latinShare(String text) {
		int letters = 0;
		int latin = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isLetter(c)) {
				letters++;
				if (Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) {
					latin++;
				}
			}
		}
		return letters == 0 ? 0 : latin / (double) letters;
	}

	private static boolean numbersGrounded(String text, String facts) {
		Set<String> allowed = new HashSet<>();
		Matcher f = NUMBER.matcher(facts);
		while (f.find()) {
			allowed.add(normalise(f.group()));
		}
		Matcher t = NUMBER.matcher(text);
		while (t.find()) {
			if (!allowed.contains(normalise(t.group()))) {
				return false;
			}
		}
		return true;
	}

	private static String normalise(String number) {
		String n = number.replace(",", "");
		return n.endsWith(".0") ? n.substring(0, n.length() - 2) : n;
	}

	private static boolean containsWords(String text, String phrase) {
		return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}_])").matcher(text).find();
	}
}
