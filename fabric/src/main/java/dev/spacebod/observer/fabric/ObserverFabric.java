package dev.spacebod.observer.fabric;

import dev.spacebod.observer.stand.NewsstandBlockEntity;
import dev.spacebod.observer.delivery.PawstmanMenu;
import dev.spacebod.observer.stand.NewsstandMenu;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.command.ObserverCommand;
import dev.spacebod.observer.delivery.DeliveryDog;
import dev.spacebod.observer.news.Newsroom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;

public class ObserverFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Observer.init(FabricLoader.getInstance().getConfigDir(), FabricLoader.getInstance().getGameDir(), FabricLoader.getInstance().isDevelopmentEnvironment());
		ObserverContent.registerComponents();
		ObserverContent.registerBlocks();
		ObserverContent.registerItems();
		ObserverContent.registerBlockEntities(FabricBlockEntityTypeBuilder.create(NewsstandBlockEntity::new, ObserverContent.NEWSSTAND).build());
		ObserverContent.registerMenus(new MenuType<>(NewsstandMenu::new, FeatureFlags.VANILLA_SET),
					new MenuType<>(PawstmanMenu::new, FeatureFlags.VANILLA_SET));
		ObserverContent.registerEntities();
		FabricDefaultAttributeRegistry.register(ObserverContent.DELIVERY_DOG, DeliveryDog.createAttributes());

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ObserverContent.NEWSSTAND_ITEM));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ObserverCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(Newsroom::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(Newsroom::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(Newsroom::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Newsroom.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Newsroom.onPlayerLeave(handler.player));
	}
}
