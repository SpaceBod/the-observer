package dev.spacebod.observer.news;

import dev.spacebod.observer.edition.Edition;

import java.util.List;
import java.util.Map;

/**
 * Everything an edition will say, decided on the server thread and handed to the press to write up. The
 * press only turns {@link #stories} into prose; every other box is final already.
 *
 * @param printing which printing this is in the world, reprints included; varies the wording
 * @param stories  in printing order, the lead first
 * @param boards  the leaderboards page; {@code boardIds} are remembered so the same boards rest next time
 * @param dossiers by player name, for everyone with a story
 * @param cast     townsfolk the stories may quote ("Hattie Fenwick, Farmer of Oakridge")
 * @param players  everyone in this edition; the model may name no one else
 */
public record Brief(String paper, int issue, int printing, long day, int periodDays, boolean founding, List<Story> stories,
				   List<String> obituaries, List<String> numbers, List<Edition.Board> boards, List<String> boardIds,
				   Map<String, Dossier> dossiers, List<String> cast,
				   String weather, List<String> players) {
}
