package dev.spacebod.observer.demo;

import dev.spacebod.observer.GameBootstrap;
import dev.spacebod.observer.news.Brief;
import dev.spacebod.observer.news.Ledger;
import dev.spacebod.observer.news.Story;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demonstration town makes a full, varied paper: what it is for. */
class DemoTownTest {
	@Test
	void theDemoTownFillsAVariedPaper() {
		GameBootstrap.ensure();
		Ledger ledger = new Ledger();
		DemoTown.seed(ledger, 40);
		Brief brief = ledger.close("The Observer", 40, 7, false, false, 8, 99).brief();
		assertFalse(brief.founding(), "a town with history, not a founding edition");
		assertEquals(8, brief.stories().size(), brief.stories().toString());
		Set<String> sections = brief.stories().stream().map(Story::section).collect(Collectors.toSet());
		assertTrue(sections.size() >= 5, "a mix of sections: " + sections);
		assertTrue(brief.stories().stream().map(Story::subject).distinct().count() >= 5, "stories about every resident");
		assertTrue(brief.obituaries().size() >= 5, brief.obituaries().toString());
		assertEquals("boss", brief.stories().getFirst().kind(), "the Ender Dragon leads the paper");
	}
}
