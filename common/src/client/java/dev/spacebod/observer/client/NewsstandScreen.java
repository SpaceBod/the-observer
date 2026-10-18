package dev.spacebod.observer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.Rewards;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.stand.NewsstandMenu;
import dev.spacebod.observer.stand.Newsstands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The newsstand's counter, printed like a page of the paper: the status lamp and the countdown to the next
 * edition, the reader's copy (bought with emeralds dropped in the coin slot), how they want it
 * delivered and the paper's voice. Operators also get a small editor's desk beside the page.
 */
public class NewsstandScreen extends AbstractContainerScreen<NewsstandMenu> {
	private static final int PAPER = 0xFFF1E8D2;
	private static final int PAPER_DARK = 0xFFE3D7BB;
	private static final int INK = 0xFF1E1B18;
	private static final int GREY = 0xFF7A7166;
	private static final int RULE = 0xFFB3A88F;
	private static final int ACCENT = 0xFF9A1F1F;
	private static final int EMERALD = 0xFF17A35A;
	private static final int AMBER = 0xFFE0A030;
	private static final float SMALL = 0.75F;
	private static final float TINY = 0.6F;

	// The copy card and its action row (the coin slot sits at the row's start; see NewsstandMenu.COIN_X/Y).
	private static final int CARD_TOP = 86;
	private static final int CARD_BOTTOM = 127;
	private static final int ACTION_X = 48;
	private static final int ACTION_Y = 102;
	private static final int ACTION_H = 20;
	// The delivery switch.
	private static final int SWITCH_X = 80;
	private static final int SWITCH_Y = 135;
	private static final int SWITCH_W = 152;

	// The editor's desk: a narrow card beside the page.
	private static final int DESK_W = 92;
	private static final int DESK_GAP = 6;
	private static final int DESK_BAR = 14;
	/** Whether the editor's desk is open; closed to begin with, then as the operator left it until the game closes. */
	private static boolean deskOpen;

	private record Button(int id, int x, int y, int w, int h, Component label, boolean enabled, boolean selected, Component hint) {
		boolean over(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public NewsstandScreen(NewsstandMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, NewsstandMenu.WIDTH, NewsstandMenu.HEIGHT);
	}

	private int status() {
		return menu.get(NewsstandMenu.STATUS);
	}

	private boolean editor() {
		return menu.get(NewsstandMenu.EDITOR) == 1;
	}

	private boolean onSale() {
		return menu.onSale();
	}

	private Tone tone() {
		return Tone.values()[Mth.clamp(menu.get(NewsstandMenu.TONE), 0, Tone.values().length - 1)];
	}

	/** Where the desk goes: right of the page, or left of it if the window is too narrow. */
	private int deskX() {
		int right = leftPos + imageWidth + DESK_GAP;
		return right + DESK_W <= width - 2 ? right : Math.max(2, leftPos - DESK_GAP - DESK_W);
	}

	private int deskY() {
		return topPos;
	}

	private boolean overDeskBar(double mx, double my) {
		return mx >= deskX() && mx < deskX() + DESK_W && my >= deskY() && my < deskY() + DESK_BAR;
	}

