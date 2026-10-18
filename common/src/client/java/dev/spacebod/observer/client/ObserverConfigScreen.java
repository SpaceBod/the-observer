package dev.spacebod.observer.client;

import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverConfig;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.press.Press;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The mod's settings from the mods list (Mod Menu on Fabric, the Mods screen on NeoForge). Writes
 * config/observer.json, which is what worlds hosted on this computer use.
 */
public class ObserverConfigScreen extends Screen {
	private static final int TEXT_WIDTH = 300;

	private final @Nullable Screen parent;
	private List<FormattedCharSequence> note = List.of();

	public ObserverConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.spacebod_observer.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ObserverConfig config = Observer.config();
		int x = width / 2 - 150;
		int y = 48;
		addRenderableWidget(Button.builder(device(config), b -> {
			config.pressDevice = config.gpu() ? "cpu" : "gpu";
			config.save();
			b.setMessage(device(config));
			rebuildWidgets();
		}).bounds(x, y, 300, 20).build());
		addRenderableWidget(Button.builder(toggle("press", config.usePress), b -> {
			config.usePress = !config.usePress;
			config.save();
			b.setMessage(toggle("press", config.usePress));
		}).bounds(x, y + 24, 300, 20).build());
		addRenderableWidget(Button.builder(toggle("dog", config.deliveryDog), b -> {
			config.deliveryDog = !config.deliveryDog;
			config.save();
			b.setMessage(toggle("dog", config.deliveryDog));
		}).bounds(x, y + 48, 300, 20).build());
		addRenderableWidget(Button.builder(tone(config), b -> {
			Tone[] tones = Tone.values();
			config.tone = tones[(Tone.of(config.tone).ordinal() + 1) % tones.length].id;
			config.save();
			b.setMessage(tone(config));
		}).bounds(x, y + 72, 300, 20).build());
		addRenderableWidget(Button.builder(frequency(config), b -> {
			Frequency[] all = Frequency.values();
			config.frequency = all[(config.frequency().ordinal() + 1) % all.length].id;
			config.save();
			b.setMessage(frequency(config));
		}).bounds(x, y + 96, 300, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.spacebod_observer.config.models"),
				b -> ClientCompat.setScreen(minecraft, new ModelOfferScreen(this))).bounds(x, y + 120, 300, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, height - 28, 300, 20).build());

		Component status = Component.translatable("screen.spacebod_observer.config.status", Press.device());
		if (config.gpu() && "cpu".equals(Press.library())) {
			status = status.copy().append(" ").append(Component.translatable("screen.spacebod_observer.config.restart"));
		}
		note = font.split(status, TEXT_WIDTH);
	}

	private static Component device(ObserverConfig config) {
		return Component.translatable("screen.spacebod_observer.config.device",
				Component.translatable("screen.spacebod_observer.config.device." + config.pressDevice));
	}

	private static Component frequency(ObserverConfig config) {
		return Component.translatable("screen.spacebod_observer.config.frequency", config.frequency().title);
	}

	private static Component tone(ObserverConfig config) {
		return Component.translatable("screen.spacebod_observer.config.tone", Tone.of(config.tone).title);
	}

	private static Component toggle(String key, boolean on) {
		return Component.translatable("screen.spacebod_observer.config." + key, on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	@Override
	public void onClose() {
		ClientCompat.setScreen(minecraft, parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 20, 0xFFFFFFFF);
		int y = 48 + 152;
		for (FormattedCharSequence line : note) {
			g.text(font, line, width / 2 - TEXT_WIDTH / 2, y, 0xFFB0B0B0);
			y += 10;
		}
	}
}
