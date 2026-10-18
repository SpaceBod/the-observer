package dev.spacebod.observer.sim;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.news.Copy;
import dev.spacebod.observer.news.Ledger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who has their copy, and the paper's voice in the house templates. */
class NewsstandTest {
	@AfterEach
	void resetTone() {
		Observer.config().tone = Tone.BROADSHEET.id;
	}

	@Test
	void eachPrintingIsANewCopyToCollect() {
		SimWorld world = new SimWorld("The Observer");
		world.player("Alex").miningTrip(200, 0);
		world.publish();
		Ledger.PlayerRecord alex = world.ledger.player(world.player("Alex").uuid().toString(), "Alex");
		assertFalse(world.ledger.collected(alex), "nobody has the first edition yet");

		alex.lastPrintingReceived = world.ledger.latestPrinting;
		assertTrue(world.ledger.collected(alex));

		world.reprint();
		assertFalse(world.ledger.collected(alex), "a reprint is a new copy to collect");
	}

	@Test
	void theRoastAndTheChronicleHaveTheirOwnHouseCopy() {
		Copy copy = Copy.get();
		for (String kind : new String[]{"death", "deaths", "miner", "hunter", "advancement.goal", "quiet", "first", "bounty"}) {
			assertNotSame(copy.kind(kind), copy.kind(kind, Tone.ROAST), kind);
			assertNotSame(copy.kind(kind), copy.kind(kind, Tone.CHRONICLE), kind);
		}
		assertEquals(copy.kind("miner"), copy.kind("miner", Tone.BROADSHEET));
	}

	@Test
	void anEditionIsPrintedInTheCurrentTone() {
		Observer.config().tone = Tone.ROAST.id;
		var paper = Scenarios.badWeek().result();
		assertEquals("roast", paper.edition().tone());
		String lead = paper.edition().lead().headline() + " " + paper.edition().lead().body();
		assertTrue(lead.contains("waking up in") || lead.contains("more a lifestyle"), "not the Roast's copy: " + lead);
	}

	@Test
	void issueNumbersNeverGoBackwardsWhenTheFrequencyChanges() {
		SimWorld world = new SimWorld("The Observer");
		world.player("Alex").miningTrip(200, 0);
		world.publish();
		world.publish();
		assertEquals(2, world.ledger.issue, "one number per edition");

		world.period = 1;
		world.publish();
		int daily = world.ledger.issue;
		assertEquals(3, daily, "switching to daily just carries on counting");
		world.reprint();
		assertEquals(daily, world.ledger.issue, "publishing again the same day reprints it");

		world.period = 14;
		world.publish();
		assertEquals(daily + 1, world.ledger.issue, "a fortnightly paper picks up where the daily one stopped");
	}

	@Test
	void aDailyPapersBountiesAreSmallerThanAFortnightlyOnes() {
		var player = java.util.UUID.nameUUIDFromBytes("Alex".getBytes());
		long daily = 0;
		long fortnightly = 0;
		for (long seed = 0; seed < 40; seed++) {
			for (var bounty : dev.spacebod.observer.exchange.Bounties.forPlayer(
					new dev.spacebod.observer.edition.Edition.Exchange(seed, 8, java.util.Map.of(player.toString(), 2), java.util.Map.of(), 1, 1.0), player)) {
				daily += bounty.count();
			}
			for (var bounty : dev.spacebod.observer.exchange.Bounties.forPlayer(
					new dev.spacebod.observer.edition.Edition.Exchange(seed, 8, java.util.Map.of(player.toString(), 2), java.util.Map.of(), 14, 1.0), player)) {
				fortnightly += bounty.count();
			}
		}
		assertTrue(fortnightly > daily * 3, "daily " + daily + ", fortnightly " + fortnightly);
	}

	@Test
	void aDailyPaperSaysToday() {
		SimWorld world = new SimWorld("The Observer");
		world.player("Alex").miningTrip(200, 0).farm(30).potter(1);
		world.publish();
		world.publish();
		world.period = 1;
		world.player("Alex").miningTrip(400, 2);
		var paper = world.publish();
		String all = paper.edition().stories().stream().map(a -> a.headline() + " " + a.body()).reduce("", String::concat);
		assertFalse(all.toLowerCase().contains("week"), all);
	}

	@Test
	void aReaderTheEditionDidNotKnowGetsTheirOwnNotices() {
		SimWorld world = new SimWorld("The Observer");
		world.player("Alex").miningTrip(200, 0);
		var edition = world.publish().edition();
		String newcomer = java.util.UUID.nameUUIDFromBytes("Newcomer".getBytes()).toString();
		assertFalse(edition.exchange().tiers().containsKey(newcomer));

		var theirs = world.ledger.withReader(edition, newcomer, java.util.Map.of("crafted:iron_ingot", 3));
		assertEquals(1, theirs.exchange().tiers().get(newcomer));
		assertFalse(dev.spacebod.observer.exchange.Bounties.forPlayer(theirs.exchange(), java.util.UUID.fromString(newcomer)).isEmpty());
		assertEquals(edition.exchange().tiers().get(world.player("Alex").uuid().toString()),
				theirs.exchange().tiers().get(world.player("Alex").uuid().toString()), "everyone else's notices are unchanged");
	}
}
