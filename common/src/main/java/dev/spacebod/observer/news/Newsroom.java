package dev.spacebod.observer.news;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverConfig;
import dev.spacebod.observer.delivery.Delivery;
import dev.spacebod.observer.demo.DemoTown;
import dev.spacebod.observer.stand.Newsstands;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.press.Press;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The server side of the paper: keeps the ledger, notices deaths and advancements, and when an edition is due
 * (sunrise on its day) closes the period, sends the brief to the press thread and hands the printed edition
 * to {@link Delivery}. Loader entrypoints forward the server's lifecycle events here.
 */
public final class Newsroom {
	/** The paper's name, on every masthead. */
	public static final String PAPER = "The Observer";
	public static final long TICKS_PER_DAY = 24000L;
	private static final ExecutorService PRESS = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Observer Press");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});

	private static @Nullable Ledger ledger;
	/** The brief at the press right now; printed with the house templates instead if the server stops first. */
	private static @Nullable Brief printing;
	private static Edition.Exchange printingExchange = Edition.Exchange.NONE;
	private static final Random SEEDS = new Random();

	private Newsroom() {
	}

	public static void onServerStarted(MinecraftServer server) {
		ledger = Ledger.load(server.getWorldPath(LevelResource.ROOT).resolve("spacebod_observer"));
		StatsImport.seed(ledger, server);
		printing = null;
	}

	public static void onServerStopping(MinecraftServer server) {
		Ledger l = ledger;
		if (l == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			l.player(player.getStringUUID(), player.getGameProfile().name()).latest = StatSnapshot.capture(player);
		}
		Brief unfinished = printing;
		if (unfinished != null) {
			l.setLatestEdition(Copy.get().print(unfinished, List.of(), false).withExchange(printingExchange));
		}
		l.save();
		Delivery.clear();
		ledger = null;
		printing = null;
	}

	public static void onPlayerJoin(ServerPlayer player) {
		Ledger l = ledger;
		if (l == null) {
			return;
		}
		l.player(player.getStringUUID(), player.getGameProfile().name());
		Edition latest = l.latestEdition();
		Ledger.PlayerRecord record = l.players.get(player.getStringUUID());
		if (latest != null && !l.collected(record) && record.lastPrintingOffered < l.latestPrinting) {
			Delivery.send(player, latest);
		}
	}

	public static void onPlayerLeave(ServerPlayer player) {
		Ledger l = ledger;
		if (l != null) {
			l.player(player.getStringUUID(), player.getGameProfile().name()).latest = StatSnapshot.capture(player);
		}
	}

	public static void onDeath(ServerPlayer player, String deathMessage, String key) {
		Ledger l = ledger;
		if (l != null) {
			l.player(player.getStringUUID(), player.getGameProfile().name()).events
					.add(new Ledger.Event("death", deathMessage, "", day(player.level().getServer()), key));
		}
	}

	public static void onAdvancement(ServerPlayer player, AdvancementHolder advancement) {
		Ledger l = ledger;
		// Recipe unlocks and other silent advancements aren't news.
		AdvancementNews.Info info = AdvancementNews.of(advancement);
		if (l == null || info == null) {
			return;
		}
		l.player(player.getStringUUID(), player.getGameProfile().name()).events.add(new Ledger.Event("advancement." + info.frame(),
				info.title(), info.description(), day(player.level().getServer()), advancement.id().toString()));
	}

	public static void onServerTick(MinecraftServer server) {
		Ledger l = ledger;
		if (l == null) {
			return;
		}
		Delivery.tick(server);
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		int period = Observer.config().frequency().days;
		long day = day(server);
		// First sight of the world, or the clock was turned back: the next edition is tomorrow morning. After
		// that, editions come out on the first morning of each week.
		if (l.nextEditionDay < 0 || l.nextEditionDay > day + period) {
			l.nextEditionDay = day + 1;
		}
		boolean morning = server.overworld().getOverworldClockTime() % TICKS_PER_DAY < TICKS_PER_DAY / 2;
		if (Observer.config().autoPublish && printing == null && day >= l.nextEditionDay && morning && !server.getPlayerList().getPlayers().isEmpty()) {
			publish(server);
		}
	}

	/** Fills the ledger with the demonstration town and prints an edition about it. False if one is already at the press. */
	public static boolean demo(MinecraftServer server) {
		Ledger l = ledger;
		if (l == null || printing != null) {
			return false;
		}
		DemoTown.seed(l, day(server));
		return publish(server);
	}

