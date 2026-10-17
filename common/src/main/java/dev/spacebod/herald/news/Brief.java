package dev.spacebod.herald.news;

import dev.spacebod.herald.edition.Edition;

import java.util.List;

/**
 * Everything an edition will say, decided on the server thread and handed to the press to write up. The
 * press only turns {@link #stories} into prose; every other box is final already.
 *
 * @param stories in printing order, the lead first
 * @param briefs  one-line items for the "In Brief" column
 * @param boards  the leaderboards page; {@code boardIds} are remembered so the same boards rest next time
 * @param players everyone in this edition; the model may name no one else
 */
public record Brief(String paper, int issue, long day, int periodDays, boolean founding, List<Story> stories,
				   List<String> obituaries, List<String> numbers, List<String> briefs, List<Edition.Board> boards, List<String> boardIds,
				   String weather, List<String> players) {
}
