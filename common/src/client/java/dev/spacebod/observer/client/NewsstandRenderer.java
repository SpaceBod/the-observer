package dev.spacebod.observer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.stand.NewsstandBlock;
import dev.spacebod.observer.stand.NewsstandBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a newsstand shows each player: a folded copy in the window while theirs is waiting, the name on its
 * plate, and two lamps (the coin slot and the bulb on the lid): green while their copy waits, red once they
 * have it, blinking orange while the next edition is at the press, dark before the first.
 */
public class NewsstandRenderer implements BlockEntityRenderer<NewsstandBlockEntity, NewsstandRenderer.State> {
	private static final RenderType LAMP = RenderTypes.entityCutout(Observer.id("textures/block/newsstand_lamp.png"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final FormattedCharSequence NAME = Component.literal("THE OBSERVER").withStyle(ChatFormatting.BOLD).getVisualOrderText();
	private static final int INK = 0xFF1E1B18;

	private final ItemModelResolver items;
	private final Font font;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState paper = new ItemStackRenderState();
		boolean waiting;
		NewsstandBlockEntity.Lamp lamp = NewsstandBlockEntity.Lamp.OFF;
		Direction facing = Direction.NORTH;
	}

	public NewsstandRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(NewsstandBlockEntity stand, State state, float partialTicks, Vec3 camera,
								   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(stand, state, partialTicks, camera, breakProgress);
		state.facing = stand.getBlockState().getValue(NewsstandBlock.FACING);
		var player = Minecraft.getInstance().player;
		state.waiting = player != null && stand.paperWaitingFor(player.getUUID());
		state.lamp = player != null ? stand.lampFor(player.getUUID()) : NewsstandBlockEntity.Lamp.OFF;
		if (state.waiting) {
			items.updateForTopItem(state.paper, new ItemStack(ObserverContent.NEWSPAPER), ItemDisplayContext.FIXED, stand.getLevel(), null, 0);
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		// Into the model's own space: sixteenths of a block, front facing north, then turned the way the stand faces.
		pose.translate(0.5F, 0F, 0.5F);
		ClientCompat.rotate(pose, Axis.YP, 180F - state.facing.toYRot());
		pose.translate(-0.5F, 0F, -0.5F);
		pose.scale(1F / 16F, 1F / 16F, 1F / 16F);

		if (state.waiting) {
			// In the window, leaning back a little.
			pose.pushPose();
			pose.translate(8F, 9.6F, 4.2F);
			ClientCompat.rotate(pose, Axis.YP, 180F);
			ClientCompat.rotate(pose, Axis.XP, -8F);
			pose.scale(6.7F, 6.7F, 6.7F);
			state.paper.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}

		nameplate(pose, collector, state.lightCoords);
		lamps(pose, collector, state);
		pose.popPose();
	}

	/** The paper's name printed across the cream plate above the window. */
	private void nameplate(PoseStack pose, SubmitNodeCollector collector, int light) {
		pose.pushPose();
		pose.translate(8F, 13.5F, 2.97F);
		ClientCompat.rotate(pose, Axis.YP, 180F);
		float scale = 8.6F / font.width(NAME);
		pose.scale(scale, -scale, scale);
		collector.submitText(pose, -font.width(NAME) / 2F, -3.5F, NAME, false, Font.DisplayMode.POLYGON_OFFSET, light, INK, 0, 0);
		pose.popPose();
	}

	private void lamps(PoseStack pose, SubmitNodeCollector collector, State state) {
		boolean lit = state.lamp != NewsstandBlockEntity.Lamp.OFF;
		int color = switch (state.lamp) {
			case WAITING -> 0xFF3BE070;
			case CLAIMED -> 0xFFE23B3B;
			case PRINTING -> (System.currentTimeMillis() / 450) % 2 == 0 ? 0xFFFFA030 : 0xFF9A5A14;
			case OFF -> 0xFF34363A;
		};
		int light = lit ? FULL_BRIGHT : state.lightCoords;
		int top = lit ? brighter(color) : color;
		collector.submitCustomGeometry(pose, LAMP, (p, buffer) -> {
			// The coin slot's window.
			front(buffer, p, 4F, 4.5F, 6F, 5.5F, 2.45F, -1, color, light);
			// The bulb on the lid: four sides and a brighter top.
			float x0 = 7.25F;
			float x1 = 8.75F;
			float y0 = 16.5F;
			float y1 = 17.75F;
			float z0 = 7.25F;
			float z1 = 8.75F;
			front(buffer, p, x0, y0, x1, y1, z0, -1, color, light);
			front(buffer, p, x0, y0, x1, y1, z1, 1, color, light);
			side(buffer, p, x0, y0, z0, y1, z1, -1, color, light);
			side(buffer, p, x1, y0, z0, y1, z1, 1, color, light);
			vertex(buffer, p, x0, y1, z0, 0, 0, top, light, 0, 1, 0);
			vertex(buffer, p, x0, y1, z1, 0, 1, top, light, 0, 1, 0);
			vertex(buffer, p, x1, y1, z1, 1, 1, top, light, 0, 1, 0);
			vertex(buffer, p, x1, y1, z0, 1, 0, top, light, 0, 1, 0);
		});
	}

	/** A quad facing along z at depth {@code z}, from corner (x0, y0) to (x1, y1). */
	private static void front(VertexConsumer buffer, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, float nz, int color, int light) {
		vertex(buffer, p, x0, y0, z, 0, 1, color, light, 0, 0, nz);
		vertex(buffer, p, x1, y0, z, 1, 1, color, light, 0, 0, nz);
		vertex(buffer, p, x1, y1, z, 1, 0, color, light, 0, 0, nz);
		vertex(buffer, p, x0, y1, z, 0, 0, color, light, 0, 0, nz);
	}

	/** A quad facing along x at {@code x}, spanning y0..y1 and z0..z1. */
	private static void side(VertexConsumer buffer, PoseStack.Pose p, float x, float y0, float z0, float y1, float z1, float nx, int color, int light) {
		vertex(buffer, p, x, y0, z0, 0, 1, color, light, nx, 0, 0);
		vertex(buffer, p, x, y0, z1, 1, 1, color, light, nx, 0, 0);
		vertex(buffer, p, x, y1, z1, 1, 0, color, light, nx, 0, 0);
		vertex(buffer, p, x, y1, z0, 0, 0, color, light, nx, 0, 0);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose p, float x, float y, float z, float u, float v, int color, int light,
							   float nx, float ny, float nz) {
		buffer.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	private static int brighter(int color) {
		int r = Math.min(255, ((color >> 16) & 0xFF) + 60);
		int g = Math.min(255, ((color >> 8) & 0xFF) + 60);
		int b = Math.min(255, (color & 0xFF) + 60);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
