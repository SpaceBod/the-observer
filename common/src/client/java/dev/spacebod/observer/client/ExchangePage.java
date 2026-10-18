package dev.spacebod.observer.client;

import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.exchange.Bounties;
import dev.spacebod.observer.exchange.HandIn;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * The Exchange pages, drawn live for whoever is reading: their notices, worked out from the edition and their
 * UUID ({@link Bounties}), with progress from their own inventory and kill statistics. A notice's button runs
 * {@code /observer exchange claim}, which the server checks; it shows as paid once the server has marked this
 * player's copy.
 */
final class ExchangePage {
	private static final float CARD_GAP = 3;
	private static final float CARD_H = 54;
	private static final float BUTTON_W = 30;
	private static final float BUTTON_H = 9;
	private static final float SMALL = NewspaperLayout.SMALL;
	private static final float BODY = NewspaperLayout.BODY;
	private static final int GREEN = 0xFF2E7D32;
	/** Notices per page; the Exchange has two pages. */
	static final int PER_PAGE = 3;

	private final Edition edition;
	private final List<Bounties.Bounty> bounties;
	private final Map<String, Integer> baselines;
	private final Font font;

	ExchangePage(Edition edition, LocalPlayer player, Font font) {
		this.edition = edition;
		this.font = font;
		this.bounties = Bounties.forPlayer(edition.exchange(), player.getUUID());
		this.baselines = edition.exchange().baselines().getOrDefault(player.getStringUUID(), Map.of());
		// Kill counts come from the server's statistics; ask for a fresh copy, as the vanilla stats screen does.
		if (Minecraft.getInstance().getConnection() != null) {
			Minecraft.getInstance().getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
		}
	}

