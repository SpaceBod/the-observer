package dev.spacebod.observer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.delivery.DeliveryDog;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A friendly wolf in a red collar, carrying the paper rolled up crosswise in its jaws. The paper is the dog's
 * held newspaper drawn with a different item model (newspaper_rolled), so it is the real stack that it drops.
 */
public class DeliveryDogRenderer extends MobRenderer<DeliveryDog, DeliveryDogRenderer.State, WolfModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_tame.png");
	private static final Identifier COLLAR = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar.png");
	private static final Identifier ROLLED_PAPER = Observer.id("newspaper_rolled");
	/** A tame wolf's tail with full health: raised and happy. */
	private static final float HAPPY_TAIL = (float) (Math.PI / 5) * 1.8f;

	public static final class State extends WolfRenderState {
		final ItemStackRenderState paper = new ItemStackRenderState();
	}

	public DeliveryDogRenderer(EntityRendererProvider.Context context) {
		super(context, new AdultWolfModel(context.bakeLayer(ModelLayers.WOLF)), 0.5F);
		addLayer(new CollarLayer(this));
		addLayer(new PaperLayer(this));
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DeliveryDog entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.tailAngle = HAPPY_TAIL;
		state.collarColor = DyeColor.RED;
		ItemStack held = entity.getMainHandItem();
		if (held.isEmpty()) {
			state.paper.clear();
		} else {
			ItemStack rolled = held.copyWithCount(1);
			rolled.set(DataComponents.ITEM_MODEL, ROLLED_PAPER);
			itemModelResolver.updateForLiving(state.paper, rolled, ItemDisplayContext.NONE, entity);
		}
	}

	private static final class CollarLayer extends RenderLayer<State, WolfModel> {
		CollarLayer(RenderLayerParent<State, WolfModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (!state.isInvisible && state.collarColor != null) {
				renderColoredCutoutModel(getParentModel(), COLLAR, poseStack, collector, lightCoords, state, state.collarColor.getTextureDiffuseColor(), 1);
			}
		}
	}

	/** Places the rolled paper across the bottom of the snout, following the head as it turns and nods. */
	private static final class PaperLayer extends RenderLayer<State, WolfModel> {
		PaperLayer(RenderLayerParent<State, WolfModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.paper.isEmpty() || state.isInvisible) {
				return;
			}
			poseStack.pushPose();
			getParentModel().root().getChild("head").translateAndRotate(poseStack);
			// The snout spans x -0.5..2.5, y 0..3, z -5..-1 (pixels) from the head's pivot. The roll (3 px thick) sits
			// just under it, its top tucked 0.2 px into the snout, so it reads as gripped rather than skewered.
			poseStack.translate(1f / 16f, 4.3f / 16f, -3.5f / 16f);
			state.paper.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
