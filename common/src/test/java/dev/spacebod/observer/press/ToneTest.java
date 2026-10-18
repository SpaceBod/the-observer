package dev.spacebod.observer.press;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.spacebod.observer.GameBootstrap;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.news.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every voice is complete, and its example stories pass the same checks the model's drafts do. */
class ToneTest {
	private static PressProfile profile;

	@BeforeAll
	static void load() {
		GameBootstrap.ensure();
		profile = PressProfile.defaults();
	}

	@Test
	void everyToneHasAStyleAndItsOwnExamples() {
		for (Tone tone : Tone.values()) {
			PressProfile.ToneProfile voice = profile.tone(tone);
			assertFalse(voice.style.isEmpty(), tone.id);
			assertFalse(voice.fillerStyle.isEmpty(), tone.id);
			if (tone != Tone.BROADSHEET) {
				assertEquals(profile.examples.size(), voice.examples.size(), tone.id);
				assertEquals(profile.fillers.examples.size(), voice.fillerExamples.size(), tone.id);
			}
		}
	}

	@Test
	void toneExamplesPassTheSubEditor() {
		for (Tone tone : Tone.values()) {
			for (int i = 0; i < profile.examples.size(); i++) {
				PressProfile.Example example = profile.examples.get(i);
				String reply = Press.reply(profile.tone(tone).examples, i, example.headline, example.story);
				Story story = new Story("example", example.section, example.subject, 1, Map.of(), example.facts);
				Assignment assignment = new Assignment(example.angle, example.support, null, example.quote.isEmpty() ? null : example.quote);
				FactCheck.Verdict verdict = FactCheck.check(reply, story, assignment, List.of(example.subject), profile, Set.of());
				assertNotNull(verdict.draft(), tone.id + " example " + i + ": " + verdict.reason());
			}
			for (int i = 0; i < profile.fillers.examples.size(); i++) {
				PressProfile.FillerExample example = profile.fillers.examples.get(i);
				String reply = Press.reply(profile.tone(tone).fillerExamples, i, example.headline, example.story);
				List<String> facts = new java.util.ArrayList<>(example.notes);
				facts.add("Topic: " + example.topic);
				facts.add(example.writer);
				Story piece = new Story("letter", "Letters", null, 0, Map.of(), facts);
				FactCheck.Verdict verdict = FactCheck.checkFiller(reply, piece, List.of(), profile, Set.of());
				assertNotNull(verdict.draft(), tone.id + " filler example " + i + ": " + verdict.reason());
				JsonObject json = JsonParser.parseString(reply).getAsJsonObject();
				assertFalse(json.get("story").getAsString().isBlank());
			}
		}
	}
}
