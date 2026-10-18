package dev.spacebod.observer.press;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.news.Brief;
import dev.spacebod.observer.news.Dossier;
import dev.spacebod.observer.news.Story;
import net.ladenthin.llama.value.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The editor pass: one model call that reads the whole week (every story's facts, every dossier, the townsfolk
 * who may be quoted) and plans each story. It can only point at fact ids and cast ids, so whatever it chooses is
 * true; anything it gets wrong is dropped rather than printed.
 */
final class Editor {
	private Editor() {
	}

	/** Assignments by story index; stories the editor skipped (or every story, if the call fails) get none. */
	static Map<Integer, Assignment> plan(LlamaWriter writer, Brief brief, PressProfile profile, String grammar, int seed) {
		Map<Integer, Assignment> plans = new HashMap<>();
		PressProfile.EditorProfile editor = profile.editor;
		if (!editor.enabled || brief.stories().isEmpty()) {
			return plans;
		}
		Frequency frequency = Frequency.ofDays(brief.periodDays());
		String system = frequency.reword(Template.render(editor.system, Map.of("paper", brief.paper())));
		String reply;
		try {
			reply = writer.write(system, List.of(new Pair<>("user", frequency.reword(request(brief, editor)))), seed, grammar, editor.maxTokens);
		} catch (RuntimeException e) {
			Observer.LOGGER.warn("The editor could not plan this edition; writing without a plan: {}", e.getMessage());
			return plans;
		}
		try {
			JsonArray array = JsonParser.parseString(reply.strip()).getAsJsonObject().getAsJsonArray("plans");
			int quotes = 0;
			for (JsonElement element : array) {
				JsonObject plan = element.getAsJsonObject();
				int index = plan.get("story").getAsInt() - 1;
				if (index < 0 || index >= brief.stories().size() || plans.containsKey(index)) {
					continue;
				}
				Story story = brief.stories().get(index);
				Dossier dossier = story.subject() != null ? brief.dossiers().get(story.subject()) : null;
				List<String> support = new ArrayList<>();
				String previously = null;
				for (JsonElement id : plan.getAsJsonArray("use")) {
					Dossier.Fact fact = dossier != null ? dossier.fact(id.getAsString().strip()) : null;
					if (fact == null || brief.stories().stream().anyMatch(s -> s.facts().contains(fact.text()))) {
						continue; // not this player's, or already in a story
					}
					if (fact.text().startsWith("Previously in the Observer: ")) {
						previously = fact.text().substring("Previously in the Observer: ".length());
					} else if (support.size() < editor.maxSupport) {
						support.add(fact.text());
					}
				}
				String quote = null;
				String quoteId = plan.get("quote").getAsString().strip();
				if (quoteId.matches("T\\d+") && quotes < editor.maxQuotes) {
					int q = Integer.parseInt(quoteId.substring(1)) - 1;
					if (q >= 0 && q < brief.cast().size()) {
						quote = brief.cast().get(q);
						quotes++;
					}
				}
				String angle = Sanitizer.clean(plan.get("angle").getAsString());
				plans.put(index, new Assignment(angle.length() > 200 ? angle.substring(0, 200) : angle, support, previously, quote));
			}
		} catch (RuntimeException e) {
			Observer.LOGGER.debug("The editor's plan could not be read; writing without it: {}", reply);
			plans.clear();
		}
		return plans;
	}

	private static String request(Brief brief, PressProfile.EditorProfile editor) {
		StringBuilder stories = new StringBuilder();
		for (int i = 0; i < brief.stories().size(); i++) {
			Story story = brief.stories().get(i);
			stories.append(i + 1).append(". [").append(story.section()).append("] ")
					.append(story.subject() != null ? story.subject() + ": " : "").append(String.join(" ", story.facts())).append('\n');
		}
		StringBuilder dossiers = new StringBuilder();
		brief.dossiers().values().forEach(d -> dossiers.append(d.text()));
		StringBuilder cast = new StringBuilder();
		for (int i = 0; i < brief.cast().size(); i++) {
			cast.append("T").append(i + 1).append(' ').append(brief.cast().get(i)).append('\n');
		}
		Map<String, String> values = new LinkedHashMap<>();
		values.put("stories", stories.toString().strip());
		values.put("dossiers", dossiers.toString().strip());
		values.put("cast", cast.toString().strip());
		return Template.render(editor.request, values);
	}
}
