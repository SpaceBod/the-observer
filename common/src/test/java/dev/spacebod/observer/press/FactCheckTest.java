package dev.spacebod.observer.press;

import dev.spacebod.observer.GameBootstrap;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.news.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every rule of the sub-editor, against drafts of the kind small models actually write. */
class FactCheckTest {
	private static final String GOOD_STORY = "Luca mined 412 blocks since the last edition, 380 of them stone. Neighbours report a steady tapping "
			+ "from below at all hours. The town is said to be very proud of the new hole.";
	private static PressProfile profile;
	private static Set<String> copyGuard;

	private final Story story = new Story("miner", "Industry", "Luca", 40, Map.of(),
			List.of("Luca mined 412 blocks.", "The block mined most was stone: 380."));

	@BeforeAll
	static void load() {
		GameBootstrap.ensure();
		profile = PressProfile.defaults();
		String system = Template.render(profile.system, Map.of("paper", "The Observer",
				"style", String.join("\n", profile.tone(Tone.BROADSHEET).style)));
		copyGuard = FactCheck.ngrams(system, profile.checks.copyGuardWords);
	}

	private FactCheck.Verdict check(String headline, String body) {
		String reply = "{\"headline\": \"" + headline + "\", \"story\": \"" + body + "\"}";
		return FactCheck.check(reply, story, List.of("Luca", "Steve"), profile, copyGuard);
	}

	private void rejects(String reason, String headline, String body) {
		FactCheck.Verdict verdict = check(headline, body);
		assertNull(verdict.draft(), "should have failed: " + reason);
		assertTrue(verdict.reason().startsWith(reason), "failed for '" + verdict.reason() + "', expected '" + reason + "'");
	}

	@Test
	void acceptsAGoodStory() {
		FactCheck.Verdict verdict = check("Luca Mines 412 Blocks", GOOD_STORY);
		assertNotNull(verdict.draft(), verdict.reason());
		assertEquals("Luca Mines 412 Blocks", verdict.draft().headline());
	}

	@Test
	void structure() {
		assertEquals("not JSON", FactCheck.check("HEADLINE: x\nSTORY: y", story, List.of(), profile, copyGuard).reason());
		assertTrue(FactCheck.check("{\"headline\": \"Luca Mines\"}", story, List.of(), profile, copyGuard).reason().startsWith("schema"));
		rejects("schema", "Luca", GOOD_STORY);
		assertTrue(FactCheck.check("{\"headline\": \"Luca Mines 412 Blocks\", \"story\": \"" + GOOD_STORY + "\", \"extra\": 1}",
				story, List.of(), profile, copyGuard).reason().startsWith("schema"));
	}

	@Test
	void form() {
		rejects("unfinished sentence", "Luca Mines 412 Blocks", GOOD_STORY.substring(0, GOOD_STORY.length() - 1) + " and then");
		rejects("repetition", "Luca Mines 412 Blocks",
				"Luca mined and mined and mined and mined and mined and mined and mined and mined and mined and mined and mined and mined 412 blocks.");
	}

	@Test
	void grounding() {
		rejects("number not in facts", "Luca Mines 413 Blocks", GOOD_STORY);
		rejects("subject not named", "Miner Of The Week", GOOD_STORY.replace("Luca", "The miner"));
		rejects("subject misspelt", "Lucas Mines 412 Blocks", GOOD_STORY);
		rejects("other player named", "Luca Mines 412 Blocks", GOOD_STORY.replace("Neighbours", "Steve and the neighbours"));
		rejects("ungrounded 'diamonds'", "Luca Mines 412 Blocks", GOOD_STORY.replace("of them stone", "of them stone, and two diamonds"));
		assertTrue(FactCheck.numberForms("diamond").contains("diamonds") && FactCheck.numberForms("diamonds").contains("diamond"),
				"a grounded word counts in either number");
		rejects("invented quote", "Luca Mines 412 Blocks", GOOD_STORY.replace("The town is said to be very proud of the new hole.",
				"Luca said: \\\"what a hole\\\", and went home."));
	}

	@Test
	void policy() {
		rejects("forbidden word 'his'", "Luca Mines 412 Blocks", GOOD_STORY.replace("the new hole", "his new hole"));
		rejects("banned phrase 'minecraft'", "Luca Mines 412 Blocks", GOOD_STORY.replace("from below", "from below the Minecraft world"));
	}

	@Test
	void copying() {
		// A run of six words lifted from the style guide.
		rejects("copied from prompt", "Luca Mines 412 Blocks", GOOD_STORY.replace("The town is said to be very proud of the new hole.",
				"The joke is that ordinary things are treated as big news."));
	}

	@Test
	void sanitising() {
		assertEquals("Luca's \"big\" week - again...", Sanitizer.clean("Luca’s “big” week — again…"));
		assertEquals("Red text, bold", Sanitizer.clean("§cRed text,§r **bold**"));
		assertEquals("A tidy line", Sanitizer.clean("A\u0000 tidy​ line 😀"));
		assertEquals("Luca Mines Blocks", Sanitizer.headline("\"Luca Mines Blocks.\""));
	}

	@Test
	void templates() {
		List<String> lines = List.of("Section: {{section}}", "{{#subject}}Refer to {{subject}} as \"they\".{{/subject}}", "Write.");
		assertEquals("Section: Industry\nRefer to Luca as \"they\".\nWrite.", Template.render(lines, Map.of("section", "Industry", "subject", "Luca")));
		// An empty section leaves no blank line behind.
		assertEquals("Section: Industry\nWrite.", Template.render(lines, Map.of("section", "Industry", "subject", "")));
	}

	@Test
	void defaultsBriefEveryStoryKind() {
		for (String kind : List.of("death", "deaths", "advancement.task", "advancement.goal", "advancement.challenge", "boss", "hunter", "miner",
				"miner.rare", "builder", "traveller", "farmer", "trader", "feud", "raid", "breaker", "insomniac", "jumper", "cake", "newcomer",
				"quiet", "first", "pursuit.new", "pursuit.record", "bounty")) {
			assertTrue(profile.briefs.containsKey(kind), "no brief for " + kind);
		}
	}

	private FactCheck.Verdict filler(String body) {
		Story piece = new Story("column", "Opinion", null, 0, Map.of(),
				List.of("Topic: creepers arriving at the door without knocking", "Blocks placed across the town this week: 4,210"));
		String reply = "{\"headline\": \"Creepers Should Knock\", \"story\": \"" + body + "\"}";
		return FactCheck.checkFiller(reply, piece, List.of("Luca", "Steve"), profile, copyGuard);
	}

	@Test
	void fillersStayOffPlayersAndInventedNumbers() {
		String good = "Every other visitor knocks. The creeper alone arrives in silence and leaves with the front wall. With 4,210 blocks placed "
				+ "across the town this week, the Observer would like some of them to stay where they are.";
		assertNotNull(filler(good).draft(), filler(good).reason());
		assertEquals("other player named", filler(good.replace("Every other visitor", "Steve")).reason());
		assertEquals("number not in facts", filler(good.replace("4,210", "5,000")).reason());
	}

	@Test
	void templatesHaveInvertedSections() {
		List<String> lines = List.of("Write {{#letter}}a letter from {{writer}}{{/letter}}{{^letter}}a column{{/letter}}.");
		assertEquals("Write a letter from Orla.", Template.render(lines, Map.of("letter", "yes", "writer", "Orla")));
		assertEquals("Write a column.", Template.render(lines, Map.of("letter", "", "writer", "")));
	}
}
