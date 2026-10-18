package dev.spacebod.observer.fabric.client;

import dev.spacebod.observer.client.NewsstandRenderer;
import dev.spacebod.observer.client.NewsstandScreen;
import dev.spacebod.observer.client.PawstmanScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.client.ObserverClient;
import dev.spacebod.observer.client.DeliveryDogRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ObserverFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ObserverClient.init();
		EntityRendererRegistry.register(ObserverContent.DELIVERY_DOG, DeliveryDogRenderer::new);
		BlockEntityRenderers.register(ObserverContent.NEWSSTAND_ENTITY, NewsstandRenderer::new);
		MenuScreens.register(ObserverContent.NEWSSTAND_MENU, NewsstandScreen::new);
		MenuScreens.register(ObserverContent.PAWSTMAN_MENU, PawstmanScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(ObserverClient::onEndTick);
	}
}
