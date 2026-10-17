package dev.spacebod.herald.press;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans model output before it is checked or printed: NFKC normalisation, plain quotes and dashes, and nothing
 * that could change how the text renders (control characters, formatting codes, emoji, markdown).
 */
final class Sanitizer {
	private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]");
	private static final Pattern FORMATTING_CODE = Pattern.compile("§.?");
	private static final Pattern SYMBOLS = Pattern.compile("[\\p{So}\\p{Sk}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");
	private static final Pattern MARKDOWN = Pattern.compile("[*_`#~<>\\[\\]|]");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private Sanitizer() {
	}

	static String clean(String text) {
		String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
		s = s.replace('“', '"').replace('”', '"').replace('‘', '\'').replace('’', '\'')
				.replace("—", " - ").replace("–", "-").replace("…", "...");
		s = FORMATTING_CODE.matcher(s).replaceAll("");
		s = CONTROL.matcher(s).replaceAll(" ");
		s = SYMBOLS.matcher(s).replaceAll("");
		s = MARKDOWN.matcher(s).replaceAll("");
		return SPACES.matcher(s).replaceAll(" ").strip();
	}

	/** A headline also loses wrapping quotes and a trailing full stop. */
	static String headline(String text) {
		return clean(text).replaceAll("^[\"']+|[\"']+$", "").replaceAll("[.]+$", "").strip();
	}
}
