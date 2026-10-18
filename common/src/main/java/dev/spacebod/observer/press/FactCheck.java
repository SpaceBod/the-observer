package dev.spacebod.observer.press;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.spacebod.observer.news.Story;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
 * the press counts for {@code /observer status}.
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
		return check(reply, story, Assignment.NONE, players, profile, copyGuard);
	}

	/** As {@link #check(String, Story, List, PressProfile, Set)}, for a story the editor added facts or a quote to. */
	static Verdict check(String reply, Story story, Assignment assignment, List<String> players, PressProfile profile, Set<String> copyGuard) {
		return check(reply, story, assignment, players, profile, copyGuard, false);
	}

	/**
	 * A letter or opinion column: a "story" whose facts are its topic and the town's figures. No player may be
	 * named and no number invented, but it needn't use a number, and everyday words (creepers, cows) are its topic.
	 */
	static Verdict checkFiller(String reply, Story filler, List<String> players, PressProfile profile, Set<String> copyGuard) {
		return check(reply, filler, Assignment.NONE, players, profile, copyGuard, true);
	}

	private static Verdict check(String reply, Story story, Assignment assignment, List<String> players, PressProfile profile,
								 Set<String> copyGuard, boolean filler) {
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
				return Verdict.fail("banned phrase '" + phrase + "'");
			}
		}
		for (String word : checks.forbiddenWords) {
			if (containsWords(text, word.toLowerCase(Locale.ROOT))) {
				return Verdict.fail("forbidden word '" + word + "'");
			}
		}

		// 5. Grounding
		List<String> grounding = new ArrayList<>(story.facts());
		grounding.addAll(assignment.support());
		if (assignment.previously() != null) {
			grounding.add(assignment.previously());
		}
		if (assignment.quote() != null) {
			grounding.add(assignment.quote());
		}
		String facts = (String.join(" ", grounding) + " " + (story.subject() != null ? story.subject() : "")).toLowerCase(Locale.ROOT);
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
		if (checks.keyNumber && !filler && !story.facts().isEmpty() && !keyNumberUsed(text, story.facts().getFirst())) {
			return Verdict.fail("key number missing");
		}
		for (String word : filler ? List.<String>of() : checks.groundedWords) {
			String w = word.toLowerCase(Locale.ROOT);
			if (containsWords(text, w) && numberForms(w).stream().noneMatch(form -> containsWords(facts, form))) {
				return Verdict.fail("ungrounded '" + word + "'");
			}
		}
		String quoteName = assignment.quoteName();
		boolean quoteAllowed = quoteName != null && text.contains(quoteName.toLowerCase(Locale.ROOT));
		if (checks.noInventedQuotes && !quoteAllowed) {
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

	/**
	 * How good a passing draft is, to choose between drafts: more of the story's numbers used, a headline that
	 * names its player, a length near the house style's middle, and no headline words already used this edition.
	 */
	static double score(Draft draft, Story story, Set<String> headlineWordsUsed) {
		String text = (draft.headline() + " " + draft.story()).toLowerCase(Locale.ROOT);
		double score = 0;
		Matcher numbers = NUMBER.matcher(String.join(" ", story.facts()));
		while (numbers.find()) {
			if (text.contains(numbers.group())) {
				score += 2;
			}
		}
		if (story.subject() != null && draft.headline().toLowerCase(Locale.ROOT).contains(story.subject().toLowerCase(Locale.ROOT))) {
			score += 1;
		}
		score -= Math.abs(words(draft.story()).size() - 55) / 20.0;
		for (String word : words(draft.headline().toLowerCase(Locale.ROOT))) {
			if (word.length() > 3 && headlineWordsUsed.contains(word)) {
				score -= 1.5;
			}
		}
		return score;
	}

	static List<String> headlineWords(String headline) {
		return words(headline.toLowerCase(Locale.ROOT));
	}

	private static final String[] NUMBER_WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
			"eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};

	/** Whether any number in the story's first fact appears, as digits or (up to twenty) as a word. */
	private static boolean keyNumberUsed(String text, String firstFact) {
		Matcher numbers = NUMBER.matcher(firstFact);
		boolean any = false;
		while (numbers.find()) {
			any = true;
			String n = numbers.group();
			if (text.contains(n) || text.contains(normalise(n))) {
				return true;
			}
			try {
				int value = Integer.parseInt(normalise(n));
				if (value >= 0 && value < NUMBER_WORDS.length && containsWords(text, NUMBER_WORDS[value])) {
					return true;
				}
				if (value == 1 && containsWords(text, "once") || value == 2 && containsWords(text, "twice")) {
					return true;
				}
			} catch (NumberFormatException ignored) {
				// a decimal; digits only
			}
		}
		return !any;
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
		List<String> out = new ArrayList<>();
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

	/** A word with its singular or plural beside it: "diamond" is grounded by "Diamonds!" in the facts. */
	static List<String> numberForms(String word) {
		if (word.endsWith("es") && word.length() > 3) {
			return List.of(word, word.substring(0, word.length() - 1), word.substring(0, word.length() - 2));
		}
		if (word.endsWith("s") && word.length() > 2) {
			return List.of(word, word.substring(0, word.length() - 1));
		}
		return List.of(word, word + "s", word + "es");
	}

	private static boolean containsWords(String text, String phrase) {
		return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}_])").matcher(text).find();
	}
}
