package dev.spacebod.observer.press;

import dev.spacebod.observer.Tone;
import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.edition.Article;
import dev.spacebod.observer.news.Brief;
import dev.spacebod.observer.news.Story;
import net.ladenthin.llama.value.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Letters to the editor and opinion columns written by the model, so a quiet week still reads fresh. Each is about
 * an everyday topic from the profile, may use the town's figures, and names no player: it is checked as a story
 * whose only facts are its topic and those figures.
 */
final class Fillers {
	private Fillers() {
	}

	static List<Article> write(LlamaWriter writer, Brief brief, PressProfile profile, String grammar, Map<String, Integer> rejections) {
		PressProfile.FillerProfile fillers = profile.fillers;
		List<Article> out = new ArrayList<>();
		if (!fillers.enabled || fillers.topics.isEmpty() || fillers.system.isEmpty()) {
			return out;
		}
		int count = brief.stories().size() < fillers.quietBelow ? fillers.quietCount : fillers.busyCount;
		Random random = new Random(brief.issue() * 7919L + brief.printing());
		List<String> topics = new ArrayList<>(fillers.topics);
		Collections.shuffle(topics, random);
		// Letter writers from the end of the townsfolk list; the stories quote from its start.
		List<String> writers = new ArrayList<>(brief.cast());
		Collections.reverse(writers);

		PressProfile.ToneProfile voice = profile.tone(Tone.of(Observer.config().tone));
		Frequency frequency = Frequency.ofDays(brief.periodDays());
		String system = frequency.reword(Template.render(fillers.system, Map.of("paper", brief.paper(), "style", String.join("\n", voice.fillerStyle))));
		Set<String> copyGuard = copyGuard(profile, system, voice);
		List<String> notes = brief.numbers().stream().map(row -> {
			String[] parts = row.split("\\|", 2);
			return frequency.reword(parts[0] + " across the town this week: " + (parts.length > 1 ? parts[1] : ""));
		}).toList();
		List<Pair<String, String>> examples = Press.reword(examples(fillers, voice), frequency);
		Set<String> headlineWords = new HashSet<>();
		int drafts = Math.max(1, profile.sampling.drafts);

		for (int i = 0; i < count && i < topics.size(); i++) {
			boolean letter = i % 2 == 0 && i / 2 < writers.size();
			String from = letter ? writers.get(i / 2) : "";
			String topic = topics.get(i);
			List<String> mine = notes.isEmpty() ? List.of() : List.of(notes.get(random.nextInt(notes.size())));
			List<String> facts = new ArrayList<>();
			facts.add("Topic: " + topic);
			facts.addAll(mine);
			facts.add(from);
			Story piece = new Story(letter ? "letter" : "column", letter ? "Letters" : "Opinion", null, 0, Map.of(), facts);

			List<Pair<String, String>> turns = new ArrayList<>(examples);
			turns.add(new Pair<>("user", frequency.reword(request(fillers, letter, from, topic, mine))));
			Article best = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (int attempt = 0; attempt < Math.max(1, profile.sampling.attempts) && best == null; attempt++) {
				List<Integer> seeds = new ArrayList<>();
				for (int d = 0; d < drafts; d++) {
					seeds.add(brief.printing() * 977 + i * 31 + attempt * drafts + d);
				}
				for (String reply : writer.drafts(system, turns, seeds, grammar)) {
					FactCheck.Verdict verdict = FactCheck.checkFiller(reply, piece, brief.players(), profile, copyGuard);
					if (verdict.draft() == null) {
						rejections.merge("filler " + verdict.reason(), 1, Integer::sum);
						continue;
					}
					double score = FactCheck.score(verdict.draft(), piece, headlineWords);
					if (score > bestScore) {
						bestScore = score;
						String body = verdict.draft().story() + (letter ? " — " + from : "");
						best = new Article(piece.section(), verdict.draft().headline(), body);
					}
				}
			}
			if (best != null) {
				out.add(best);
				headlineWords.addAll(FactCheck.headlineWords(best.headline()));
			}
		}
		return out;
	}

	private static String request(PressProfile.FillerProfile fillers, boolean letter, String writer, String topic, List<String> notes) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("letter", letter ? "yes" : "");
		values.put("writer", writer);
		values.put("topic", topic);
		values.put("notes", notes.stream().map(n -> "- " + n).collect(Collectors.joining("\n")));
		return Template.render(fillers.request, values);
	}

	private static List<Pair<String, String>> examples(PressProfile.FillerProfile fillers, PressProfile.ToneProfile voice) {
		List<Pair<String, String>> turns = new ArrayList<>();
		for (int i = 0; i < fillers.examples.size(); i++) {
			PressProfile.FillerExample example = fillers.examples.get(i);
			turns.add(new Pair<>("user", request(fillers, example.letter, example.writer, example.topic, example.notes)));
			turns.add(new Pair<>("assistant", Press.reply(voice.fillerExamples, i, example.headline, example.story)));
		}
		return turns;
	}

	private static Set<String> copyGuard(PressProfile profile, String system, PressProfile.ToneProfile voice) {
		int n = profile.checks.copyGuardWords;
		Set<String> grams = new HashSet<>();
		if (n <= 0) {
			return grams;
		}
		grams.addAll(FactCheck.ngrams(system, n));
		for (PressProfile.FillerExample example : profile.fillers.examples) {
			grams.addAll(FactCheck.ngrams(example.headline + " " + example.story, n));
		}
		for (PressProfile.Written written : voice.fillerExamples) {
			grams.addAll(FactCheck.ngrams(written.headline + " " + written.story, n));
		}
		return grams;
	}
}
