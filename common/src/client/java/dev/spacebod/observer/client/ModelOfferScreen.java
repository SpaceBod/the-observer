package dev.spacebod.observer.client;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.press.ModelCatalog;
import dev.spacebod.observer.press.ModelDownload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Asked once, in singleplayer: would you like the Observer written by a small model on this computer? Offers the
 * two catalogue models (the one suited to this machine first), a manual route (open the folder, visit the model
 * page) and a way to never be asked again. A download keeps going if the screen is closed.
 */
public class ModelOfferScreen extends Screen {
	private static final int TEXT_WIDTH = 300;

	private final @Nullable Screen parent;
	private List<FormattedCharSequence> lines = List.of();

	public ModelOfferScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.spacebod_observer.offer.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ModelCatalog.Choice recommended = ModelCatalog.recommended();
		lines = font.split(Component.translatable("screen.spacebod_observer.offer.body", recommended.label, recommended.sizeText()), TEXT_WIDTH);

		int x = width / 2 - 150;
		// Below the text and the progress line.
		int y = 36 + lines.size() * 10 + 34;
		if (!ModelDownload.running()) {
			ModelCatalog.Choice other = recommended == ModelCatalog.Choice.STANDARD ? ModelCatalog.Choice.QUALITY : ModelCatalog.Choice.STANDARD;
			addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.offer.download_recommended", recommended.label, recommended.sizeText())
					.withStyle(ChatFormatting.BOLD), b -> download(recommended)).bounds(x, y, 300, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.offer.download", other.label, other.sizeText()),
					b -> download(other)).bounds(x, y + 24, 300, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.offer.folder"), b -> openFolder())
				.bounds(x, y + 52, 148, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.offer.page"),
				b -> ConfirmLinkScreen.confirmLinkNow(this, recommended.pageUri())).bounds(x + 152, y + 52, 148, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.offer.later"), b -> onClose())
				.bounds(x, y + 80, 148, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.offer.never"), b -> {
			Observer.config().offerModelDownload = false;
			Observer.config().save();
			onClose();
		}).bounds(x + 152, y + 80, 148, 20).build());
	}

	private void download(ModelCatalog.Choice choice) {
		ModelDownload.start(choice);
		rebuildWidgets();
	}

	private void openFolder() {
		try {
			Files.createDirectories(Observer.modelsDir());
			ClientCompat.openFolder(Observer.modelsDir());
		} catch (IOException e) {
			Observer.LOGGER.warn("Could not open {}", Observer.modelsDir(), e);
		}
	}

	@Override
	public void onClose() {
		ClientCompat.setScreen(minecraft, parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 20, 0xFFFFFFFF);
		int y = 36;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, width / 2 - TEXT_WIDTH / 2, y, 0xFFD0D0D0);
			y += 10;
		}
		ModelDownload.Progress progress = ModelDownload.current();
		if (progress != null) {
			int barY = y + 6;
			int left = width / 2 - 150;
			g.fill(left, barY, left + 300, barY + 6, 0xFF303030);
			g.fill(left, barY, left + Math.round(300 * progress.fraction()), barY + 6, progress.error != null ? 0xFFB03030 : 0xFF4CAF50);
			Component status = progress.error != null
					? Component.translatable("message.spacebod_observer.model_failed", progress.error)
					: progress.done
					? Component.translatable("message.spacebod_observer.model_ready", progress.choice.label)
					: Component.translatable("screen.spacebod_observer.offer.progress", progress.choice.label, Math.round(progress.fraction() * 100));
			g.centeredText(font, status, width / 2, barY + 10, 0xFFFFFFFF);
		}
	}
}
