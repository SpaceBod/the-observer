package dev.spacebod.herald.press;

import com.google.gson.JsonObject;
import dev.spacebod.herald.Herald;
import dev.spacebod.herald.edition.Article;
import dev.spacebod.herald.edition.Edition;
import dev.spacebod.herald.news.Brief;
import dev.spacebod.herald.news.Copy;
import dev.spacebod.herald.news.Story;
import net.ladenthin.llama.value.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns a {@link Brief} into a printed {@link Edition}. With a model installed, each story is drafted from its
 * facts, checked by {@link FactCheck} and redrafted until the profile's attempts run out, then left to the house
 * template. Runs on the press thread, never the server thread: loading the model takes seconds.
 */
public final class Press {
	/** Why the last edition was or wasn't written by the model, for /herald status. */
	private static volatile String lastReport = "no edition printed yet";

	private Press() {
	}

	public static String status() {
		Optional<Path> model = ModelCatalog.installed();
		if (!Herald.config().usePress) {
			return "house templates (the model is switched off in herald.json)";
		}
		if (model.isEmpty()) {
			return "house templates (no model in herald-models/)";
		}
		return "model " + model.get().getFileName() + "; last edition: " + lastReport;
	}

	public static Edition print(Brief brief) {
		Copy copy = Copy.get();
		List<Article> written = new ArrayList<>();
		Optional<Path> model = Herald.config().usePress ? ModelCatalog.installed() : Optional.empty();
		if (model.isEmpty() || brief.stories().isEmpty()) {
			return copy.print(brief, written, false);
		}

		PressProfile profile = PressProfile.load(Herald.configDir());
		String system = Template.render(profile.system, Map.of("paper", brief.paper()));
		List<Pair<String, String>> examples = examples(profile);
		Set<String> copyGuard = copyGuard(profile, system);
		Map<String, Integer> rejections = new LinkedHashMap<>();
		int attempts = Math.max(1, profile.sampling.attempts);

		long start = System.nanoTime();
		int byModel = 0;
		try (LlamaWriter writer = new LlamaWriter(model.get(), Herald.config().threads(), profile)) {
			for (int i = 0; i < brief.stories().size(); i++) {
				Story story = brief.stories().get(i);
				List<Pair<String, String>> turns = new ArrayList<>(examples);
				turns.add(new Pair<>("user", request(profile, story.section(), profile.brief(story.kind()), story.facts(), story.subject())));
				Article article = null;
				for (int attempt = 0; attempt < attempts && article == null; attempt++) {
					String reply = writer.write(system, turns, brief.issue() * 131 + i * 17 + attempt);
					FactCheck.Verdict verdict = FactCheck.check(reply, story, brief.players(), profile, copyGuard);
					if (verdict.draft() != null) {
						article = new Article(story.section(), verdict.draft().headline(), verdict.draft().story());
					} else {
						rejections.merge(verdict.reason(), 1, Integer::sum);
						Herald.LOGGER.debug("Press rejected a draft of '{}' ({}): {}", story.kind(), verdict.reason(), reply);
					}
				}
				written.add(article);
				if (article != null) {
					byModel++;
				}
			}
			lastReport = byModel + "/" + brief.stories().size() + " stories by the model"
					+ (rejections.isEmpty() ? "" : "; drafts rejected: " + rejections.entrySet().stream()
					.map(e -> e.getKey() + " x" + e.getValue()).collect(Collectors.joining(", ")));
		} catch (Throwable t) {
			// Missing native library for this platform, an unreadable model, out of memory: fall back, never crash.
			lastReport = "the model could not run (" + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "") + ")";
			Herald.LOGGER.warn("The press could not run the model; printing with house templates", t);
		}
		Herald.LOGGER.info("Press: {} in {} s", lastReport, (System.nanoTime() - start) / 1_000_000_000L);
		return copy.print(brief, written, byModel * 2 >= brief.stories().size() && byModel > 0);
	}

	private static String request(PressProfile profile, String section, String brief, List<String> facts, String subject) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("section", section);
		values.put("brief", brief);
		values.put("facts", facts.stream().map(f -> "- " + f).collect(Collectors.joining("\n")));
		values.put("subject", subject != null ? subject : "");
		return Template.render(profile.request, values);
	}

	/** The profile's examples as earlier turns: the request as the user wrote it, the reply as JSON. */
	private static List<Pair<String, String>> examples(PressProfile profile) {
		List<Pair<String, String>> turns = new ArrayList<>();
		for (PressProfile.Example example : profile.examples) {
			turns.add(new Pair<>("user", request(profile, example.section, example.brief, example.facts, example.subject)));
			JsonObject reply = new JsonObject();
			reply.addProperty("headline", example.headline);
			reply.addProperty("story", example.story);
			turns.add(new Pair<>("assistant", reply.toString()));
		}
		return turns;
	}

	/** Word runs from the instructions and example replies; finding one in a story means it was copied. */
	private static Set<String> copyGuard(PressProfile profile, String system) {
		int n = profile.checks.copyGuardWords;
		Set<String> grams = new HashSet<>();
		if (n <= 0) {
			return grams;
		}
		grams.addAll(FactCheck.ngrams(system, n));
		for (PressProfile.Example example : profile.examples) {
			grams.addAll(FactCheck.ngrams(example.headline + " " + example.story, n));
		}
		return grams;
	}
}
