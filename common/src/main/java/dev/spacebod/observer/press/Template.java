package dev.spacebod.observer.press;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logic-less prompt templates, the Mustache subset prompts need: {@code {{name}}} is replaced by a value, and
 * {@code {{#name}}...{{/name}}} keeps its contents only when the value is non-empty ({@code {{^name}}} only when
 * it is empty). Lines left empty by a
 * section are dropped, so optional instructions vanish cleanly.
 */
final class Template {
	private static final Pattern SECTION = Pattern.compile("\\{\\{([#^])(\\w+)}}(.*?)\\{\\{/\\2}}", Pattern.DOTALL);
	private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)}}");

	private Template() {
	}

	static String render(List<String> lines, Map<String, String> values) {
		StringBuilder out = new StringBuilder();
		for (String line : lines) {
			String rendered = render(line, values);
			if (rendered.isBlank() && !line.isBlank()) {
				continue; // a line that was only an empty section
			}
			if (!out.isEmpty()) {
				out.append('\n');
			}
			out.append(rendered);
		}
		return out.toString();
	}

	static String render(String template, Map<String, String> values) {
		Matcher section = SECTION.matcher(template);
		StringBuilder kept = new StringBuilder();
		while (section.find()) {
			boolean empty = values.getOrDefault(section.group(2), "").isEmpty();
			boolean keep = section.group(1).equals("#") != empty;
			section.appendReplacement(kept, Matcher.quoteReplacement(keep ? section.group(3) : ""));
		}
		section.appendTail(kept);

		Matcher variable = VARIABLE.matcher(kept);
		StringBuilder out = new StringBuilder();
		while (variable.find()) {
			variable.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(variable.group(1), "")));
		}
		variable.appendTail(out);
		return out.toString();
	}
}
