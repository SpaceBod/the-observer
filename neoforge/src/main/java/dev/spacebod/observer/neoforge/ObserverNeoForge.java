package dev.spacebod.observer.neoforge;

import dev.spacebod.observer.stand.NewsstandBlockEntity;
import dev.spacebod.observer.delivery.PawstmanMenu;
import dev.spacebod.observer.stand.NewsstandMenu;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.Set;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.command.ObserverCommand;
import dev.spacebod.observer.delivery.DeliveryDog;
import dev.spacebod.observer.news.Newsroom;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Runs on both sides; the client additionally gets ObserverNeoForgeClient. */
@Mod(Observer.MOD_ID)
public final class ObserverNeoForge {
	public ObserverNeoForge(IEventBus modBus) {
		Observer.init(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get(), !FMLEnvironment.isProduction());

		// Registries are only open during their RegisterEvent; the vanilla Registry.register calls work inside it.
		modBus.addListener((RegisterEvent event) -> {
			if (event.getRegistryKey().equals(Registries.DATA_COMPONENT_TYPE)) {
				ObserverContent.registerComponents();
			} else if (event.getRegistryKey().equals(Registries.BLOCK)) {
				ObserverContent.registerBlocks();
			} else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
				ObserverContent.registerBlockEntities(new BlockEntityType<>(NewsstandBlockEntity::new, Set.of(ObserverContent.NEWSSTAND)));
			} else if (event.getRegistryKey().equals(Registries.MENU)) {
				ObserverContent.registerMenus(new MenuType<>(NewsstandMenu::new, FeatureFlags.VANILLA_SET),
					new MenuType<>(PawstmanMenu::new, FeatureFlags.VANILLA_SET));
			} else if (event.getRegistryKey().equals(Registries.ITEM)) {
				ObserverContent.registerItems();
			} else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
				ObserverContent.registerEntities();
			}
		});
		modBus.addListener((EntityAttributeCreationEvent event) ->
				event.put(ObserverContent.DELIVERY_DOG, DeliveryDog.createAttributes().build()));

		modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
			if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
				event.accept(ObserverContent.NEWSSTAND_ITEM);
			}
		});
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ObserverCommand.register(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> Newsroom.onServerStarted(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> Newsroom.onServerStopping(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Newsroom.onServerTick(event.getServer()));
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				Newsroom.onPlayerJoin(player);
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				Newsroom.onPlayerLeave(player);
			}
		});
	}
}
