package dev.spacebod.herald;

import dev.spacebod.herald.delivery.DeliveryDog;
import dev.spacebod.herald.edition.Edition;
import dev.spacebod.herald.item.NewspaperItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Everything the mod adds to the game's registries. Fabric calls the register methods from its main
 * entrypoint; NeoForge calls each one from the matching RegisterEvent, the only time its registries are open.
 */
public final class HeraldContent {
	public static final ResourceKey<Item> NEWSPAPER_KEY = ResourceKey.create(Registries.ITEM, Herald.id("newspaper"));
	public static final ResourceKey<EntityType<?>> DELIVERY_DOG_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Herald.id("delivery_dog"));

	public static DataComponentType<Edition> EDITION;
	/** Exchange slots this copy's reader has handed in, so their page shows them as done. */
	public static DataComponentType<List<Integer>> HANDED_IN;
	public static Item NEWSPAPER;
	public static EntityType<DeliveryDog> DELIVERY_DOG;

	private HeraldContent() {
	}

	public static void registerComponents() {
		EDITION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Herald.id("edition"),
				DataComponentType.<Edition>builder()
						.persistent(Edition.CODEC)
						.networkSynchronized(Edition.STREAM_CODEC)
						.cacheEncoding()
						.build());
		HANDED_IN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Herald.id("handed_in"),
				DataComponentType.<List<Integer>>builder()
						.persistent(Codec.INT.listOf())
						.networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
						.build());
	}

	public static void registerItems() {
		NEWSPAPER = Registry.register(BuiltInRegistries.ITEM, NEWSPAPER_KEY,
				new NewspaperItem(new Item.Properties().stacksTo(1).setId(NEWSPAPER_KEY)));
	}

	public static void registerEntities() {
		// Never saved: a delivery dog is only ever mid-delivery, and an unfinished delivery is retried from the ledger.
		DELIVERY_DOG = Registry.register(BuiltInRegistries.ENTITY_TYPE, DELIVERY_DOG_KEY,
				EntityType.Builder.of(DeliveryDog::new, MobCategory.MISC)
						.sized(0.6F, 0.85F)
						.eyeHeight(0.68F)
						.clientTrackingRange(10)
						.noSave()
						.build(DELIVERY_DOG_KEY));
	}
}