	/**
	 * A click on one of the Exchange's pages, in paper units relative to that page's text block.
	 *
	 * @param half 0 for the first Exchange page, 1 for the second
	 * @return whether it pressed a button
	 */
	boolean click(int half, float px, float py) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		for (int i = half * PER_PAGE; i < Math.min(bounties.size(), (half + 1) * PER_PAGE); i++) {
			Bounties.Bounty bounty = bounties.get(i);
			if (inButton(i, px, py) && ready(player, bounty)) {
				player.connection.sendCommand("observer exchange claim " + edition.issue() + " " + bounty.slot());
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
				return true;
			}
		}
		return false;
	}

	/**
	 * Draws one of the Exchange's pages in paper units; the caller has already applied the paper's transform.
	 *
	 * @param half 0 for the first page (notices 1-3), 1 for the second (4-6)
	 * @param left the page's text block left edge
	 * @param mx   mouse position in the same units, relative to {@code left} and the page top
	 */
	void draw(GuiGraphicsExtractor g, int half, float left, float mx, float my) {
		LocalPlayer player = Minecraft.getInstance().player;
		float width = NewspaperLayout.TEXT_W;
		float y = NewspaperLayout.contentTop();

		Component title = Component.literal(half == 0 ? "THE EXCHANGE" : "MORE NOTICES").withStyle(ChatFormatting.BOLD);
		float titleScale = fit(title, width, 1.4f);
		text(g, title, left + (width - font.width(title) * titleScale) / 2, y, titleScale, NewspaperLayout.INK);
		y += 9 * titleScale + 2;
		Component sub = Component.translatable("screen.spacebod_observer.exchange.subtitle", edition.exchange().closesDay())
				.withStyle(ChatFormatting.ITALIC);
		for (FormattedCharSequence line : font.split(sub, Math.round(width / SMALL))) {
			line(g, line, left + (width - font.width(line) * SMALL) / 2, y, SMALL, NewspaperLayout.GREY);
			y += font.lineHeight * SMALL;
		}
		y += 1;
		fill(g, left, y, left + width, y + 0.5f, NewspaperLayout.INK);

		if (player == null || bounties.isEmpty()) {
			if (half == 0) {
				wrap(g, Component.translatable("screen.spacebod_observer.exchange.none"), left, y + 8, width, BODY, 6, NewspaperLayout.INK);
			}
			return;
		}
		for (int i = half * PER_PAGE; i < Math.min(bounties.size(), (half + 1) * PER_PAGE); i++) {
			card(g, player, i, left, width, mx, my);
		}
	}

	private void card(GuiGraphicsExtractor g, LocalPlayer player, int index, float left, float width, float mx, float my) {
		Bounties.Bounty bounty = bounties.get(index);
		float top = cardTop(index);
		fill(g, left, top, left + width, top + 0.5f, NewspaperLayout.RULE);
		fill(g, left, top + CARD_H, left + width, top + CARD_H + 0.5f, NewspaperLayout.RULE);
		fill(g, left, top, left + 0.5f, top + CARD_H, NewspaperLayout.RULE);
		fill(g, left + width - 0.5f, top, left + width, top + CARD_H, NewspaperLayout.RULE);

		float x = left + 3;
		float inner = width - 6;
		float y = top + 3;

		// Kind on the left, reward on the right; the reward may take at most half the line.
		Component kind = Component.literal(bounty.kind() == Bounties.Kind.DELIVER ? "WANTED" : "BOUNTY").withStyle(ChatFormatting.BOLD);
		text(g, kind, x, y, SMALL, NewspaperLayout.ACCENT);
		Item rewardItem = BuiltInRegistries.ITEM.getValue(bounty.reward());
		Component reward = Component.literal(Bounties.rewardText(bounty));
		float rewardScale = fit(reward, inner / 2 - 8, SMALL);
		float rewardWidth = font.width(reward) * rewardScale;
		text(g, reward, x + inner - rewardWidth, y, rewardScale, NewspaperLayout.INK);
		ItemStack rewardIcon = new ItemStack(rewardItem);
		if (!bounty.rewardEnchantment().isEmpty()) {
			rewardIcon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		icon(g, rewardIcon, x + inner - rewardWidth - 8, y - 1.5f, 0.45f);
		y += 7;

		icon(g, targetIcon(bounty), x, y, 0.75f);
		Component headline = Component.literal(bounty.kind() == Bounties.Kind.DELIVER
				? bounty.count() + " " + name(BuiltInRegistries.ITEM.getValue(bounty.target()))
				: "Deal with " + bounty.count() + " " + creature(bounty)).withStyle(ChatFormatting.BOLD);
		text(g, headline, x + 15, y + 2, fit(headline, inner - 15, 0.85f), NewspaperLayout.INK);
		y += 14;

		// The quote gets two lines, shrinking if it needs to; the signature one.
		y = wrap(g, Component.literal("“" + bounty.note() + "”").withStyle(ChatFormatting.ITALIC), x, y, inner, BODY, 2, NewspaperLayout.INK);
		Component signature = Component.literal("— " + bounty.signature());
		text(g, signature, x, y, fit(signature, inner, SMALL), NewspaperLayout.GREY);

		// Foot of the card: progress on the left, the button on the right.
		float footY = top + CARD_H - BUTTON_H - 2;
		boolean paid = paid(player, bounty);
		if (paid) {
			Component stamp = Component.translatable("screen.spacebod_observer.exchange.paid").withStyle(ChatFormatting.BOLD);
			text(g, stamp, x, footY + 1, fit(stamp, inner, 0.9f), GREEN);
			return;
		}
		int have = Math.min(progress(player, bounty), bounty.count());
		Component status = bounty.kind() == Bounties.Kind.DELIVER
				? Component.translatable("screen.spacebod_observer.exchange.have", have, bounty.count())
				: Component.translatable("screen.spacebod_observer.exchange.slain", have, bounty.count());
		float statusScale = fit(status, inner - BUTTON_W - 30, SMALL);
		text(g, status, x, footY + 2, statusScale, NewspaperLayout.INK);
		float barX = x + font.width(status) * statusScale + 3;
		float barW = Math.max(6, inner - BUTTON_W - 4 - (barX - x));
		fill(g, barX, footY + 3, barX + barW, footY + 6, 0xFFD8CCAD);
		fill(g, barX, footY + 3, barX + barW * have / bounty.count(), footY + 6, have >= bounty.count() ? GREEN : NewspaperLayout.ACCENT);

		// The button, printed on the paper: ink when ready, faded when not, highlighted under the mouse.
		boolean ready = have >= bounty.count();
		float bx = x + inner - BUTTON_W;
		boolean hover = ready && mx >= bx && mx <= bx + BUTTON_W && my >= footY && my <= footY + BUTTON_H;
		int frame = ready ? NewspaperLayout.INK : 0xFFB5A98C;
		fill(g, bx, footY, bx + BUTTON_W, footY + BUTTON_H, hover ? NewspaperLayout.ACCENT : ready ? NewspaperLayout.INK : 0xFFE4D9BC);
		fill(g, bx, footY, bx + BUTTON_W, footY + 0.5f, frame);
		fill(g, bx, footY + BUTTON_H - 0.5f, bx + BUTTON_W, footY + BUTTON_H, frame);
		fill(g, bx, footY, bx + 0.5f, footY + BUTTON_H, frame);
		fill(g, bx + BUTTON_W - 0.5f, footY, bx + BUTTON_W, footY + BUTTON_H, frame);
		Component label = Component.translatable(bounty.kind() == Bounties.Kind.DELIVER
				? "screen.spacebod_observer.exchange.hand_in" : "screen.spacebod_observer.exchange.claim").withStyle(ChatFormatting.BOLD);
		float labelScale = fit(label, BUTTON_W - 3, SMALL);
		text(g, label, bx + (BUTTON_W - font.width(label) * labelScale) / 2, footY + (BUTTON_H - 9 * labelScale) / 2 + 0.5f, labelScale,
				ready ? NewspaperLayout.PAPER : 0xFF9C9178);
	}

	private boolean inButton(int index, float px, float py) {
		float footY = cardTop(index) + CARD_H - BUTTON_H - 2;
		float bx = 3 + NewspaperLayout.TEXT_W - 6 - BUTTON_W;
		return px >= bx && px <= bx + BUTTON_W && py >= footY && py <= footY + BUTTON_H;
	}

	private boolean ready(LocalPlayer player, Bounties.Bounty bounty) {
		return !paid(player, bounty) && progress(player, bounty) >= bounty.count();
	}

	private boolean paid(LocalPlayer player, Bounties.Bounty bounty) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			Edition copy = stack.get(ObserverContent.EDITION);
			if (copy != null && copy.issue() == edition.issue() && stack.getOrDefault(ObserverContent.HANDED_IN, List.of()).contains(bounty.slot())) {
				return true;
			}
		}
		return false;
	}

	private int progress(LocalPlayer player, Bounties.Bounty bounty) {
		if (bounty.kind() == Bounties.Kind.DELIVER) {
			return HandIn.count(player.getInventory(), BuiltInRegistries.ITEM.getValue(bounty.target()));
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(bounty.target());
		int now = player.getStats().getValue(Stats.ENTITY_KILLED, type);
		return Math.max(0, now - baselines.getOrDefault(bounty.statKey(), now));
	}

	private static ItemStack targetIcon(Bounties.Bounty bounty) {
		return bounty.kind() == Bounties.Kind.DELIVER
				? new ItemStack(BuiltInRegistries.ITEM.getValue(bounty.target()))
				: new ItemStack(Items.IRON_SWORD);
	}

	private static String name(Item item) {
		return Component.translatable(item.getDescriptionId()).getString();
	}

	private static String creature(Bounties.Bounty bounty) {
		return Bounties.plural(Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(bounty.target()).getDescriptionId()).getString(), bounty.count());
	}

	/** Top of a notice on its page: three to a page, under the page's heading. */
	private float cardTop(int index) {
		return NewspaperLayout.contentTop() + 25 + (index % PER_PAGE) * (CARD_H + CARD_GAP);
	}

	// Drawing helpers, in paper units

	private float fit(Component text, float width, float scale) {
		float natural = font.width(text);
		return natural * scale <= width ? scale : Math.max(0.35f, width / Math.max(1, natural));
	}

	private static void icon(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	/** Wraps to {@code width} in at most {@code maxLines} lines, shrinking the text until it does. */
	private float wrap(GuiGraphicsExtractor g, Component text, float x, float y, float width, float scale, int maxLines, int color) {
		float s = scale;
		List<FormattedCharSequence> lines = font.split(text, Math.round(width / s));
		while (lines.size() > maxLines && s > 0.4f) {
			s -= 0.04f;
			lines = font.split(text, Math.round(width / s));
		}
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			line(g, lines.get(i), x, y, s, color);
			y += font.lineHeight * s;
		}
		return y;
	}

	private void text(GuiGraphicsExtractor g, Component text, float x, float y, float scale, int color) {
		line(g, text.getVisualOrderText(), x, y, scale, color);
	}

	private void line(GuiGraphicsExtractor g, FormattedCharSequence line, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, line, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private static void fill(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale(x1 - x0, y1 - y0);
		g.fill(0, 0, 1, 1, color);
		g.pose().popMatrix();
	}
}
