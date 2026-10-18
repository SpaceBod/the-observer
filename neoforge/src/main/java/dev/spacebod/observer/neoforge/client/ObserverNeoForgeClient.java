package dev.spacebod.observer.neoforge.client;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.client.ObserverClient;
import dev.spacebod.observer.client.ObserverConfigScreen;
import dev.spacebod.observer.client.NewsstandRenderer;
import dev.spacebod.observer.client.NewsstandScreen;
import dev.spacebod.observer.client.PawstmanScreen;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import dev.spacebod.observer.client.DeliveryDogRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half; ObserverNeoForge (both sides) has already loaded the config by the time this runs. */
@Mod(value = Observer.MOD_ID, dist = Dist.CLIENT)
public final class ObserverNeoForgeClient {
	public ObserverNeoForgeClient(IEventBus modBus, ModContainer container) {
		ObserverClient.init();
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ObserverConfigScreen(parent));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
			event.registerEntityRenderer(ObserverContent.DELIVERY_DOG, DeliveryDogRenderer::new);
			event.registerBlockEntityRenderer(ObserverContent.NEWSSTAND_ENTITY, NewsstandRenderer::new);
		});
		modBus.addListener((RegisterMenuScreensEvent event) -> {
			event.register(ObserverContent.NEWSSTAND_MENU, NewsstandScreen::new);
			event.register(ObserverContent.PAWSTMAN_MENU, PawstmanScreen::new);
		});
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ObserverClient.onEndTick(Minecraft.getInstance()));
	}
}
