package dev.spacebod.herald.neoforge;

import dev.spacebod.herald.Herald;
import dev.spacebod.herald.HeraldContent;
import dev.spacebod.herald.command.HeraldCommand;
import dev.spacebod.herald.delivery.DeliveryDog;
import dev.spacebod.herald.news.Newsroom;
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

/** Runs on both sides; the client additionally gets HeraldNeoForgeClient. */
@Mod(Herald.MOD_ID)
public final class HeraldNeoForge {
	public HeraldNeoForge(IEventBus modBus) {
		Herald.init(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get());

		// Registries are only open during their RegisterEvent; the vanilla Registry.register calls work inside it.
		modBus.addListener((RegisterEvent event) -> {
			if (event.getRegistryKey().equals(Registries.DATA_COMPONENT_TYPE)) {
				HeraldContent.registerComponents();
			} else if (event.getRegistryKey().equals(Registries.ITEM)) {
				HeraldContent.registerItems();
			} else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
				HeraldContent.registerEntities();
			}
		});
		modBus.addListener((EntityAttributeCreationEvent event) ->
				event.put(HeraldContent.DELIVERY_DOG, DeliveryDog.createAttributes().build()));

		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> HeraldCommand.register(event.getDispatcher()));
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
