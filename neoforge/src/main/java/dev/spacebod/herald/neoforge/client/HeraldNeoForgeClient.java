package dev.spacebod.herald.neoforge.client;

import dev.spacebod.herald.Herald;
import dev.spacebod.herald.HeraldContent;
import dev.spacebod.herald.client.HeraldClient;
import dev.spacebod.herald.client.DeliveryDogRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half; HeraldNeoForge (both sides) has already loaded the config by the time this runs. */
@Mod(value = Herald.MOD_ID, dist = Dist.CLIENT)
public final class HeraldNeoForgeClient {
	public HeraldNeoForgeClient(IEventBus modBus) {
		HeraldClient.init();
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
				event.registerEntityRenderer(HeraldContent.DELIVERY_DOG, DeliveryDogRenderer::new));
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> HeraldClient.onEndTick(Minecraft.getInstance()));
	}
}
