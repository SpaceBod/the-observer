package dev.spacebod.herald.client;

import dev.spacebod.herald.Herald;
import dev.spacebod.herald.HeraldContent;
import dev.spacebod.herald.edition.Edition;
import dev.spacebod.herald.item.NewspaperItem;
import dev.spacebod.herald.press.ModelCatalog;
import dev.spacebod.herald.press.ModelDownload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client half: the reading screen, and in singleplayer the one-time offer to install a writing model. */
public final class HeraldClient {
	/** Ticks in a world before the model offer may appear, so it doesn't greet the player mid-load. */
	private static final int OFFER_DELAY_TICKS = 200;

	private static boolean offered;
	private static int ticksInWorld;
	private static ModelDownload.Progress announced;

	private HeraldClient() {
	}

	public static void init() {
		NewspaperItem.reader = stack -> {
			Edition edition = stack.get(HeraldContent.EDITION);
			if (edition != null) {
				ClientCompat.setScreen(Minecraft.getInstance(), new NewspaperScreen(edition));
			}
		};
	}

	public static void onEndTick(Minecraft minecraft) {
		announceDownload(minecraft);
		if (minecraft.player == null) {
			ticksInWorld = 0;
			return;
		}
		ticksInWorld++;
		// Singleplayer only: the model runs wherever the world runs, and here that is this computer.
		if (!offered && ticksInWorld > OFFER_DELAY_TICKS && minecraft.hasSingleplayerServer()
				&& ClientCompat.screen(minecraft) == null && Herald.config().offerModelDownload && Herald.config().usePress
				&& ModelCatalog.installed().isEmpty() && !ModelDownload.running()) {
			offered = true;
			ClientCompat.setScreen(minecraft, new ModelOfferScreen(null));
		}
	}

	private static void announceDownload(Minecraft minecraft) {
		ModelDownload.Progress progress = ModelDownload.current();
		if (progress == null || !progress.done || progress == announced || minecraft.player == null) {
			return;
		}
		announced = progress;
		ClientCompat.chat(minecraft, progress.error == null
				? Component.translatable("message.spacebod_herald.model_ready", progress.choice.label).withStyle(ChatFormatting.GREEN)
				: Component.translatable("message.spacebod_herald.model_failed", progress.error).withStyle(ChatFormatting.RED));
	}
}
