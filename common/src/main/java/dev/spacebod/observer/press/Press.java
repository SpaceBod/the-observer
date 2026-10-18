package dev.spacebod.observer.press;

import dev.spacebod.observer.Tone;
import com.google.gson.JsonObject;
import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.edition.Article;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.news.Brief;
import dev.spacebod.observer.news.Copy;
import dev.spacebod.observer.news.Story;
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
 * Turns a {@link Brief} into a printed {@link Edition}. With a model installed, the {@link Editor} first plans the
 * edition from every player's dossier; then each story is drafted several times at once, every draft is checked
 * by {@link FactCheck}, and the best that passes is printed. A story with no passing draft after the profile's
 * attempts falls back to the house template. Runs on the press thread, never the server thread.
 */
public final class Press {
	/** Why the last edition was or wasn't written by the model, for /observer status. */
	private static volatile String lastReport = "no edition printed yet";

	private Press() {
	}

	/** Where the model last ran ("GPU (name, 25/25 layers)", "CPU (8 threads)"), or "not run yet". */
	public static String device() {
		return LlamaWriter.device();
	}

	/** The llama.cpp build loaded this game ("vulkan", "cpu"), or null before the first edition written with the model. */
	public static String library() {
		return Engine.loaded();
	}

	public static String status() {
		Optional<Path> model = ModelCatalog.installed();
		if (!Observer.config().usePress) {
			return "house templates (the model is switched off in observer.json)";
		}
		if (model.isEmpty()) {
			return "house templates (no model in observer-models/)";
		}
		return "model " + model.get().getFileName() + " on the " + LlamaWriter.device() + "; last edition: " + lastReport;
	}

