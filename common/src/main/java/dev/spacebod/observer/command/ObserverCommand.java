package dev.spacebod.observer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.Rewards;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.stand.Newsstands;
import dev.spacebod.observer.delivery.Delivery;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.exchange.Bounties;
import dev.spacebod.observer.exchange.HandIn;
import dev.spacebod.observer.news.Newsroom;
import dev.spacebod.observer.press.ModelCatalog;
import dev.spacebod.observer.press.ModelDownload;
import dev.spacebod.observer.press.Press;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /observer}. Everything but the help is for operators and the owner of a singleplayer world, cheats or
 * not: {@code publish} prints an edition now, {@code reprint} hands them a free copy, {@code status} shows the
 * schedule and the press, {@code tone} and {@code dogs} set the paper's voice and whether delivery dogs run, and
 * {@code model download <standard|quality>} (admins) fetches a writing model on a dedicated server. The exchange
 * subcommand is sent by the paper's own buttons.
 */
public final class ObserverCommand {
	private ObserverCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal("observer")
				.executes(context -> help(context.getSource()))
				.then(Commands.literal("reprint")
						.requires(ObserverCommand::editor)
						.executes(context -> reprint(context.getSource())))
				// Sent by the Exchange page's buttons.
				.then(Commands.literal("exchange").then(Commands.literal("claim")
						.then(Commands.argument("issue", IntegerArgumentType.integer(1))
								.then(Commands.argument("slot", IntegerArgumentType.integer(1, Bounties.SLOTS))
										.executes(context -> claim(context.getSource(),
												IntegerArgumentType.getInteger(context, "issue"),
												IntegerArgumentType.getInteger(context, "slot")))))))
				.then(Commands.literal("publish")
						.requires(ObserverCommand::editor)
						.executes(context -> publish(context.getSource())))
				.then(Commands.literal("status")
						.requires(ObserverCommand::editor)
						.executes(context -> status(context.getSource())))
				.then(Commands.literal("autopublish")
						.requires(ObserverCommand::editor)
						.then(Commands.literal("on").executes(context -> {
							Newsstands.setAutoPublish(context.getSource().getServer(), true);
							return 1;
						}))
						.then(Commands.literal("off").executes(context -> {
							Newsstands.setAutoPublish(context.getSource().getServer(), false);
							return 1;
						})))
				.then(Commands.literal("dogs")
						.requires(ObserverCommand::editor)
						.then(Commands.literal("on").executes(context -> dogs(context.getSource(), true)))
						.then(Commands.literal("off").executes(context -> dogs(context.getSource(), false))));
		var tone = Commands.literal("tone").requires(ObserverCommand::editor);
		for (Tone choice : Tone.values()) {
			tone.then(Commands.literal(choice.id).executes(context -> {
				Newsstands.setTone(context.getSource().getServer(), choice);
				return 1;
			}));
		}
		root.then(tone);
		var frequency = Commands.literal("frequency").requires(ObserverCommand::editor);
		for (Frequency choice : Frequency.values()) {
			frequency.then(Commands.literal(choice.id).executes(context -> {
				Newsstands.setFrequency(context.getSource().getServer(), choice);
				return 1;
			}));
		}
		root.then(frequency);
		var rewards = Commands.literal("rewards").requires(ObserverCommand::editor);
		for (Rewards choice : Rewards.values()) {
			rewards.then(Commands.literal(choice.id).executes(context -> {
				Newsstands.setRewards(context.getSource().getServer(), choice);
				return 1;
			}));
		}
		root.then(rewards);
		root.then(Commands.literal("firstcopy").requires(ObserverCommand::editor)
				.then(Commands.literal("free").executes(context -> {
					Newsstands.setFirstCopyFree(context.getSource().getServer(), true);
					return 1;
				}))
				.then(Commands.literal("paid").executes(context -> {
					Newsstands.setFirstCopyFree(context.getSource().getServer(), false);
					return 1;
				})));
		root.then(Commands.literal("price").requires(ObserverCommand::editor)
				.then(Commands.argument("emeralds", IntegerArgumentType.integer(0, 64)).executes(context -> {
					Newsstands.setPrice(context.getSource().getServer(), IntegerArgumentType.getInteger(context, "emeralds"));
					return 1;
				})));
		var download = Commands.literal("download");
		for (ModelCatalog.Choice choice : ModelCatalog.Choice.values()) {
			download.then(Commands.literal(choice.id).executes(context -> download(context.getSource(), choice)));
		}
		root.then(Commands.literal("model")
				.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(download));
		if (Observer.development()) {
			root.then(Commands.literal("demo").requires(ObserverCommand::editor).executes(context -> {
				if (!Newsroom.demo(context.getSource().getServer())) {
					context.getSource().sendFailure(Component.literal("An edition is already at the press."));
					return 0;
				}
				context.getSource().sendSuccess(() -> Component.literal("The demonstration town has moved in. Stop the presses!").withStyle(ChatFormatting.GOLD), true);
				return 1;
			}));
		}
		dispatcher.register(root);
	}

	/** Operators, and the owner of a singleplayer world even with cheats off: it's their paper. */
	public static boolean editor(CommandSourceStack source) {
		if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
			return true;
		}
		ServerPlayer player = source.getPlayer();
		return player != null && source.getServer().isSingleplayerOwner(player.nameAndId());
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(Newsroom.PAPER).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Collect your copy at a newsstand, or have a delivery dog bring it."), false);
		if (editor(source)) {
			source.sendSuccess(() -> Component.literal("/observer publish - print an edition now"), false);
			source.sendSuccess(() -> Component.literal("/observer reprint - a free copy of the latest edition"), false);
			source.sendSuccess(() -> Component.literal("/observer status - schedule and press"), false);
			source.sendSuccess(() -> Component.literal("/observer tone <broadsheet|roast|chronicle> - the paper's voice"), false);
			source.sendSuccess(() -> Component.literal("/observer dogs <on|off> - allow the Pawstman"), false);
			source.sendSuccess(() -> Component.literal("/observer price <emeralds> - what each reader's copy costs (0: free)"), false);
			source.sendSuccess(() -> Component.literal("/observer firstcopy <free|paid> - whether each player's first copy is on the house"), false);
			source.sendSuccess(() -> Component.literal("/observer rewards <low|balanced|high> - the Exchange's rewards from the next edition"), false);
			source.sendSuccess(() -> Component.literal("/observer frequency <daily|weekly|fortnightly> - how often the paper comes out"), false);
			source.sendSuccess(() -> Component.literal("/observer autopublish <on|off> - publish on schedule, or only on request"), false);
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
			source.sendFailure(Component.literal("The Observer hasn't printed an edition yet."));
			return 0;
		}
		Delivery.hand(player, latest);
		return 1;
	}

	private static int dogs(CommandSourceStack source, boolean on) {
		Newsstands.setDogDelivery(source.getServer(), on);
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
		source.sendSuccess(() -> Component.literal(Newsroom.PAPER).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
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
		if (choice.installed()) {
			source.sendSuccess(() -> Component.literal(choice.label + " is already installed.").withStyle(ChatFormatting.GREEN), false);
			return 1;
		}
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.literal("Downloading " + choice.label + " (" + choice.sizeText() + ") from "
				+ choice.pageUri() + " into " + Observer.modelsDir().toAbsolutePath().normalize()).withStyle(ChatFormatting.GRAY), true);
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