	/** Closes the period and sends it to press. False if an edition is already being printed. */
	public static boolean publish(MinecraftServer server) {
		Ledger l = ledger;
		if (l == null || printing != null) {
			return false;
		}
		ObserverConfig config = Observer.config();
		ServerLevel overworld = server.overworld();
		long day = day(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			l.player(player.getStringUUID(), player.getGameProfile().name()).latest = StatSnapshot.capture(player);
		}

		Ledger.Closing closing = l.close(PAPER, day, config.frequency().days, overworld.isRaining(), overworld.isThundering(),
				config.storiesPerEdition, SEEDS.nextLong());
		Brief brief = closing.brief();
		Edition.Exchange exchange = closing.exchange();
		l.save();

		printing = brief;
		printingExchange = exchange;
		Newsstands.refresh();
		CompletableFuture.supplyAsync(() -> Press.print(brief), PRESS).whenComplete((edition, error) -> server.execute(() -> {
			if (ledger != l || printing != brief) {
				return; // the world closed while printing; onServerStopping already printed it
			}
			printing = null;
			Edition printed = edition;
			if (error != null || printed == null) {
				Observer.LOGGER.warn("The press failed; printing issue {} with house templates", brief.issue(), error);
				printed = Copy.get().print(brief, List.of(), false);
			}
			printed = printed.withExchange(exchange);
			l.recordPrinted(brief, printed);
			l.setLatestEdition(printed);
			l.save();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Delivery.send(player, printed);
			}
			Newsstands.newEdition();
		}));
		return true;
	}

	public static void onBountyDone(ServerPlayer player, String forWhom, String deed) {
		Ledger l = ledger;
		if (l != null) {
			l.player(player.getStringUUID(), player.getGameProfile().name()).events
					.add(new Ledger.Event("bounty", forWhom, deed, day(player.level().getServer())));
			l.save();
		}
	}

	public static Ledger.@Nullable PlayerRecord record(ServerPlayer player) {
		Ledger l = ledger;
		return l != null ? l.player(player.getStringUUID(), player.getGameProfile().name()) : null;
	}

	/** This printing has had its one round to the player; it now waits at the newsstands. */
	public static void markOffered(ServerPlayer player) {
		Ledger l = ledger;
		if (l != null) {
			l.player(player.getStringUUID(), player.getGameProfile().name()).lastPrintingOffered = l.latestPrinting;
		}
	}

	public static void markReceived(ServerPlayer player, int issue) {
		Ledger l = ledger;
		if (l != null) {
			Ledger.PlayerRecord record = l.player(player.getStringUUID(), player.getGameProfile().name());
			record.lastIssueReceived = Math.max(record.lastIssueReceived, issue);
			if (l.latestEdition() != null && l.latestEdition().issue() == issue) {
				record.lastPrintingReceived = Math.max(record.lastPrintingReceived, l.latestPrinting);
			}
			Newsstands.changed();
		}
	}

	/** What this player's copy costs: the paper's price, or nothing for their very first copy (if that is on). */
	public static int priceFor(ServerPlayer player) {
		ObserverConfig config = Observer.config();
		Ledger.PlayerRecord record = record(player);
		return config.firstCopyFree && record != null && record.lastIssueReceived == 0 ? 0 : config.paperPrice;
	}

	/** Whether this copy is the player's first, which comes with a word of welcome. */
	public static boolean firstCopy(ServerPlayer player) {
		Ledger.PlayerRecord record = record(player);
		return record != null && record.lastIssueReceived == 0;
	}

	/** Whether this player has the copy on sale now. */
	public static boolean collected(ServerPlayer player) {
		Ledger l = ledger;
		return l != null && l.collected(l.player(player.getStringUUID(), player.getGameProfile().name()));
	}

	/** The printing on the newsstands, or 0 before the first edition. */
	public static int printingOnSale() {
		Ledger l = ledger;
		return l != null && l.latestEdition() != null ? l.latestPrinting : 0;
	}

	/** UUIDs of everyone who has the printing on sale. */
	public static List<String> collectors() {
		Ledger l = ledger;
		if (l == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		l.players.forEach((uuid, record) -> {
			if (l.collected(record)) {
				out.add(uuid);
			}
		});
		return out;
	}

	public static void save() {
		Ledger l = ledger;
		if (l != null) {
			l.save();
		}
	}

	/** This edition with Exchange notices for {@code player}, if it was printed before they were known. */
	public static Edition withReader(Edition edition, ServerPlayer player) {
		Ledger l = ledger;
		return l != null ? l.withReader(edition, player.getStringUUID(), StatSnapshot.capture(player)) : edition;
	}

	public static @Nullable Edition latest() {
		Ledger l = ledger;
		return l != null ? l.latestEdition() : null;
	}

	public static boolean printing() {
		return printing != null;
	}

	/** After the frequency changes: the next edition is due at the start of the next period at the new length. */
	public static void reschedule(MinecraftServer server) {
		Ledger l = ledger;
		if (l != null) {
			int period = Observer.config().frequency().days;
			l.nextEditionDay = ((day(server) - 1) / period + 1) * period + 1;
			l.save();
		}
	}

	public static long nextEditionDay() {
		Ledger l = ledger;
		return l != null ? l.nextEditionDay : -1;
	}

	/** Day number as players count it: the world's first day is day 1. */
	public static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / TICKS_PER_DAY + 1;
	}

}
