package dev.spacebod.observer.client;

import dev.spacebod.observer.Tone;
import dev.spacebod.observer.edition.Article;
import dev.spacebod.observer.edition.Edition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typesets an edition onto three two-page spreads, once when the screen opens: pages 1-2 the news, 3-4 the
 * Exchange (drawn live by the screen), 5-6 the leaderboards. Nothing is ever cut: text wraps or shrinks, and a
 * story or box item that doesn't fit whole is left out. Coordinates are paper units: a spread is
 * {@link #SPREAD_W} x {@link #PAGE_H}, scaled as a whole by the screen.
 */
final class NewspaperLayout {
	static final int PAGE_W = 170;
	static final int PAGE_H = 236;
	static final int SPREAD_W = PAGE_W * 2;

	private static final int OUTER = 12;
	private static final int INNER = 11;
	private static final int TOP = 10;
	private static final int BOTTOM = 18;
	private static final int GUTTER = 6;
	static final int TEXT_W = PAGE_W - OUTER - INNER;
	static final int COLUMN_W = (TEXT_W - GUTTER) / 2;

	static final int INK = 0xFF26221C;
	static final int GREY = 0xFF6E6656;
	static final int RULE = 0xFF8C8068;
	static final int ACCENT = 0xFF8B1E1E;
	static final int PAPER = 0xFFF2EAD5;
	/** Players' names: a quiet dark blue, so readers spot themselves. */
	private static final Style NAME = Style.EMPTY.withColor(TextColor.fromRgb(0x1F3A8A));

	static final float BODY = 0.62f;
	static final float SMALL = 0.55f;
	private static final float HEAD = 0.72f;
	private static final float MIN_SCALE = 0.35f;

	record Text(FormattedCharSequence line, float x, float y, float scale, int color) {
	}

	record Fill(float x0, float y0, float x1, float y1, int color) {
	}

	static final class Page {
		final List<Text> texts = new ArrayList<>();
		final List<Fill> fills = new ArrayList<>();
		/** Whether the rule between this page's columns has been drawn. */
		boolean ruled;
	}

	private final Font font;
	private final Edition edition;
	private final List<Page> pages = new ArrayList<>();
	/** Matches any player's name as a whole word, longest first; null with no players. */
	private final @Nullable Pattern names;
	/** First of the Exchange's two pages, drawn live by the screen: after the news, which takes one spread or two. */
	int exchangePage = 2;
	/** Last page the news may use; its outer column is the "Did You Know?" column. */
	private int lastNewsPage = 3;

	// Flow state: the column being filled and how far down it is.
	private int pageIndex;
	private int column;
	private float y;
	private float columnTop;
	// Dry runs: set a story on a scratch page to see whether it would fit the news pages.
	private boolean dry;
	private boolean overflow;
	private final Page scratch = new Page();

	NewspaperLayout(Font font, Edition edition) {
		this.font = font;
		this.edition = edition;
		List<String> sorted = edition.names().stream().sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).toList();
		this.names = sorted.isEmpty() ? null : Pattern.compile("(?<![\\p{L}\\p{N}_])(" + String.join("|", sorted) + ")(?![\\p{L}\\p{N}_])");
	}

	List<Page> build() {
		// Two spreads of news; the second ends with the "Did You Know?" column.
		layOutNews(3);
		sidebar(page(1));

		exchangePage = lastNewsPage + 1;
		page(exchangePage + 3);
		leaderboard(page(exchangePage + 2), false);
		leaderboard(page(exchangePage + 3), true);
		for (int i = 1; i < pages.size(); i++) {
			runningHead(pages.get(i), i);
		}
		for (int i = 0; i < pages.size(); i++) {
			folio(pages.get(i), i);
		}
		return pages;
	}

	/** The news up to {@code lastPage}; false if a story had to be left out for lack of room. */
	private boolean layOutNews(int lastPage) {
		pages.clear();
		lastNewsPage = lastPage;
		overflow = false;
		boolean allPrinted = true;
		Page front = page(0);
		float top = masthead(front);
		Article lead = edition.stories().getFirst();
		top = banner(front, lead, top);
		pageIndex = 0;
		column = 0;
		columnTop = top;
		y = top;

		List<String> trivia = triviaColumn(lastPage);
		lines(font.split(inked(lead.body(), false), Math.round(COLUMN_W / BODY)));
		for (int i = 1; i < edition.stories().size(); i++) {
			Article story = edition.stories().get(i);
			if (fits(() -> story(story))) {
				story(story);
			} else {
				allPrinted = false;
			}
		}
		for (Article filler : edition.fillers()) {
			if (fits(() -> story(filler))) {
				story(filler);
			}
		}
		// Facts the column had no room for fill whatever space is left.
		for (String entry : trivia) {
			String[] parts = entry.split("\\|", 2);
			Article fact = new Article(parts[0], "Did You Know?", parts.length > 1 ? parts[1] : "");
			if (fits(() -> story(fact))) {
				story(fact);
			}
		}
		return allPrinted;
	}

	// Front page

	private float masthead(Page p) {
		float left = left(0);
		float y = TOP;
		fill(p, left, y, left + TEXT_W, y + 0.5f, INK);
		y += 4;
		Component name = Component.literal(edition.paper()).withStyle(ChatFormatting.BOLD);
		float scale = fit(name, TEXT_W, 1.6f);
		centred(p, name, left + TEXT_W / 2f, y, scale, INK);
		y += 9 * scale + 1;
		line(p, Component.literal(tagline(edition.tone())).withStyle(ChatFormatting.ITALIC), left, y, TEXT_W, SMALL, GREY, Align.CENTRE);
		y += 7;
		fill(p, left, y, left + TEXT_W, y + 1.5f, INK);
		fill(p, left, y + 2.5f, left + TEXT_W, y + 3, INK);
		y += 5;
		// Three parts, a third of the width each, so they can never run into one another.
		float third = TEXT_W / 3f;
		line(p, Component.literal("No. " + edition.issue()), left, y, third - 2, SMALL, INK, Align.LEFT);
		line(p, Component.literal(edition.founding() ? "FOUNDING EDITION" : "DAY " + edition.day()), left + third, y, third, SMALL, INK, Align.CENTRE);
		line(p, Component.literal("ONE EMERALD"), left + 2 * third + 2, y, third - 2, SMALL, INK, Align.RIGHT);
		y += 6;
		fill(p, left, y, left + TEXT_W, y + 0.5f, INK);
		return y + 5;
	}

	/** The motto under the paper's name, which says what voice it is written in. */
	private static String tagline(String tone) {
		return switch (Tone.of(tone)) {
			case ROAST -> "No resident is safe";
			case CHRONICLE -> "Being a true chronicle of deeds most mighty";
			case BROADSHEET -> "All the news that fits, and some that barely does";
		};
	}

	/** The lead's section, headline (as large as two lines allow) and byline, across the whole front page. */
	private float banner(Page p, Article lead, float y) {
		float left = left(0);
		y = label(p, lead.section(), left, y, TEXT_W);
		Component headline = inked(lead.headline(), true);
		float scale = 1.35f;
		while (scale > HEAD && font.split(headline, Math.round(TEXT_W / scale)).size() > 2) {
			scale -= 0.05f;
		}
		for (FormattedCharSequence line : font.split(headline, Math.round(TEXT_W / scale))) {
			p.texts.add(new Text(line, left, y, scale, INK));
			y += 9 * scale;
		}
		y += 1;
		line(p, Component.literal("From our correspondent · Day " + edition.day()).withStyle(ChatFormatting.ITALIC),
				left, y, TEXT_W, SMALL, GREY, Align.LEFT);
		y += 7;
		fill(p, left, y, left + TEXT_W, y + 0.5f, RULE);
		return y + 4;
	}

	// The flow

	private void story(Article article) {
		Headline headline = headline(article);
		List<FormattedCharSequence> body = font.split(inked(article.body(), false), Math.round(COLUMN_W / BODY));
		float keep = headerHeight(headline) + 2 * lineHeight(BODY);
		float separator = atColumnTop() ? 0 : 7;
		if (y + separator + keep > columnBottom()) {
			nextColumn();
			separator = 0;
		}
		header(article, headline, separator);
		lines(body);
	}

	/** Whether {@code placement} would fit the news pages, found by setting it on a scratch page and undoing it. */
	private boolean fits(Runnable placement) {
		int savedPage = pageIndex;
		int savedColumn = column;
		float savedY = y;
		float savedTop = columnTop;
		dry = true;
		overflow = false;
		placement.run();
		boolean fits = !overflow;
		dry = false;
		overflow = false;
		pageIndex = savedPage;
		column = savedColumn;
		y = savedY;
		columnTop = savedTop;
		scratch.texts.clear();
		scratch.fills.clear();
		return fits;
	}

	private record Headline(List<FormattedCharSequence> lines, float scale) {
	}

	private Headline headline(Article article) {
		return new Headline(font.split(inked(article.headline(), true), Math.round(COLUMN_W / HEAD)), HEAD);
	}

	private float headerHeight(Headline headline) {
		return 7 + headline.lines().size() * 9 * headline.scale() + 2;
	}

	private void header(Article article, Headline headline, float separator) {
		if (separator > 0) {
			float x = columnX();
			fill(current(), x + COLUMN_W * 0.3f, y + 2.5f, x + COLUMN_W * 0.7f, y + 3, RULE);
			y += separator;
		}
		y = label(current(), article.section(), columnX(), y, COLUMN_W);
		for (FormattedCharSequence line : headline.lines()) {
			current().texts.add(new Text(line, columnX(), y, headline.scale(), INK));
			y += 9 * headline.scale();
		}
		y += 2;
	}

	private void lines(List<FormattedCharSequence> lines) {
		float height = lineHeight(BODY);
		for (FormattedCharSequence line : lines) {
			if (y + height > columnBottom()) {
				nextColumn();
			}
			if (overflow) {
				return;
			}
			current().texts.add(new Text(line, columnX(), y, BODY, INK));
			y += height;
		}
	}

	private boolean atColumnTop() {
		return y <= columnTop + 0.5f;
	}

	/**
	 * The flow's columns: page 1's two, page 2's inner one (its outer column holds the boxes), then both columns
	 * of any further news pages. Past the last news page is overflow: the flow stays at the foot of the last
	 * column, so nothing more fits there, and never goes back up a page. Everything after the lead is dry-run
	 * first, so for real this only stops a placement that was never going to fit.
	 */
	private void nextColumn() {
		if (overflow) {
			return;
		}
		int nextPage = pageIndex;
		int nextColumn = column + 1;
		// Page 2's outer column holds the boxes, the last news page's the "Did You Know?" column.
		if (nextColumn > 1 || (nextColumn == 1 && (pageIndex == 1 || pageIndex == lastNewsPage && lastNewsPage > 1))) {
			nextPage++;
			nextColumn = 0;
		}
		if (nextPage > lastNewsPage) {
			overflow = true;
			y = columnBottom();
			return;
		}
		pageIndex = nextPage;
		column = nextColumn;
		Page p = current();
		columnTop = pageIndex == 0 ? columnTop : contentTop();
		y = columnTop;
		if (!dry && (column == 1 || pageIndex > 0) && !p.ruled) {
			float x = left(pageIndex) + COLUMN_W + GUTTER / 2f;
			fill(p, x, columnTop, x + 0.5f, PAGE_H - BOTTOM, RULE);
			p.ruled = true;
		}
	}

	private Page current() {
		return dry ? scratch : page(pageIndex);
	}

	private float columnX() {
		return left(pageIndex) + column * (COLUMN_W + GUTTER);
	}

	private static float columnBottom() {
		return PAGE_H - BOTTOM;
	}

	/**
	 * The last news page's outer column: as many of the edition's facts as fit whole, each under its topic.
	 * Returns the facts left over, for any space the news leaves.
	 */
	private List<String> triviaColumn(int pageIndex) {
		List<String> left = new ArrayList<>();
		if (pageIndex < 2) {
			left.addAll(edition.trivia());
			return left;
		}
		Page p = page(pageIndex);
		float x = left(pageIndex) + COLUMN_W + GUTTER;
		float y = contentTop();
		float bottom = PAGE_H - BOTTOM;
		fill(p, x - GUTTER / 2f, y, x - GUTTER / 2f + 0.5f, bottom, RULE);
		p.ruled = true;
		y = box(p, "DID YOU KNOW?", x, y, COLUMN_W);
		boolean first = true;
		for (String entry : edition.trivia()) {
			String[] parts = entry.split("\\|", 2);
			Component fact = Component.literal(parts.length > 1 ? parts[1] : parts[0]);
			float start = first ? y : y + 5;
			int lines = font.split(fact, Math.round(COLUMN_W / SMALL)).size();
			if (start + 7 + lines * lineHeight(SMALL) > bottom) {
				left.add(entry); // too long for the room left; a shorter one may still fit
				continue;
			}
			if (!first) {
				dotted(p, x, y + 2, COLUMN_W);
			}
			float body = label(p, parts[0], x, start, COLUMN_W);
			y = whole(p, fact, x, body, bottom);
			first = false;
		}
		return left;
	}

	// Page 2's boxes

	private void sidebar(Page p) {
		float x = left(1) + COLUMN_W + GUTTER;
		float y = contentTop();
		// Leave room for the colophon at the foot of the column.
		float bottom = PAGE_H - BOTTOM - 2 * lineHeight(SMALL) - 3;
		fill(p, x - GUTTER / 2f, y, x - GUTTER / 2f + 0.5f, PAGE_H - BOTTOM, RULE);
		p.ruled = true;

		y = box(p, "OBITUARIES", x, y, COLUMN_W);
		List<String> obituaries = edition.obituaries();
		if (obituaries.isEmpty()) {
			y = whole(p, Component.literal("No deaths reported. Well done, everyone.").withStyle(ChatFormatting.ITALIC), x, y, bottom);
		}
		int shown = 0;
		for (String obituary : obituaries) {
			float next = whole(p, inked(obituary, false), x, y, bottom - lineHeight(SMALL));
			if (next == y) {
				break;
			}
			y = next + 1;
			shown++;
		}
		if (shown < obituaries.size()) {
			y = whole(p, Component.literal("...and " + (obituaries.size() - shown) + " more.").withStyle(ChatFormatting.ITALIC), x, y, bottom);
		}
		y += 4;

		if (!edition.numbers().isEmpty() && y + 9 + lineHeight(SMALL) <= bottom) {
			y = box(p, "BY THE NUMBERS", x, y, COLUMN_W);
			for (String row : edition.numbers()) {
				if (y + lineHeight(SMALL) > bottom) {
					break;
				}
				String[] parts = row.split("\\|", 2);
				y = leaderRow(p, Component.literal(parts[0]), Component.literal(parts.length > 1 ? parts[1] : "").withStyle(ChatFormatting.BOLD),
						x, y, COLUMN_W);
			}
			y += 4;
		}

		String[] weather = edition.weather().split("\\|");
		if (weather.length >= 2 && y + 9 + 2 * lineHeight(SMALL) <= bottom) {
			y = box(p, "WEATHER", x, y, COLUMN_W);
			y = whole(p, Component.literal("Today: " + weather[0]), x, y, bottom);
			y = whole(p, Component.literal("Moon: " + weather[1]), x, y, bottom);
			if (weather.length >= 3) {
				y = whole(p, Component.literal(weather[2]).withStyle(ChatFormatting.ITALIC), x, y, bottom);
			}
			y += 4;
		}

		if (!edition.classifieds().isEmpty() && y + 9 + 2 * lineHeight(SMALL) <= bottom) {
			y = box(p, "CLASSIFIEDS", x, y, COLUMN_W);
			for (int i = 0; i < edition.classifieds().size(); i++) {
				float start = i > 0 ? y + 2.5f : y;
				float next = whole(p, Component.literal(edition.classifieds().get(i)), x, start, bottom);
				if (next == start) {
					break; // this ad doesn't fit whole; leave it out
				}
				if (i > 0) {
					dotted(p, x, y + 0.5f, COLUMN_W);
				}
				y = next;
			}
		}
	}

	// The leaderboard pages

	/** Page 5, the edition's leaders, or page 6, the Hall of Fame: all-time records. */
	private void leaderboard(Page p, boolean allTime) {
		int index = pages.indexOf(p);
		float left = left(index);
		float y = contentTop();
		Component title = Component.literal(allTime ? "HALL OF FAME" : "THE LEADERBOARD").withStyle(ChatFormatting.BOLD);
		float titleScale = fit(title, TEXT_W, 1.4f);
		centred(p, title, left + TEXT_W / 2f, y, titleScale, INK);
		y += 9 * titleScale + 2;
		String sub = allTime ? "The town's records of all time" : "Who led the town at what since the last edition";
		y = centredWrapped(p, Component.literal(sub).withStyle(ChatFormatting.ITALIC), left, y, TEXT_W, SMALL, GREY);
		y += 1;
		fill(p, left, y, left + TEXT_W, y + 0.5f, INK);
		y += 4;

		List<Edition.Board> boards = edition.boards().stream().filter(b -> b.allTime() == allTime).toList();
		if (boards.isEmpty()) {
			centredWrapped(p, Component.literal("No records to report yet. The Observer's statisticians are standing by."), left, y + 4, TEXT_W, BODY, INK);
			return;
		}
		float bottom = PAGE_H - BOTTOM;
		float[] columnY = {y, y};
		fill(p, left + COLUMN_W + GUTTER / 2f, y, left + COLUMN_W + GUTTER / 2f + 0.5f, bottom, RULE);
		for (Edition.Board board : boards) {
			float height = 10 + lineHeight(SMALL) + 1 + board.rows().size() * lineHeight(SMALL) + 5;
			int c = columnY[0] <= columnY[1] ? 0 : 1;
			if (columnY[c] + height > bottom) {
				c = 1 - c;
				if (columnY[c] + height > bottom) {
					continue; // no room for this one whole
				}
			}
			float x = left + c * (COLUMN_W + GUTTER);
			float by = box(p, board.title().toUpperCase(Locale.ROOT), x, columnY[c], COLUMN_W);
			line(p, Component.literal(board.unit()).withStyle(ChatFormatting.ITALIC), x, by, COLUMN_W, SMALL, GREY, Align.LEFT);
			by += lineHeight(SMALL) + 1;
			for (int i = 0; i < board.rows().size(); i++) {
				Edition.Row row = board.rows().get(i);
				Component name = i == 0
						? Component.literal("1. ").withStyle(ChatFormatting.BOLD).append(Component.literal(row.name()).withStyle(NAME.withBold(true)))
						: Component.literal((i + 1) + ". ").append(Component.literal(row.name()).withStyle(NAME));
				by = leaderRow(p, name, Component.literal(row.value()).withStyle(ChatFormatting.BOLD), x, by, COLUMN_W);
			}
			columnY[c] = by + 5;
		}
	}

	// Page furniture

	private void runningHead(Page p, int index) {
		float left = left(index);
		Component name = Component.literal(edition.paper().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
		Component issue = Component.literal("No. " + edition.issue() + " · Day " + edition.day());
		float issueWidth = Math.min(font.width(issue) * SMALL, TEXT_W * 0.4f);
		boolean leftPage = index % 2 == 0;
		if (leftPage) {
			line(p, name, left, TOP, TEXT_W - issueWidth - 4, SMALL, INK, Align.LEFT);
			line(p, issue, left + TEXT_W - issueWidth, TOP, issueWidth, SMALL, INK, Align.RIGHT);
		} else {
			line(p, issue, left, TOP, issueWidth, SMALL, INK, Align.LEFT);
			line(p, name, left + issueWidth + 4, TOP, TEXT_W - issueWidth - 4, SMALL, INK, Align.RIGHT);
		}
		fill(p, left, TOP + 6, left + TEXT_W, TOP + 6.5f, INK);
	}

	private void folio(Page p, int index) {
		centred(p, Component.literal(Integer.toString(index + 1)), left(index) + TEXT_W / 2f, PAGE_H - 12, SMALL, GREY);
		if (index == 1) {
			Component colophon = Component.literal(edition.pressWritten() ? "Written at the Observer's automatic press" : "Set by hand at the Observer office")
					.withStyle(ChatFormatting.ITALIC);
			float x = left(1) + COLUMN_W + GUTTER;
			whole(p, colophon, x, PAGE_H - BOTTOM - 2 * lineHeight(SMALL), PAGE_H);
		}
	}

	static float contentTop() {
		return TOP + 11;
	}

	/** Left edge of a page's text block; even pages sit on the left of the spread, odd on the right. */
	static float left(int pageIndex) {
		return pageIndex % 2 == 0 ? OUTER : PAGE_W + INNER;
	}

	private Page page(int index) {
		while (pages.size() <= index) {
			pages.add(new Page());
		}
		return pages.get(index);
	}

	// Drawing helpers: nothing here can draw past the width it is given.

	/** Text with every player's name picked out in {@link #NAME}; bold for headlines. */
	private Component inked(String text, boolean bold) {
		Style base = bold ? Style.EMPTY.withBold(true) : Style.EMPTY;
		MutableComponent out = Component.empty();
		int from = 0;
		if (names != null) {
			Matcher m = names.matcher(text);
			while (m.find()) {
				out.append(Component.literal(text.substring(from, m.start())).withStyle(base));
				out.append(Component.literal(m.group()).withStyle(NAME.withBold(bold)));
				from = m.end();
			}
		}
		return out.append(Component.literal(text.substring(from)).withStyle(base));
	}

	enum Align {
		LEFT, CENTRE, RIGHT
	}

	/** The largest scale, up to {@code scale}, at which {@code text} fits {@code width}. */
	private float fit(Component text, float width, float scale) {
		float natural = font.width(text);
		return natural * scale <= width ? scale : Math.max(MIN_SCALE, width / Math.max(1, natural));
	}

	/** One line of text in a box of {@code width}, shrunk if it would not fit. */
	private void line(Page p, Component text, float x, float y, float width, float scale, int color, Align align) {
		float s = fit(text, width, scale);
		float w = font.width(text) * s;
		float dx = switch (align) {
			case LEFT -> 0;
			case CENTRE -> (width - w) / 2;
			case RIGHT -> width - w;
		};
		// Shrunk text keeps its line: centre it in the height it would have had.
		p.texts.add(new Text(text.getVisualOrderText(), x + dx, y + (9 * scale - 9 * s) / 2, s, color));
	}

	/** A label on the left and a figure on the right, joined by dots; the label gives way if space is short. */
	private float leaderRow(Page p, Component label, Component value, float x, float y, float width) {
		float valueWidth = Math.min(font.width(value) * SMALL, width * 0.5f);
		line(p, value, x + width - valueWidth, y, valueWidth, SMALL, INK, Align.RIGHT);
		float labelSpace = width - valueWidth - 3;
		line(p, label, x, y, labelSpace, SMALL, INK, Align.LEFT);
		float labelWidth = Math.min(font.width(label) * SMALL, labelSpace);
		for (float d = x + labelWidth + 2; d < x + width - valueWidth - 2; d += 2) {
			fill(p, d, y + 3.5f, d + 0.5f, y + 4, GREY);
		}
		return y + lineHeight(SMALL);
	}

	/** Wrapped small text, drawn only if all of it fits above {@code bottom}; returns {@code y} unchanged if not. */
	private float whole(Page p, FormattedText text, float x, float y, float bottom) {
		List<FormattedCharSequence> lines = font.split(text, Math.round(COLUMN_W / SMALL));
		if (y + lines.size() * lineHeight(SMALL) > bottom) {
			return y;
		}
		for (FormattedCharSequence line : lines) {
			p.texts.add(new Text(line, x, y, SMALL, INK));
			y += lineHeight(SMALL);
		}
		return y;
	}

	private float centredWrapped(Page p, Component text, float left, float y, float width, float scale, int color) {
		for (FormattedCharSequence line : font.split(text, Math.round(width / scale))) {
			p.texts.add(new Text(line, left + (width - font.width(line) * scale) / 2, y, scale, color));
			y += lineHeight(scale);
		}
		return y;
	}

	private float label(Page p, String section, float x, float y, float width) {
		line(p, Component.literal(section.toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD), x, y, width, SMALL, ACCENT, Align.LEFT);
		return y + 7;
	}

	float box(Page p, String title, float x, float y, float width) {
		fill(p, x, y, x + width, y + 7, INK);
		line(p, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 2, y + 1.5f, width - 4, SMALL, PAPER, Align.LEFT);
		return y + 9;
	}

	private void dotted(Page p, float x, float y, float width) {
		for (float d = x; d < x + width; d += 2) {
			fill(p, d, y, d + 0.5f, y + 0.5f, RULE);
		}
	}

	private void centred(Page p, Component text, float centreX, float y, float scale, int color) {
		p.texts.add(new Text(text.getVisualOrderText(), centreX - font.width(text) * scale / 2, y, scale, color));
	}

	private static void fill(Page p, float x0, float y0, float x1, float y1, int color) {
		p.fills.add(new Fill(x0, y0, x1, y1, color));
	}

	private float lineHeight(float scale) {
		return font.lineHeight * scale;
	}
}
