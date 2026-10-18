package dev.spacebod.observer.sim;

import dev.spacebod.observer.GameBootstrap;
import dev.spacebod.observer.edition.Article;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.exchange.Bounties;
import dev.spacebod.observer.news.Brief;
import dev.spacebod.observer.news.Copy;
import dev.spacebod.observer.news.Ledger;
import dev.spacebod.observer.news.Story;
import dev.spacebod.observer.press.Press;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A world without a server: players do things between editions, and {@link #publish()} runs exactly what the
 * server's newsroom does at sunrise ({@link Ledger#close}), then prints the edition with the house templates
 * (or, with {@link #publishWithPress()}, with the writing model if one is configured).
 */
public final class SimWorld {
	final Ledger ledger = new Ledger();
	final Map<String, SimPlayer> players = new LinkedHashMap<>();
	final String paper;
	long day = 1;
	/** Days per edition: 7 is weekly, 1 daily, 14 fortnightly. */
	int period = 7;
	private long seed = 1234;

	public SimWorld(String paper) {
		GameBootstrap.ensure();
		this.paper = paper;
	}

	public SimPlayer player(String name) {
		return players.computeIfAbsent(name, n -> new SimPlayer(this, n));
	}

	/** One period of play: whatever {@code play} has the players do happens before the next edition. */
	public SimWorld week(Consumer<SimWorld> play) {
		play.accept(this);
		return this;
	}

	public Result publish() {
		Ledger.Closing closing = close();
		Edition edition = Copy.get().print(closing.brief(), List.of(), false).withExchange(closing.exchange());
		ledger.recordPrinted(closing.brief(), edition);
		ledger.setLatestEdition(edition);
		return new Result(closing.brief(), edition);
	}

	/** Prints the week just printed again, from the same data (an operator running /observer publish twice). */
	public Result reprint() {
		day -= period;
		return publish();
	}

	/** As {@link #publish()}, through the writing model when Observer's config names one. */
	public Result publishWithPress() {
		Ledger.Closing closing = close();
		Edition edition = Press.print(closing.brief()).withExchange(closing.exchange());
		ledger.recordPrinted(closing.brief(), edition);
		ledger.setLatestEdition(edition);
		return new Result(closing.brief(), edition);
	}

	private Ledger.Closing close() {
		for (SimPlayer p : players.values()) {
			Ledger.PlayerRecord record = ledger.player(p.uuid.toString(), p.name);
			record.latest = new HashMap<>(p.stats);
			record.events.addAll(p.events);
			p.events.clear();
		}
		Ledger.Closing closing = ledger.close(paper, day, period, false, false, 8, seed++);
		day += period;
		return closing;
	}

	/** One printed edition and the brief it was printed from. */
	public record Result(Brief brief, Edition edition) {
		public List<String> kinds() {
			return brief.stories().stream().map(Story::kind).toList();
		}

		public List<Story> storiesAbout(String player) {
			return brief.stories().stream().filter(s -> player.equals(s.subject())).toList();
		}

		/** Every fact in every story and every dossier: what the paper knows. */
		public String knowledge() {
			List<String> lines = new ArrayList<>();
			brief.stories().forEach(s -> lines.addAll(s.facts()));
			brief.dossiers().values().forEach(d -> d.facts().forEach(f -> lines.add(f.text())));
			return String.join("\n", lines);
		}

		public List<String> boardTitles(boolean allTime) {
			return edition.boards().stream().filter(b -> b.allTime() == allTime).map(Edition.Board::title).toList();
		}

		public List<Bounties.Bounty> bounties(SimPlayer player) {
			return Bounties.forPlayer(edition.exchange(), player.uuid);
		}

		/** The edition as readable Markdown, for the scenario gallery. */
		public String markdown(String title, List<SimPlayer> readers) {
			StringBuilder md = new StringBuilder();
			md.append("# ").append(title).append("\n\n");
			md.append("**").append(edition.paper()).append("**, No. ").append(edition.issue()).append(", day ").append(edition.day())
					.append(edition.founding() ? " (Founding Edition)" : "").append(edition.pressWritten() ? ", written by the model" : ", house templates")
					.append("\n\n## News\n\n");
			for (int i = 0; i < edition.stories().size(); i++) {
				Article a = edition.stories().get(i);
				Story s = i < brief.stories().size() ? brief.stories().get(i) : null;
				md.append("### ").append(a.headline()).append("\n*").append(a.section())
						.append(s != null ? " · " + s.kind() + " · score " + Math.round(s.score()) : "").append("*\n\n")
						.append(a.body()).append("\n\n");
			}
			for (var dossier : brief.dossiers().values()) {
				list(md, "Dossier: " + dossier.player(), dossier.facts().stream().map(f -> f.id() + " " + f.text()).toList());
			}
			list(md, "Obituaries", edition.obituaries());
			list(md, "By the Numbers", edition.numbers().stream().map(n -> n.replace("|", ": ")).toList());
			list(md, "Fillers available", edition.fillers().stream().map(f -> f.section() + ": " + f.headline()).toList());
			for (boolean allTime : new boolean[]{false, true}) {
				md.append("## ").append(allTime ? "Hall of Fame" : "Leaderboard").append("\n\n");
				for (Edition.Board board : edition.boards()) {
					if (board.allTime() == allTime) {
						md.append("- **").append(board.title()).append("** (").append(board.unit()).append("): ");
						md.append(String.join(", ", board.rows().stream().map(r -> r.name() + " " + r.value()).toList())).append("\n");
					}
				}
				md.append("\n");
			}
			md.append("## The Exchange\n\n");
			for (SimPlayer reader : readers) {
				md.append("**").append(reader.name).append("** (tier ").append(edition.exchange().tiers().get(reader.uuid.toString())).append(")\n\n");
				for (Bounties.Bounty b : Bounties.forPlayer(edition.exchange(), reader.uuid)) {
					md.append("- ").append(b.kind() == Bounties.Kind.DELIVER ? "WANTED " : "BOUNTY ").append(b.count()).append(" ")
							.append(b.target().getPath()).append(" for ").append(b.rewardCount()).append(" ").append(b.reward().getPath())
							.append(" — ").append(b.signature()).append(": \"").append(b.note()).append("\"\n");
				}
				md.append("\n");
			}
			return md.toString();
		}

		private static void list(StringBuilder md, String title, List<String> items) {
			if (!items.isEmpty()) {
				md.append("## ").append(title).append("\n\n");
				items.forEach(item -> md.append("- ").append(item).append("\n"));
				md.append("\n");
			}
		}
	}
}
