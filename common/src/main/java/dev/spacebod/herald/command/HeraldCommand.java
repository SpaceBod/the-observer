package dev.spacebod.herald.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.spacebod.herald.Herald;
import dev.spacebod.herald.delivery.Delivery;
import dev.spacebod.herald.edition.Edition;
import dev.spacebod.herald.exchange.Bounties;
import dev.spacebod.herald.exchange.HandIn;
import dev.spacebod.herald.news.Newsroom;
import dev.spacebod.herald.press.ModelCatalog;
import dev.spacebod.herald.press.ModelDownload;
import dev.spacebod.herald.press.Press;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /herald}: {@code reprint} hands anyone a fresh copy of the latest edition; the rest are for operators
 * (and, for publish and status, the owner of a singleplayer world, cheats or not):
 * {@code publish} prints an edition now, {@code status} shows the schedule and the press, and
 * {@code model download <standard|quality>} fetches a writing model on a dedicated server.
 */
public final class HeraldCommand {
	private HeraldCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal("herald")
				.executes(context -> help(context.getSource()))
				.then(Commands.literal("reprint").executes(context -> reprint(context.getSource())))
				// Sent by the Exchange page's buttons.
				.then(Commands.literal("exchange").then(Commands.literal("claim")
						.then(Commands.argument("issue", IntegerArgumentType.integer(1))
								.then(Commands.argument("slot", IntegerArgumentType.integer(1, Bounties.SLOTS))
										.executes(context -> claim(context.getSource(),
												IntegerArgumentType.getInteger(context, "issue"),
												IntegerArgumentType.getInteger(context, "slot")))))))
				.then(Commands.literal("publish")
						.requires(HeraldCommand::editor)
						.executes(context -> publish(context.getSource())))
				.then(Commands.literal("status")
						.requires(HeraldCommand::editor)
						.executes(context -> status(context.getSource())));
		var download = Commands.literal("download");
		for (ModelCatalog.Choice choice : ModelCatalog.Choice.values()) {
			download.then(Commands.literal(choice.id).executes(context -> download(context.getSource(), choice)));
		}
		root.then(Commands.literal("model")
				.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(download));
		dispatcher.register(root);
	}

	/** Operators, and the owner of a singleplayer world even with cheats off: it's their paper. */
	private static boolean editor(CommandSourceStack source) {
		if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
			return true;
		}
		ServerPlayer player = source.getPlayer();
		return player != null && source.getServer().isSingleplayerOwner(player.nameAndId());
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(Newsroom.paperName(source.getServer())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("/herald reprint - a fresh copy of the latest edition"), false);
		if (editor(source)) {
			source.sendSuccess(() -> Component.literal("/herald publish - print an edition now"), false);
			source.sendSuccess(() -> Component.literal("/herald status - schedule and press"), false);
		}
		return 1;
	}

	private static int claim(CommandSourceStack source, int issue, int slot) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can hand in bounties."));
			return 0;
		}
		HandIn.Result result = HandIn.handIn(player, issue, slot);
		player.sendSystemMessage(result.message());
		return result.ok() ? 1 : 0;
	}

	private static int reprint(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		Edition latest = Newsroom.latest();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can take a copy."));
			return 0;
		}
		if (latest == null) {
			source.sendFailure(Component.literal("The Herald hasn't printed an edition yet."));
			return 0;
		}
		Delivery.hand(player, latest);
		return 1;
	}

	private static int publish(CommandSourceStack source) {
		if (!Newsroom.publish(source.getServer())) {
			source.sendFailure(Component.literal("An edition is already at the press."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Stop the presses! A new edition is being printed.").withStyle(ChatFormatting.GOLD), true);
		return 1;
	}

	private static int status(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		Edition latest = Newsroom.latest();
		long day = Newsroom.day(server);
		source.sendSuccess(() -> Component.literal(Newsroom.paperName(server)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Latest edition: " + (latest != null ? "No. " + latest.issue() + ", day " + latest.day() : "none yet")), false);
		source.sendSuccess(() -> Component.literal(Newsroom.printing() ? "At the press now."
				: "Next edition: day " + Newsroom.nextEditionDay() + " (today is day " + day + ")"), false);
		source.sendSuccess(() -> Component.literal("Writing: " + Press.status()).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int download(CommandSourceStack source, ModelCatalog.Choice choice) {
		if (ModelDownload.running()) {
			source.sendFailure(Component.literal("A model is already downloading."));
			return 0;
		}
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.literal("Downloading " + choice.label + " (" + choice.sizeText() + ") from "
				+ choice.pageUri() + " into " + Herald.modelsDir().toAbsolutePath().normalize()).withStyle(ChatFormatting.GRAY), true);
		ModelDownload.start(choice).thenAccept(progress -> server.execute(() -> {
			if (progress.error != null) {
				source.sendFailure(Component.literal("Download failed: " + progress.error));
			} else {
				source.sendSuccess(() -> Component.literal("The press is ready: the next edition will be written with " + choice.label + ".")
						.withStyle(ChatFormatting.GREEN), true);
			}
		}));
		return 1;
	}
}
