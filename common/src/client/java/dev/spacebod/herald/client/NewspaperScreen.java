package dev.spacebod.herald.client;

import dev.spacebod.herald.Herald;
import dev.spacebod.herald.edition.Edition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Reads an edition as an open newspaper: the stapled spread texture, two typeset pages at a time
 * ({@link NewspaperLayout}), and the book's page-turn arrows in the bottom corners. The whole spread is
 * scaled to fill the window.
 */
public class NewspaperScreen extends Screen {
	private static final Identifier TEXTURE = Herald.id("textures/gui/newspaper.png");
	/** The texture is the spread plus the stack of sheets showing beneath its right and bottom edges. */
	private static final int TEXTURE_W = NewspaperLayout.SPREAD_W + 4;
	private static final int TEXTURE_H = NewspaperLayout.PAGE_H + 4;

	private final Edition edition;
	private List<NewspaperLayout.Page> pages;
	private int spread;
	private PageButton back;
	private PageButton forward;
	private ExchangePage exchange;

	public NewspaperScreen(Edition edition) {
		super(Component.literal(edition.paper()));
		this.edition = edition;
	}

	@Override
	protected void init() {
		if (pages == null) {
			pages = new NewspaperLayout(font, edition).build();
		}
		if (exchange == null && minecraft.player != null) {
			exchange = new ExchangePage(edition, minecraft.player, font);
		}
		float s = scale();
		int left = paperLeft();
		int top = paperTop();
		int bottom = top + Math.round((NewspaperLayout.PAGE_H - 16) * s);
		back = addRenderableWidget(new PageButton(left + Math.round(10 * s), bottom, false, b -> turn(-1), true));
		forward = addRenderableWidget(new PageButton(left + Math.round((NewspaperLayout.SPREAD_W - 10) * s) - 23, bottom, true, b -> turn(1), true));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, top + Math.round(TEXTURE_H * s) + 4, 200, 20).build());
		updateButtons();
	}

	private int spreads() {
		return (pages.size() + 1) / 2;
	}

	private void turn(int delta) {
		spread = Math.clamp(spread + delta, 0, spreads() - 1);
		updateButtons();
	}

	private void updateButtons() {
		back.visible = spread > 0;
		forward.visible = spread < spreads() - 1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && exchange != null && spread == NewspaperLayout.EXCHANGE_PAGE / 2) {
			float s = scale();
			float px = (float) (event.x() - paperLeft()) / s;
			float py = (float) (event.y() - paperTop()) / s;
			// Which of the spread's two pages was clicked: the fold is halfway across.
			int half = px < NewspaperLayout.PAGE_W ? 0 : 1;
			if (exchange.click(half, px - NewspaperLayout.left(NewspaperLayout.EXCHANGE_PAGE + half), py)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private float scale() {
		return Math.clamp(Math.min((width - 16) / (float) TEXTURE_W, (height - 32) / (float) TEXTURE_H), 0.5f, 4f);
	}

	private int paperLeft() {
		return Math.round((width - TEXTURE_W * scale()) / 2);
	}

	private int paperTop() {
		return Math.max(4, Math.round((height - 28 - TEXTURE_H * scale()) / 2));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		float s = scale();
		g.pose().pushMatrix();
		g.pose().translate(paperLeft(), paperTop());
		g.pose().scale(s, s);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
		for (int side = 0; side < 2; side++) {
			int index = spread * 2 + side;
			if (index < pages.size()) {
				draw(g, pages.get(index));
			}
			int half = index - NewspaperLayout.EXCHANGE_PAGE;
			if ((half == 0 || half == 1) && exchange != null) {
				float left = NewspaperLayout.left(index);
				exchange.draw(g, half, left, (mouseX - paperLeft()) / s - left, (mouseY - paperTop()) / s);
			}
		}
		g.pose().popMatrix();
	}

	private void draw(GuiGraphicsExtractor g, NewspaperLayout.Page page) {
		for (NewspaperLayout.Fill fill : page.fills) {
			// Rules can be thinner than one paper unit, so fill a unit square scaled to size.
			g.pose().pushMatrix();
			g.pose().translate(fill.x0(), fill.y0());
			g.pose().scale(fill.x1() - fill.x0(), fill.y1() - fill.y0());
			g.fill(0, 0, 1, 1, fill.color());
			g.pose().popMatrix();
		}
		for (NewspaperLayout.Text text : page.texts) {
			g.pose().pushMatrix();
			g.pose().translate(text.x(), text.y());
			g.pose().scale(text.scale(), text.scale());
			g.text(font, text.line(), 0, 0, text.color(), false);
			g.pose().popMatrix();
		}
	}
}
