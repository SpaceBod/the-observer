package dev.spacebod.herald.fabric.client;

import dev.spacebod.herald.HeraldContent;
import dev.spacebod.herald.client.HeraldClient;
import dev.spacebod.herald.client.DeliveryDogRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class HeraldFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HeraldClient.init();
		EntityRendererRegistry.register(HeraldContent.DELIVERY_DOG, DeliveryDogRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(HeraldClient::onEndTick);
	}
}
