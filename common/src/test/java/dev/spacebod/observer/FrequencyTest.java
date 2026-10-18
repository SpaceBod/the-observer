package dev.spacebod.observer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The weekly paper's words, turned into a daily or fortnightly paper's. */
class FrequencyTest {
	@Test
	void rewordsWeeksInEveryForm() {
		String text = "This week Alex mined 300 blocks, a record week; usually about 40 a week. Farmer of the Week. Weekly news.";
		assertEquals("Today Alex mined 300 blocks, a record day; usually about 40 a day. Farmer of the Day. Daily news.",
				Frequency.DAILY.reword(text));
		assertEquals("This fortnight Alex mined 300 blocks, a record fortnight; usually about 40 a fortnight. Farmer of the Fortnight. Fortnightly news.",
				Frequency.FORTNIGHTLY.reword(text));
		assertEquals(text, Frequency.WEEKLY.reword(text));
	}

	@Test
	void leavesOtherWordsAlone() {
		assertEquals("A weekend at the weekender's.", Frequency.DAILY.reword("A weekend at the weekender's."));
	}

	@Test
	void knowsEachFrequencyByItsLength() {
		assertEquals(Frequency.DAILY, Frequency.ofDays(1));
		assertEquals(Frequency.WEEKLY, Frequency.ofDays(7));
		assertEquals(Frequency.FORTNIGHTLY, Frequency.ofDays(14));
	}
}