	public static Edition print(Brief brief) {
		Copy copy = Copy.get();
		List<Article> written = new ArrayList<>();
		Optional<Path> model = Observer.config().usePress ? ModelCatalog.installed() : Optional.empty();
		if (model.isEmpty()) {
			return copy.print(brief, written, false);
		}

		PressProfile profile = PressProfile.load(Observer.configDir());
		PressProfile.ToneProfile voice = profile.tone(Tone.of(Observer.config().tone));
		Frequency frequency = Frequency.ofDays(brief.periodDays());
		String system = frequency.reword(Template.render(profile.system, Map.of("paper", brief.paper(), "style", String.join("\n", voice.style))));
		List<Pair<String, String>> examples = reword(examples(profile, voice), frequency);
		Set<String> copyGuard = copyGuard(profile, system, voice);
		Map<String, Integer> rejections = new LinkedHashMap<>();
		Set<String> headlineWords = new HashSet<>();
		int attempts = Math.max(1, profile.sampling.attempts);
		int drafts = Math.max(1, profile.sampling.drafts);

		long start = System.nanoTime();
		long planned = start;
		int byModel = 0;
		List<Article> fillers = List.of();
		long storiesDone = start;
		try (LlamaWriter writer = new LlamaWriter(model.get(), Observer.config().threads(), profile, Observer.config().gpu())) {
			String storyGrammar = LlamaWriter.grammar(profile.schema);
			Map<Integer, Assignment> plans = Editor.plan(writer, brief, profile, LlamaWriter.grammar(profile.editor.schema), brief.printing() * 7 + 3);
			planned = System.nanoTime();
			for (int i = 0; i < brief.stories().size(); i++) {
				Story story = brief.stories().get(i);
				Assignment assignment = plans.getOrDefault(i, Assignment.NONE);
				List<Pair<String, String>> turns = new ArrayList<>(examples);
				turns.add(new Pair<>("user", frequency.reword(request(profile, story.section(), profile.brief(story.kind()), story.facts(), story.subject(), assignment))));
				Article article = null;
				for (int attempt = 0; attempt < attempts && article == null; attempt++) {
					List<Integer> seeds = new ArrayList<>();
					for (int d = 0; d < drafts; d++) {
						seeds.add(brief.printing() * 131 + i * 17 + attempt * drafts + d);
					}
					double best = Double.NEGATIVE_INFINITY;
					for (String reply : writer.drafts(system, turns, seeds, storyGrammar)) {
						FactCheck.Verdict verdict = FactCheck.check(reply, story, assignment, brief.players(), profile, copyGuard);
						if (verdict.draft() == null) {
							rejections.merge(verdict.reason(), 1, Integer::sum);
							Observer.LOGGER.debug("Press rejected a draft of '{}' ({}): {}", story.kind(), verdict.reason(), reply);
							continue;
						}
						double score = FactCheck.score(verdict.draft(), story, headlineWords);
						if (score > best) {
							best = score;
							article = new Article(story.section(), verdict.draft().headline(), verdict.draft().story());
						}
					}
				}
				written.add(article);
				if (article != null) {
					byModel++;
					headlineWords.addAll(FactCheck.headlineWords(article.headline()));
				}
			}
			storiesDone = System.nanoTime();
			fillers = Fillers.write(writer, brief, profile, storyGrammar, rejections);
			lastReport = byModel + "/" + brief.stories().size() + " stories and " + fillers.size() + " letters and columns by the model, "
					+ plans.size() + " planned by the editor"
					+ (rejections.isEmpty() ? "" : "; drafts rejected: " + rejections.entrySet().stream()
					.map(e -> e.getKey() + " x" + e.getValue()).collect(Collectors.joining(", ")));
		} catch (Throwable t) {
			// Missing native library for this platform, an unreadable model, out of memory: fall back, never crash.
			lastReport = "the model could not run (" + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "") + ")";
			Observer.LOGGER.warn("The press could not run the model; printing with house templates", t);
		}
		long end = System.nanoTime();
		Observer.LOGGER.info("Press: {} (editor {} s, stories {} s, letters and columns {} s)", lastReport, (planned - start) / 1_000_000_000L,
				Math.max(0, storiesDone - planned) / 1_000_000_000L, Math.max(0, end - storiesDone) / 1_000_000_000L);
		return copy.print(brief, written, byModel * 2 >= brief.stories().size() && byModel > 0, fillers);
	}

	private static String request(PressProfile profile, String section, String brief, List<String> facts, String subject, Assignment assignment) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("section", section);
		values.put("brief", brief);
		values.put("angle", assignment.angle());
		values.put("facts", bullets(facts));
		values.put("support", bullets(assignment.support()));
		values.put("previously", assignment.previously() != null ? assignment.previously() : "");
		values.put("quote", assignment.quote() != null ? assignment.quote() : "");
		values.put("subject", subject != null ? subject : "");
		return Template.render(profile.request, values);
	}

	private static String bullets(List<String> lines) {
		return lines.stream().map(f -> "- " + f).collect(Collectors.joining("\n"));
	}

	/** The profile's examples as earlier turns: the request as the user wrote it, the reply as JSON. */
	private static List<Pair<String, String>> examples(PressProfile profile, PressProfile.ToneProfile voice) {
		List<Pair<String, String>> turns = new ArrayList<>();
		for (int i = 0; i < profile.examples.size(); i++) {
			PressProfile.Example example = profile.examples.get(i);
			Assignment assignment = new Assignment(example.angle, example.support, null, example.quote.isEmpty() ? null : example.quote);
			turns.add(new Pair<>("user", request(profile, example.section, example.brief, example.facts, example.subject, assignment)));
			turns.add(new Pair<>("assistant", reply(voice.examples, i, example.headline, example.story)));
		}
		return turns;
	}

	/** Prompt turns written for a weekly paper, in the words of this one's frequency. */
	static List<Pair<String, String>> reword(List<Pair<String, String>> turns, Frequency frequency) {
		return turns.stream().map(turn -> new Pair<>(turn.getKey(), frequency.reword(turn.getValue()))).toList();
	}

	/** An example's reply as JSON, in the tone's own words when it has them. */
	static String reply(List<PressProfile.Written> voiced, int index, String headline, String story) {
		PressProfile.Written written = index < voiced.size() ? voiced.get(index) : null;
		JsonObject reply = new JsonObject();
		reply.addProperty("headline", written != null ? written.headline : headline);
		reply.addProperty("story", written != null ? written.story : story);
		return reply.toString();
	}

	/** Word runs from the instructions and example replies; finding one in a story means it was copied. */
	private static Set<String> copyGuard(PressProfile profile, String system, PressProfile.ToneProfile voice) {
		int n = profile.checks.copyGuardWords;
		Set<String> grams = new HashSet<>();
		if (n <= 0) {
			return grams;
		}
		grams.addAll(FactCheck.ngrams(system, n));
		for (PressProfile.Example example : profile.examples) {
			grams.addAll(FactCheck.ngrams(example.headline + " " + example.story, n));
		}
		for (PressProfile.Written written : voice.examples) {
			grams.addAll(FactCheck.ngrams(written.headline + " " + written.story, n));
		}
		return grams;
	}
}
