package dev.spacebod.observer.client;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.item.NewspaperItem;
import dev.spacebod.observer.press.ModelCatalog;
import dev.spacebod.observer.press.ModelDownload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client half: the reading screen, and in singleplayer the one-time offer to install a writing model. */
public final class ObserverClient {
	/** Ticks in a world before the model offer may appear, so it doesn't greet the player mid-load. */
	private static final int OFFER_DELAY_TICKS = 200;

	private static boolean offered;
	private static int ticksInWorld;
	private static ModelDownload.Progress announced;

	private ObserverClient() {
	}

	public static void init() {
		NewspaperItem.reader = stack -> {
			Edition edition = stack.get(ObserverContent.EDITION);
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
				&& ClientCompat.screen(minecraft) == null && Observer.config().offerModelDownload && Observer.config().usePress
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
				? Component.translatable("message.spacebod_observer.model_ready", progress.choice.label).withStyle(ChatFormatting.GREEN)
				: Component.translatable("message.spacebod_observer.model_failed", progress.error).withStyle(ChatFormatting.RED));
	}
}
