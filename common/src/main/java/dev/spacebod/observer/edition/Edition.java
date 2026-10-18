package dev.spacebod.observer.edition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;

/**
 * One printed issue, carried whole on the newspaper item so it reads the same forever, on any client,
 * with nothing to look up on the server. Everyone gets the same paper; only the Exchange page differs per
 * reader, and it is worked out from {@link #exchange} and the reader's UUID when the page is opened.
 *
 * @param stories      the lead first, then the rest in printing order
 * @param fillers      opinion columns and almanac items, used in order only while the news pages have room
 * @param obituaries   one line per death since the last edition
 * @param numbers      "By the Numbers" box, one "label|value" entry per row
 * @param names        the players in this edition, picked out in the paper's text
 * @param boards       the leaderboards page
 * @param classifieds  the small ads
 * @param pressWritten whether the stories came from the writing model rather than the house templates
 * @param trivia       the "Did You Know?" column, one "topic|fact" entry each, in the order to print them
 * @param tone         the voice it was written in ("broadsheet", "roast", "chronicle"), for the masthead
 */
public record Edition(String paper, int issue, long day, boolean founding, List<Article> stories, List<Article> fillers,
					  List<String> obituaries, List<String> numbers, List<String> names, List<Board> boards, List<String> classifieds,
					  String weather, boolean pressWritten, Exchange exchange, List<String> trivia, String tone) {
	public static final Codec<Edition> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("paper").forGetter(Edition::paper),
			Codec.INT.fieldOf("issue").forGetter(Edition::issue),
			Codec.LONG.fieldOf("day").forGetter(Edition::day),
			Codec.BOOL.optionalFieldOf("founding", false).forGetter(Edition::founding),
			Article.CODEC.listOf().fieldOf("stories").forGetter(Edition::stories),
			Article.CODEC.listOf().optionalFieldOf("fillers", List.of()).forGetter(Edition::fillers),
			Codec.STRING.listOf().optionalFieldOf("obituaries", List.of()).forGetter(Edition::obituaries),
			Codec.STRING.listOf().optionalFieldOf("numbers", List.of()).forGetter(Edition::numbers),
			Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(Edition::names),
			Board.CODEC.listOf().optionalFieldOf("boards", List.of()).forGetter(Edition::boards),
			Codec.STRING.listOf().optionalFieldOf("classifieds", List.of()).forGetter(Edition::classifieds),
			Codec.STRING.optionalFieldOf("weather", "").forGetter(Edition::weather),
			Codec.BOOL.optionalFieldOf("press", false).forGetter(Edition::pressWritten),
			Exchange.CODEC.optionalFieldOf("exchange", Exchange.NONE).forGetter(Edition::exchange),
			Codec.STRING.listOf().optionalFieldOf("trivia", List.of()).forGetter(Edition::trivia),
			Codec.STRING.optionalFieldOf("tone", "broadsheet").forGetter(Edition::tone)
	).apply(i, Edition::new));
	public static final StreamCodec<ByteBuf, Edition> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public Article lead() {
		return stories.getFirst();
	}

	public Edition withExchange(Exchange exchange) {
		return new Edition(paper, issue, day, founding, stories, fillers, obituaries, numbers, names, boards, classifieds, weather,
				pressWritten, exchange, trivia, tone);
	}

	/**
	 * One leaderboard: the top players at something this edition, or over all time (the Hall of Fame).
	 *
	 * @param unit what the numbers count ("blocks mined")
	 * @param rows best first, at most three
	 */
	public record Board(String title, String unit, List<Row> rows, boolean allTime) {
		public static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("title").forGetter(Board::title),
				Codec.STRING.fieldOf("unit").forGetter(Board::unit),
				Row.CODEC.listOf().fieldOf("rows").forGetter(Board::rows),
				Codec.BOOL.optionalFieldOf("all_time", false).forGetter(Board::allTime)
		).apply(i, Board::new));
	}

	public record Row(String name, String value) {
		public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Row::name),
				Codec.STRING.fieldOf("value").forGetter(Row::value)
		).apply(i, Row::new));
	}

	/**
	 * What every reader needs to work out their own bounties, identically on client and server.
	 *
	 * @param seed       this edition's random seed for the Exchange
	 * @param closesDay  the day the next edition is due; bounties can be handed in until then
	 * @param tiers      by player UUID: how far along each player is (0 starting out, 1 iron, 2 Nether, 3 End).
	 *                   Players not listed weren't known when the paper was printed and get no bounties
	 * @param baselines  by player UUID: their kill counts for their slay bounties, as of printing
	 */
	/**
	 * @param days    how long the notices stay up, which sizes the asks
	 * @param rewards how generous the rewards are (1 is balanced)
	 */
	public record Exchange(long seed, long closesDay, Map<String, Integer> tiers, Map<String, Map<String, Integer>> baselines, int days,
						   double rewards) {
		public static final Exchange NONE = new Exchange(0, 0, Map.of(), Map.of(), 7, 1.0);
		public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("seed").forGetter(Exchange::seed),
				Codec.LONG.fieldOf("closes").forGetter(Exchange::closesDay),
				Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("tiers").forGetter(Exchange::tiers),
				Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT)).fieldOf("baselines").forGetter(Exchange::baselines),
				Codec.INT.optionalFieldOf("days", 7).forGetter(Exchange::days),
				Codec.DOUBLE.optionalFieldOf("rewards", 1.0).forGetter(Exchange::rewards)
		).apply(i, Exchange::new));
	}
}