	/** The screen's buttons for the menu's current data, in screen coordinates. */
	private List<Button> buttons() {
		List<Button> out = new ArrayList<>();
		int x = leftPos;
		int y = topPos;
		boolean collected = menu.get(NewsstandMenu.COLLECTED) == 1;
		int owed = menu.get(NewsstandMenu.OWED);
		if (onSale() && !collected && owed > 0) {
			out.add(new Button(NewsstandMenu.TAKE, x + ACTION_X + 22, y + ACTION_Y, 92, ACTION_H, Component.literal("BUY YOUR COPY"), menu.coins() >= owed, false,
					Component.literal("Put " + owed + (owed == 1 ? " emerald" : " emeralds") + " in the coin slot for your copy")));
		} else if (onSale() && !collected) {
			out.add(new Button(NewsstandMenu.TAKE, x + ACTION_X, y + ACTION_Y, 114, ACTION_H, Component.literal("TAKE YOUR COPY"), true, false,
					Component.literal(menu.get(NewsstandMenu.FEE) > 0 ? "Your first copy is on the house" : "The Observer is free on this server")));
		} else if (onSale()) {
			int price = menu.get(NewsstandMenu.PRICE);
			int bx = price > 0 ? x + ACTION_X + 22 : x + ACTION_X;
			out.add(new Button(NewsstandMenu.BUY, bx, y + ACTION_Y, 92, ACTION_H, Component.literal("BUY ANOTHER"), menu.coins() >= price, false,
					Component.literal(price == 0 ? "Spare copies are free here" : "Put " + price + (price == 1 ? " emerald" : " emeralds") + " in the coin slot")));
		}
		boolean dogs = menu.get(NewsstandMenu.DOGS_ALLOWED) == 1;
		boolean atStand = menu.get(NewsstandMenu.AT_STAND) == 1;
		int fee = menu.get(NewsstandMenu.FEE);
		out.add(new Button(NewsstandMenu.DELIVER_DOG, x + SWITCH_X, y + SWITCH_Y, SWITCH_W / 2, 16, Component.literal("PAWSTMAN"), dogs, !atStand && dogs,
				Component.literal(!dogs ? "The Pawstman is off duty on this server"
						: "The Pawstman brings each edition to you, and you pay him at the door")));
		out.add(new Button(NewsstandMenu.DELIVER_STAND, x + SWITCH_X + SWITCH_W / 2 - 1, y + SWITCH_Y, SWITCH_W / 2 + 1, 16, Component.literal("COLLECT HERE"), true, atStand || !dogs,
				Component.literal("Pick up each edition at any newsstand")));
		if (editor() && deskOpen) {
			int dx = deskX() + 6;
			int dy = deskY();
			Tone[] tones = Tone.values();
			for (int i = 0; i < tones.length; i++) {
				out.add(new Button(NewsstandMenu.TONE_FIRST + i, dx, dy + 24 + i * 13, DESK_W - 12, 11, Component.literal(tones[i].title.toUpperCase(Locale.ROOT)),
						true, i == tone().ordinal(), Component.literal(tones[i].description)));
			}
			Frequency[] frequencies = Frequency.values();
			int current = menu.get(NewsstandMenu.FREQUENCY);
			for (int i = 0; i < frequencies.length; i++) {
				Frequency f = frequencies[i];
				out.add(new Button(NewsstandMenu.FREQUENCY_FIRST + i, dx, dy + 76 + i * 13, DESK_W - 12, 11, Component.literal(f.title.toUpperCase(Locale.ROOT)),
						true, i == current, Component.literal("An edition every " + (f.days == 1 ? "day" : f.days + " days") + ", at sunrise")));
			}
			boolean auto = menu.get(NewsstandMenu.AUTO) == 1;
			out.add(new Button(NewsstandMenu.AUTO_TOGGLE, dx, dy + 116, DESK_W - 12, 11, Component.literal(auto ? "AUTO-PUBLISH: ON" : "AUTO-PUBLISH: OFF"), true, false,
					Component.literal(auto ? "Pause the schedule: editions only when you publish one" : "Publish on schedule again")));
			Rewards[] levels = Rewards.values();
			int level = menu.get(NewsstandMenu.REWARDS);
			for (int i = 0; i < levels.length; i++) {
				out.add(new Button(NewsstandMenu.REWARDS_FIRST + i, dx, dy + 142 + i * 13, DESK_W - 12, 11, Component.literal(levels[i].title.toUpperCase(Locale.ROOT)),
						true, i == level, Component.literal("The Exchange's rewards from the next edition: " + levels[i].title.toLowerCase(Locale.ROOT))));
			}
			int half = (DESK_W - 12 - 2) / 2;
			out.add(new Button(NewsstandMenu.DOGS_TOGGLE, dx, dy + 194, half, 11, Component.literal(dogs ? "ON DUTY" : "OFF DUTY"), true, false,
					Component.literal(dogs ? "Send the Pawstman off duty: everyone collects at a newsstand" : "Put the Pawstman back on his rounds")));
			out.add(new Button(NewsstandMenu.FEE_CYCLE, dx + half + 2, dy + 194, half, 11, Component.literal(fee == 0 ? "PRICE: FREE" : "PRICE: " + fee), true, false,
					Component.literal("What each reader's copy costs, by Pawstman or at a stand, in emeralds (click to change)")));
			boolean firstFree = menu.get(NewsstandMenu.FIRST_FREE) == 1;
			out.add(new Button(NewsstandMenu.FIRST_FREE_TOGGLE, dx, dy + 207, DESK_W - 12, 11, Component.literal(firstFree ? "FIRST COPY: FREE" : "FIRST COPY: PAID"),
					true, false, Component.literal(firstFree ? "Each player's first copy is on the house" : "Every copy costs the price, the first one too")));
			out.add(new Button(NewsstandMenu.PUBLISH, dx, dy + imageHeight - 22, DESK_W - 12, 13, Component.literal("PUBLISH NOW"), status() != Newsstands.Status.AT_PRESS.ordinal(),
					false, Component.literal("Close the period and send it to press")));
		}
		return out;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && editor() && overDeskBar(event.x(), event.y())) {
			deskOpen = !deskOpen;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1F));
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (Button button : buttons()) {
				if (button.enabled() && !button.selected() && button.over(event.x(), event.y())) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
					if (menu.clickMenuButton(minecraft.player, button.id())) {
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button.id());
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
		boolean onDesk = editor() && mx >= deskX() && mx < deskX() + DESK_W && my >= deskY() && my < deskY() + (deskOpen ? imageHeight : DESK_BAR);
		return !onDesk && super.hasClickedOutside(mx, my, xo, yo);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		// Everything is printed on the page in extractBackground.
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		card(g, x, y, imageWidth, imageHeight, 260);
		dogEar(g, x + imageWidth, y);
		masthead(g, x, y);
		statusBar(g, x, y);
		countdown(g, x, y);
		yourCopy(g, x, y);
		delivery(g, x, y);
		text(g, Component.literal("YOUR POCKETS").withStyle(ChatFormatting.BOLD), x + NewsstandMenu.INVENTORY_X, y + NewsstandMenu.INVENTORY_Y - 9, TINY, GREY);
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 9; col++) {
				int sy = row < 3 ? NewsstandMenu.INVENTORY_Y + row * 18 : NewsstandMenu.INVENTORY_Y + 58;
				slot(g, x + NewsstandMenu.INVENTORY_X + col * 18 - 1, y + sy - 1);
			}
		}
		if (editor()) {
			desk(g, mouseX, mouseY);
		}
		for (Button button : buttons()) {
			button(g, button, button.over(mouseX, mouseY));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		for (Button button : buttons()) {
			if (button.over(mouseX, mouseY)) {
				g.setTooltipForNextFrame(font, button.hint(), mouseX, mouseY);
			}
		}
	}

	// The page

	/** A sheet of newsprint with a drop shadow and a faint, fixed scatter of grain. */
	private void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int flecks) {
		g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, INK);
		g.fill(x, y, x + w, y + h, PAPER);
		int seed = 1234567 + w;
		for (int i = 0; i < flecks; i++) {
			seed = seed * 1103515245 + 12345;
			int fx = Math.floorMod(seed >>> 8, w);
			seed = seed * 1103515245 + 12345;
			int fy = Math.floorMod(seed >>> 8, h);
			g.fill(x + fx, y + fy, x + fx + 1, y + fy + 1, 0x12000000);
		}
	}

	private void masthead(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x + 8, y + 5, x + imageWidth - 14, y + 6, INK);
		Component name = Component.literal("The Observer").withStyle(ChatFormatting.BOLD);
		float scale = 1.6F;
		text(g, name, x + (imageWidth - font.width(name) * scale) / 2F, y + 9, scale, INK);
		text(g, Component.literal("NEWSSTAND").withStyle(ChatFormatting.BOLD), x + 8, y + 26, SMALL, GREY);
		int issue = menu.get(NewsstandMenu.ISSUE);
		Component right = Component.literal(issue > 0 ? "No. " + issue + " · Day " + menu.getWide(NewsstandMenu.DAY) : "Est. this world");
		text(g, right, x + imageWidth - 8 - font.width(right) * SMALL, y + 26, SMALL, GREY);
		g.fill(x + 8, y + 33, x + imageWidth - 8, y + 34, INK);
		g.fill(x + 8, y + 35, x + imageWidth - 8, y + 36, INK);
	}

	private void statusBar(GuiGraphicsExtractor g, int x, int y) {
		int status = status();
		int lamp;
		String text;
		if (status == Newsstands.Status.AT_PRESS.ordinal()) {
			lamp = (System.currentTimeMillis() / 400) % 2 == 0 ? AMBER : 0xFF8A5E1A;
			text = "AT THE PRESS · THE NEXT ISSUE IS BEING SET";
		} else if (status == Newsstands.Status.ON_SALE.ordinal()) {
			lamp = EMERALD;
			text = "ON SALE · ISSUE No. " + menu.get(NewsstandMenu.ISSUE) + " IS ON THE STANDS";
		} else {
			lamp = GREY;
			text = "AWAITING THE FIRST EDITION";
		}
		int ly = y + 40;
		g.fill(x + 9, ly, x + 15, ly + 6, INK);
		g.fill(x + 10, ly + 1, x + 14, ly + 5, lamp);
		g.fill(x + 10, ly + 1, x + 12, ly + 2, 0x88FFFFFF);
		text(g, Component.literal(text).withStyle(ChatFormatting.BOLD), x + 19, ly, SMALL, INK);
	}

	private void countdown(GuiGraphicsExtractor g, int x, int y) {
		int top = y + 50;
		int bottom = top + 22;
		g.fill(x + 8, top, x + imageWidth - 8, bottom, INK);

		text(g, Component.literal("NEXT EDITION").withStyle(ChatFormatting.BOLD), x + 13, top + 4, SMALL, 0xFFE8C66A);
		boolean atPress = status() == Newsstands.Status.AT_PRESS.ordinal();
		boolean weekly = menu.get(NewsstandMenu.AUTO) == 1;
		long seconds = menu.getWide(NewsstandMenu.SECONDS) - 1;
		long nextDay = menu.getWide(NewsstandMenu.NEXT_DAY);
		String detail = atPress ? "Hold the front page..."
				: !weekly ? "Scheduled editions are paused"
				: nextDay > 0 ? "Day " + nextDay + ", at sunrise" : "When the presses are ready";
		text(g, Component.literal(detail).withStyle(ChatFormatting.ITALIC), x + 13, top + 13, TINY, 0xFFCFC4AE);

		float scale = 1.4F;
		int right = x + imageWidth - 13;
		float baseline = top + (22 - 8 * scale) / 2F + 0.5F;
		if (atPress) {
			// The word stays put; the dots fill in after it.
			Component word = Component.literal("PRINTING").withStyle(ChatFormatting.BOLD);
			float wordX = right - font.width(Component.literal("PRINTING...").withStyle(ChatFormatting.BOLD)) * scale;
			text(g, word, wordX, baseline, scale, PAPER);
			int dots = (int) (System.currentTimeMillis() / 400 % 4);
			text(g, Component.literal(".".repeat(dots)).withStyle(ChatFormatting.BOLD), wordX + font.width(word) * scale, baseline, scale, PAPER);
			return;
		}
		String clock;
		if (!weekly) {
			clock = "PAUSED";
		} else if (seconds < 0) {
			clock = "--:--";
		} else if (seconds == 0) {
			clock = "DUE NOW";
		} else if (seconds >= 3600) {
			clock = seconds / 3600 + "h " + String.format(Locale.ROOT, "%02dm", seconds / 60 % 60);
		} else {
			clock = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
		}
		Component big = Component.literal(clock).withStyle(ChatFormatting.BOLD);
		text(g, big, right - font.width(big) * scale, baseline, scale, PAPER);
	}

	/** An inset card: the paper, one line about the reader's copy, and the one thing they can do about it. */
	private void yourCopy(GuiGraphicsExtractor g, int x, int y) {
		heading(g, "YOUR COPY", x + 8, y + CARD_TOP - 8, x + imageWidth - 8);
		g.fill(x + 8, y + CARD_TOP, x + imageWidth - 8, y + CARD_BOTTOM, RULE);
		g.fill(x + 9, y + CARD_TOP + 1, x + imageWidth - 9, y + CARD_BOTTOM - 1, PAPER_DARK);
		int middle = (CARD_TOP + CARD_BOTTOM) / 2;
		g.pose().pushMatrix();
		g.pose().translate(x + 16, y + middle - 12);
		g.pose().scale(1.5F, 1.5F);
		g.item(new ItemStack(ObserverContent.NEWSPAPER), 0, 0);
		g.pose().popMatrix();

		boolean collected = menu.get(NewsstandMenu.COLLECTED) == 1;
		int owed = menu.get(NewsstandMenu.OWED);
		String line = !onSale() ? "No edition has been printed yet."
				: !collected && owed > 0 ? "Issue No. " + menu.get(NewsstandMenu.ISSUE) + " is in. " + owed + (owed == 1 ? " emerald." : " emeralds.")
				: !collected ? "Issue No. " + menu.get(NewsstandMenu.ISSUE) + " is waiting for you."
				: "You already have yours.";
		text(g, Component.literal(line).withStyle(ChatFormatting.BOLD), x + ACTION_X, y + (onSale() ? CARD_TOP + 7 : middle - 3), SMALL, INK);

		if (onSale() && collected) {
			stamp(g, "COLLECTED", x + imageWidth - 38, y + (CARD_TOP + CARD_BOTTOM) / 2F + 1);
		}
		boolean paying = menu.coinSlotOpen();
		if (paying) {
			// The coin slot: the same height as the button beside it.
			int sx = x + NewsstandMenu.COIN_X - 1;
			int sy = y + NewsstandMenu.COIN_Y - 1;
			g.fill(sx - 1, sy - 1, sx + 19, sy + 19, EMERALD);
			g.fill(sx, sy, sx + 18, sy + 18, 0xFF3A332C);
			g.fill(sx, sy, sx + 18, sy + 1, 0x55000000);
			if (!menu.hasCoins()) {
				// A faint emerald with the number wanted, as a villager shows a trade's cost.
				ItemStack wanted = new ItemStack(Items.EMERALD);
				g.item(wanted, sx + 1, sy + 1);
				g.fill(sx, sy, sx + 18, sy + 18, 0xB03A332C);
				g.itemDecorations(font, wanted, sx + 1, sy + 1, String.valueOf(collected ? menu.get(NewsstandMenu.PRICE) : menu.get(NewsstandMenu.OWED)));
			}
		}
	}

	/** The delivery switch, its two halves joined, level with its label. */
	private void delivery(GuiGraphicsExtractor g, int x, int y) {
		heading(g, "DELIVERY", x + 8, y + SWITCH_Y + 5, x + 8);
	}

	/** The operators' card: the paper's voice, how often it comes out, delivery dogs and an early edition. */
	private void desk(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = deskX();
		int y = deskY();
		// Open, as tall as the page beside it; closed, only its title bar.
		if (deskOpen) {
			card(g, x, y, DESK_W, imageHeight, 60);
		} else {
			g.fill(x + 3, y + 3, x + DESK_W + 3, y + DESK_BAR + 3, 0x55000000);
			g.fill(x - 1, y - 1, x + DESK_W + 1, y + DESK_BAR + 1, INK);
		}
		boolean hover = overDeskBar(mouseX, mouseY);
		g.fill(x, y, x + DESK_W, y + DESK_BAR, hover ? 0xFF35302A : INK);
		Component title = Component.literal("EDITOR'S DESK").withStyle(ChatFormatting.BOLD);
		text(g, title, x + 6, y + (DESK_BAR - 7 * SMALL) / 2F, SMALL, PAPER);
		// A small arrow: down to open, up to close.
		int ax = x + DESK_W - 11;
		int ay = y + DESK_BAR / 2 - 1;
		for (int i = 0; i < 3; i++) {
			int row = deskOpen ? ay + 1 - i : ay - 1 + i;
			g.fill(ax + i, row, ax + 5 - i, row + 1, 0xFFE8C66A);
		}
		if (!deskOpen) {
			return;
		}
		deskLabel(g, "VOICE", x + 6, y + 16);
		deskLabel(g, "SCHEDULE", x + 6, y + 68);
		deskLabel(g, "EXCHANGE REWARDS", x + 6, y + 134);
		deskLabel(g, "PAWSTMAN", x + 6, y + 186);
		dotted(g, x + 6, y + imageHeight - 28, DESK_W - 12);
	}

	private void deskLabel(GuiGraphicsExtractor g, String text, int x, int y) {
		text(g, Component.literal(text).withStyle(ChatFormatting.BOLD), x, y, TINY, ACCENT);
	}

	// Drawing helpers

	private void button(GuiGraphicsExtractor g, Button b, boolean hover) {
		int fill;
		int ink;
		int border;
		if (b.selected()) {
			fill = ACCENT;
			ink = PAPER;
			border = INK;
		} else if (!b.enabled()) {
			fill = PAPER_DARK;
			ink = 0xFF9C917E;
			border = RULE;
		} else if (hover) {
			fill = INK;
			ink = PAPER;
			border = INK;
		} else {
			fill = PAPER_DARK;
			ink = INK;
			border = INK;
		}
		g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), border);
		g.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, fill);
		if (b.enabled() && !b.selected() && !hover) {
			g.fill(b.x() + 1, b.y() + b.h() - 2, b.x() + b.w() - 1, b.y() + b.h() - 1, 0x22000000);
		}
		Component label = b.label().copy().withStyle(ChatFormatting.BOLD);
		float scale = Math.min(b.h() >= 13 ? SMALL : TINY, (b.w() - 6) / (float) font.width(label));
		text(g, label, b.x() + (b.w() - font.width(label) * scale) / 2F, b.y() + (b.h() - 7 * scale) / 2F, scale, ink);
	}

	/** A section heading: a red diamond, its name, and a thin rule running on to {@code right}. */
	private void heading(GuiGraphicsExtractor g, String name, int x, int y, int right) {
		Component text = Component.literal(name).withStyle(ChatFormatting.BOLD);
		text(g, text, x, y, SMALL, ACCENT);
		int ruleFrom = x + Math.round(font.width(text) * SMALL) + 5;
		if (right - ruleFrom > 12) {
			g.fill(ruleFrom, y + 3, right, y + 4, RULE);
		}
	}

	/** A slightly crooked rubber stamp in red ink, centred on (cx, cy). */
	private void stamp(GuiGraphicsExtractor g, String word, float cx, float cy) {
		Component text = Component.literal(word).withStyle(ChatFormatting.BOLD);
		float scale = 0.6F;
		int w = Math.round(font.width(text) * scale) + 8;
		int h = 12;
		int ink = 0xC09A1F1F;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(-9));
		g.fill(-w / 2, -h / 2, w / 2, -h / 2 + 1, ink);
		g.fill(-w / 2, h / 2 - 1, w / 2, h / 2, ink);
		g.fill(-w / 2, -h / 2, -w / 2 + 1, h / 2, ink);
		g.fill(w / 2 - 1, -h / 2, w / 2, h / 2, ink);
		g.fill(-w / 2 + 2, -h / 2 + 2, w / 2 - 2, -h / 2 + 3, ink);
		g.fill(-w / 2 + 2, h / 2 - 3, w / 2 - 2, h / 2 - 2, ink);
		g.pose().translate(-font.width(text) * scale / 2F, -7 * scale / 2F);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, ink, false);
		g.pose().popMatrix();
	}

	/** The page's top right corner, folded down. */
	private static void dogEar(GuiGraphicsExtractor g, int right, int top) {
		int size = 9;
		for (int r = 0; r <= size; r++) {
			// Beyond the fold: the page is gone; below it, the folded flap.
			g.fill(right - size + r, top - 1 + r, right + 1, top + r, 0xFF141210);
			g.fill(right - size, top + r, right - size + r, top + r + 1, PAPER_DARK);
			g.fill(right - size + r, top + r, right - size + r + 1, top + r + 1, RULE);
		}
		g.fill(right - size, top + size, right, top + size + 1, 0x33000000);
	}

	private void label(GuiGraphicsExtractor g, String text, int x, int y) {
		text(g, Component.literal(text).withStyle(ChatFormatting.BOLD), x, y, SMALL, ACCENT);
	}

	private void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, RULE);
		g.fill(x + 1, y + 1, x + 17, y + 17, PAPER_DARK);
		g.fill(x + 1, y + 1, x + 17, y + 2, 0x22000000);
		g.fill(x + 1, y + 1, x + 2, y + 17, 0x22000000);
	}

	private static void dotted(GuiGraphicsExtractor g, int x, int y, int width) {
		for (int d = 0; d < width; d += 2) {
			g.fill(x + d, y, x + d + 1, y + 1, RULE);
		}
	}

	private void text(GuiGraphicsExtractor g, Component text, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}
}
