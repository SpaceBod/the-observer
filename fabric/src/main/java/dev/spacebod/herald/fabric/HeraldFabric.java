package dev.spacebod.herald.fabric;

import dev.spacebod.herald.Herald;
import dev.spacebod.herald.HeraldContent;
import dev.spacebod.herald.command.HeraldCommand;
import dev.spacebod.herald.delivery.DeliveryDog;
import dev.spacebod.herald.news.Newsroom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;

public class HeraldFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Herald.init(FabricLoader.getInstance().getConfigDir(), FabricLoader.getInstance().getGameDir());
		HeraldContent.registerComponents();
		HeraldContent.registerItems();
		HeraldContent.registerEntities();
		FabricDefaultAttributeRegistry.register(HeraldContent.DELIVERY_DOG, DeliveryDog.createAttributes());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> HeraldCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(Newsroom::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(Newsroom::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(Newsroom::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Newsroom.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Newsroom.onPlayerLeave(handler.player));
	}
}
