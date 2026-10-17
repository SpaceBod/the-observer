package dev.spacebod.herald.sim;

import dev.spacebod.herald.edition.Article;
import dev.spacebod.herald.exchange.Bounties;
import dev.spacebod.herald.news.Story;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the newsroom makes of each scenario: the right stories lead, the right facts reach the paper, the
 * leaderboards and the Exchange react, and nothing in the paper is empty or invented.
 */
class ScenarioTest {
	@Test
	void foundingEditionCoversWholeLivesWithoutFirsts() {
		Scenarios.Run run = Scenarios.foundingEdition();
		var paper = run.result();
		assertTrue(paper.edition().founding());
		// A whole life has no "before", so nothing in it counts as a first.
		assertFalse(paper.kinds().contains("first"), paper.kinds().toString());
		assertTrue(paper.kinds().contains("miner.rare"), paper.kinds().toString());
		assertEquals(2, paper.edition().obituaries().size());
		// Lifetime figures are the edition's figures here, so both pages are filled.
		assertFalse(paper.boardTitles(false).isEmpty());
		assertFalse(paper.boardTitles(true).isEmpty());
	}

	@Test
	void redstoneBuildIsNoticed() {
		var paper = Scenarios.redstoneEngineer().result();
		String known = paper.knowledge();
		assertTrue(known.contains("redstone parts placed"), known);
		assertTrue(paper.kinds().contains("pursuit.new") || paper.kinds().contains("first"), paper.kinds().toString());
		assertTrue(known.contains("built their first piston") || known.contains("built an observer"), known);
	}

	@Test
	void miningExpeditionWithDiamondsLeads() {
		var paper = Scenarios.miningExpedition().result();
		assertEquals("miner.rare", paper.kinds().getFirst(), paper.kinds().toString());
		assertTrue(paper.knowledge().contains("diamond"), paper.knowledge());
		assertTrue(paper.boardTitles(false).contains("Diamond Prospector"), paper.boardTitles(false).toString());
	}

	@Test
	void monsterHuntMakesAHunterBoardWithARace() {
		var paper = Scenarios.monsterHunt().result();
		assertTrue(paper.kinds().contains("hunter"), paper.kinds().toString());
		var spiderBoard = paper.edition().boards().stream()
				.filter(b -> !b.allTime() && b.title().equals("Top Spider Hunter")).findFirst();
		assertTrue(spiderBoard.isPresent(), paper.boardTitles(false).toString());
		assertEquals("Alex", spiderBoard.get().rows().get(0).name());
		assertEquals("Jo", spiderBoard.get().rows().get(1).name());
	}

	@Test
	void tntIsAFirstAndADemolitionRecord() {
		var paper = Scenarios.demolition().result();
		String known = paper.knowledge();
		assertTrue(known.contains("placed TNT for the first time"), known);
		assertTrue(known.contains("blocks of TNT placed"), known);
		assertTrue(paper.boardTitles(false).contains("Demolition Expert"), paper.boardTitles(false).toString());
	}

	@Test
	void badWeekFillsTheObituariesAndLeadsWithMishaps() {
		var paper = Scenarios.badWeek().result();
		assertEquals(5, paper.edition().obituaries().size());
		assertEquals("deaths", paper.kinds().getFirst(), paper.kinds().toString());
		assertTrue(paper.boardTitles(false).contains("Most Accident-Prone"));
		Story deaths = paper.brief().stories().getFirst();
		assertTrue(deaths.facts().getFirst().contains("5 times"), deaths.facts().toString());
	}

	@Test
	void recordHarvestComparesWithTheUsualWeek() {
		var paper = Scenarios.recordHarvest().result();
		assertTrue(paper.kinds().contains("pursuit.record"), paper.kinds().toString());
		Story record = paper.brief().stories().stream().filter(s -> s.kind().equals("pursuit.record")).findFirst().orElseThrow();
		assertTrue(record.facts().stream().anyMatch(f -> f.contains("usual")), record.facts().toString());
	}

	@Test
	void netherDebutIsAFirstAndOpensNetherBounties() {
		Scenarios.Run run = Scenarios.netherDebut();
		var paper = run.result();
		assertTrue(paper.knowledge().contains("set foot in the Nether for the first time"), paper.knowledge());
		assertEquals(2, paper.edition().exchange().tiers().get(run.player("Alex").uuid().toString()));
	}

	@Test
	void quietWeekStillPrintsAFullPaper() {
		var paper = Scenarios.quietWeek().result();
		// Nothing was news, so the desk sends nothing and the house copy leads with its "quiet week" piece.
		assertTrue(paper.brief().stories().isEmpty(), paper.kinds().toString());
		assertEquals(1, paper.edition().stories().size());
		assertEquals("From the Editor", paper.edition().lead().section());
		assertTrue(paper.edition().fillers().size() >= 6, "fillers: " + paper.edition().fillers().size());
	}

	@Test
	void busyServerSharesThePaperOut() {
		Scenarios.Run run = Scenarios.busyServer();
		var paper = run.result();
		assertEquals(8, paper.brief().stories().size());
		Map<String, Long> perPlayer = paper.brief().stories().stream().filter(s -> s.subject() != null)
				.collect(Collectors.groupingBy(Story::subject, Collectors.counting()));
		assertTrue(perPlayer.size() >= 4, "only " + perPlayer + " made the paper");
		assertTrue(perPlayer.values().stream().allMatch(n -> n <= 3), perPlayer.toString());
		// The challenge advancement is the biggest news of the week.
		assertEquals("advancement.challenge", paper.kinds().getFirst(), paper.kinds().toString());
		// With five players, most boards are real races.
		long races = paper.edition().boards().stream().filter(b -> !b.allTime() && b.rows().size() >= 2).count();
		assertTrue(races >= 5, "races: " + races);
	}

	@Test
	void everyScenarioPrintsACompletePaper() {
		for (var entry : Scenarios.all().entrySet()) {
			Scenarios.Run run = entry.getValue().get();
			var paper = run.result();
			String name = entry.getKey();
			assertFalse(paper.edition().stories().isEmpty(), name);
			for (Article article : paper.edition().stories()) {
				assertFalse(article.headline().isBlank(), name);
				assertFalse(article.body().isBlank(), name);
				// No template placeholder may survive into print.
				assertFalse((article.headline() + article.body()).matches("(?s).*\\{[a-z]+}.*"), name + ": " + article);
			}
			for (SimPlayer player : run.players()) {
				List<Bounties.Bounty> bounties = paper.bounties(player);
				assertEquals(Bounties.SLOTS, bounties.size(), name + " " + player.name());
			}
		}
	}

	@Test
	void editionsAreRepeatable() {
		Function<Scenarios.Run, String> print = run -> run.result().edition().toString();
		assertEquals(print.apply(Scenarios.busyServer()), print.apply(Scenarios.busyServer()));
	}
}
