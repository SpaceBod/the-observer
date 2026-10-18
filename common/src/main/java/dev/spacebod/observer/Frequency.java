package dev.spacebod.observer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How often the paper comes out, chosen by the server's operators. The house copy, the insights and the
 * prompts are written for a weekly paper; {@link #reword} turns their "this week" into "today" or "this
 * fortnight" so every edition talks about the period it actually covers.
 */
public enum Frequency {
	DAILY("daily", "Daily", 1, "day", "today", "yesterday"),
	WEEKLY("weekly", "Weekly", 7, "week", "this week", "last week"),
	FORTNIGHTLY("fortnightly", "Fortnightly", 14, "fortnight", "this fortnight", "last fortnight");

	private static final Pattern WEEK_WORDS = Pattern.compile(
			"\\b([Tt]his week|[Ll]ast week|[Ww]eekly|[Ww]eek's|[Ww]eeks|[Ww]eek)\\b");

	public final String id;
	public final String title;
	public final int days;
	/** "day", "week", "fortnight". */
	public final String noun;
	private final String thisPeriod;
	private final String lastPeriod;

	Frequency(String id, String title, int days, String noun, String thisPeriod, String lastPeriod) {
		this.id = id;
		this.title = title;
		this.days = days;
		this.noun = noun;
		this.thisPeriod = thisPeriod;
		this.lastPeriod = lastPeriod;
	}

	/** The frequency with this id, or weekly for anything unknown. */
	public static Frequency of(String id) {
		for (Frequency frequency : values()) {
			if (frequency.id.equals(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
				return frequency;
			}
		}
		return WEEKLY;
	}

	/** The frequency an edition covering this many days was printed at (the nearest, for odd lengths). */
	public static Frequency ofDays(int days) {
		return days <= 3 ? DAILY : days <= 10 ? WEEKLY : FORTNIGHTLY;
	}

	/** Text written for a weekly paper, in this frequency's words. Weekly text comes back unchanged. */
	public String reword(String text) {
		if (this == WEEKLY || text == null || text.isEmpty()) {
			return text;
		}
		Matcher matcher = WEEK_WORDS.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String found = matcher.group(1);
			String lower = found.toLowerCase(Locale.ROOT);
			String replaced = switch (lower) {
				case "this week" -> thisPeriod;
				case "last week" -> lastPeriod;
				case "weekly" -> id;
				case "week's" -> noun + "'s";
				case "weeks" -> noun + "s";
				default -> noun;
			};
			if (Character.isUpperCase(found.charAt(0))) {
				replaced = Character.toUpperCase(replaced.charAt(0)) + replaced.substring(1);
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replaced));
		}
		matcher.appendTail(out);
		return out.toString();
	}
}
