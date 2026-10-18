package dev.spacebod.observer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.delivery.PawstmanMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Pawstman's counter: the issue he brought, his fee, the coin slot, and pay-and-take or no-thanks. */
public class PawstmanScreen extends AbstractContainerScreen<PawstmanMenu> {
	private static final int PAPER = 0xFFF1E8D2;
	private static final int PAPER_DARK = 0xFFE3D7BB;
	private static final int INK = 0xFF1E1B18;
	private static final int GREY = 0xFF7A7166;
	private static final int RULE = 0xFFB3A88F;
	private static final int ACCENT = 0xFF9A1F1F;
	private static final int EMERALD = 0xFF17A35A;
	private static final float SMALL = 0.75F;
	private static final float TINY = 0.6F;

	private static final int PAY_X = 48;
	private static final int DECLINE_X = 120;
	private static final int BUTTON_Y = 52;
	private static final int BUTTON_W = 66;
	private static final int BUTTON_H = 15;

	public PawstmanScreen(PawstmanMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PawstmanMenu.WIDTH, PawstmanMenu.HEIGHT);
	}

	private boolean over(double mx, double my, int bx) {
		return mx >= leftPos + bx && mx < leftPos + bx + BUTTON_W && my >= topPos + BUTTON_Y && my < topPos + BUTTON_Y + BUTTON_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int button = over(event.x(), event.y(), PAY_X) && menu.enoughCoins() ? PawstmanMenu.PAY
					: over(event.x(), event.y(), DECLINE_X) ? PawstmanMenu.DECLINE : -1;
			if (button >= 0) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		// Printed in extractBackground.
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		g.fill(x + 3, y + 3, x + imageWidth + 3, y + imageHeight + 3, 0x55000000);
		g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, INK);
		g.fill(x, y, x + imageWidth, y + imageHeight, PAPER);
		g.fill(x, y, x + imageWidth, y + 15, INK);
		Component title = Component.literal("THE PAWSTMAN").withStyle(ChatFormatting.BOLD);
		text(g, title, x + (imageWidth - font.width(title)) / 2F, y + 4, 1F, PAPER);

		g.pose().pushMatrix();
		g.pose().translate(x + 9, y + 20);
		g.pose().scale(1.25F, 1.25F);
		g.item(new ItemStack(ObserverContent.NEWSPAPER), 0, 0);
		g.pose().popMatrix();
		int fee = menu.fee();
		int price = menu.price();
		String headline;
		String line1;
		String line2;
		if (fee == 0 && menu.firstCopy()) {
			headline = "Welcome to The Observer!";
			line1 = "Issue No. " + menu.issue() + " is on the house. After this,";
			line2 = price == 0 ? "the paper stays free." : "a copy costs " + price + (price == 1 ? " emerald." : " emeralds.");
		} else if (fee == 0) {
			headline = "Issue No. " + menu.issue() + " has arrived!";
			line1 = "Price: free. Not today? He'll take it back,";
			line2 = "and you can pick it up at a newsstand.";
		} else {
			headline = "Issue No. " + menu.issue() + " has arrived!";
			line1 = "Price: " + fee + (fee == 1 ? " emerald." : " emeralds.") + " Not today? He'll take it";
			line2 = "back, and you can buy it at a newsstand.";
		}
		text(g, Component.literal(headline).withStyle(ChatFormatting.BOLD), x + 34, y + 21, SMALL, INK);
		text(g, Component.literal(line1).withStyle(ChatFormatting.ITALIC), x + 34, y + 30, TINY, GREY);
		text(g, Component.literal(line2).withStyle(ChatFormatting.ITALIC), x + 34, y + 37, TINY, GREY);
		for (int d = 8; d < imageWidth - 8; d += 2) {
			g.fill(x + d, y + 45, x + d + 1, y + 46, RULE);
		}

		// The coin slot, when there is something to pay.
		if (fee > 0) {
		int sx = x + PawstmanMenu.COIN_X - 1;
		int sy = y + PawstmanMenu.COIN_Y - 1;
		g.fill(sx - 2, sy - 2, sx + 20, sy + 20, EMERALD);
		g.fill(sx - 1, sy - 1, sx + 19, sy + 19, INK);
		g.fill(sx, sy, sx + 18, sy + 18, 0xFF3A332C);
		if (!menu.getSlot(0).hasItem()) {
			ItemStack wanted = new ItemStack(Items.EMERALD);
			g.item(wanted, sx + 1, sy + 1);
			g.fill(sx, sy, sx + 18, sy + 18, 0xB03A332C);
			g.itemDecorations(font, wanted, sx + 1, sy + 1, String.valueOf(fee));
		}
		}
		button(g, x + PAY_X, Component.literal(fee == 0 ? "TAKE IT" : "PAY & TAKE"), menu.enoughCoins(), over(mouseX, mouseY, PAY_X), ACCENT);
		button(g, x + DECLINE_X, Component.literal("NO THANKS"), true, over(mouseX, mouseY, DECLINE_X), PAPER_DARK);

		text(g, Component.literal("YOUR POCKETS").withStyle(ChatFormatting.BOLD), x + PawstmanMenu.INVENTORY_X, y + PawstmanMenu.INVENTORY_Y - 9, TINY, GREY);
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 9; col++) {
				int slotY = row < 3 ? PawstmanMenu.INVENTORY_Y + row * 18 : PawstmanMenu.INVENTORY_Y + 58;
				int px = x + PawstmanMenu.INVENTORY_X + col * 18 - 1;
				int py = y + slotY - 1;
				g.fill(px, py, px + 18, py + 18, RULE);
				g.fill(px + 1, py + 1, px + 17, py + 17, PAPER_DARK);
				g.fill(px + 1, py + 1, px + 17, py + 2, 0x22000000);
				g.fill(px + 1, py + 1, px + 2, py + 17, 0x22000000);
			}
		}
	}

	private void button(GuiGraphicsExtractor g, int bx, Component label, boolean enabled, boolean hover, int fill) {
		int by = topPos + BUTTON_Y;
		int ink;
		int face;
		if (!enabled) {
			face = PAPER;
			ink = 0xFFA79C88;
		} else if (hover) {
			face = INK;
			ink = PAPER;
		} else {
			face = fill;
			ink = fill == ACCENT ? PAPER : INK;
		}
		g.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, enabled ? INK : 0xFFC2B79F);
		g.fill(bx + 1, by + 1, bx + BUTTON_W - 1, by + BUTTON_H - 1, face);
		Component bold = label.copy().withStyle(ChatFormatting.BOLD);
		float scale = Math.min(SMALL, (BUTTON_W - 6) / (float) font.width(bold));
		text(g, bold, bx + (BUTTON_W - font.width(bold) * scale) / 2F, by + (BUTTON_H - 7 * scale) / 2F, scale, ink);
	}

	private void text(GuiGraphicsExtractor g, Component text, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}
}
