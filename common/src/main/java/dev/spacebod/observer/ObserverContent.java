package dev.spacebod.observer;

import dev.spacebod.observer.delivery.DeliveryDog;
import dev.spacebod.observer.delivery.PawstmanMenu;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.item.NewspaperItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import dev.spacebod.observer.stand.NewsstandBlock;
import dev.spacebod.observer.stand.NewsstandBlockEntity;
import dev.spacebod.observer.stand.NewsstandMenu;

import java.util.List;

/**
 * Everything the mod adds to the game's registries. Fabric calls the register methods from its main
 * entrypoint; NeoForge calls each one from the matching RegisterEvent, the only time its registries are open.
 */
public final class ObserverContent {
	public static final ResourceKey<Item> NEWSPAPER_KEY = ResourceKey.create(Registries.ITEM, Observer.id("newspaper"));
	public static final ResourceKey<Block> NEWSSTAND_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, Observer.id("newsstand"));
	public static final ResourceKey<Item> NEWSSTAND_ITEM_KEY = ResourceKey.create(Registries.ITEM, Observer.id("newsstand"));
	public static final ResourceKey<EntityType<?>> DELIVERY_DOG_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Observer.id("delivery_dog"));

	public static DataComponentType<Edition> EDITION;
	/** Exchange slots this copy's reader has handed in, so their page shows them as done. */
	public static DataComponentType<List<Integer>> HANDED_IN;
	public static Item NEWSPAPER;
	public static EntityType<DeliveryDog> DELIVERY_DOG;
	public static NewsstandBlock NEWSSTAND;
	/** Painted sheet metal: lighter and hollower than a solid block of iron. */
	private static final SoundType NEWSSTAND_SOUND = new SoundType(1.0F, 1.25F, SoundEvents.COPPER_BREAK, SoundEvents.COPPER_STEP,
			SoundEvents.COPPER_PLACE, SoundEvents.COPPER_HIT, SoundEvents.COPPER_FALL);
	public static Item NEWSSTAND_ITEM;
	public static BlockEntityType<NewsstandBlockEntity> NEWSSTAND_ENTITY;
	public static MenuType<NewsstandMenu> NEWSSTAND_MENU;
	public static MenuType<PawstmanMenu> PAWSTMAN_MENU;

	private ObserverContent() {
	}

	public static void registerComponents() {
		EDITION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Observer.id("edition"),
				DataComponentType.<Edition>builder()
						.persistent(Edition.CODEC)
						.networkSynchronized(Edition.STREAM_CODEC)
						.cacheEncoding()
						.build());
		HANDED_IN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Observer.id("handed_in"),
				DataComponentType.<List<Integer>>builder()
						.persistent(Codec.INT.listOf())
						.networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
						.build());
	}

	public static void registerBlocks() {
		NEWSSTAND = Registry.register(BuiltInRegistries.BLOCK, NEWSSTAND_BLOCK_KEY, new NewsstandBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_RED)
				.strength(2.5F, 6.0F)
				.sound(NEWSSTAND_SOUND)
				.noOcclusion()
				.setId(NEWSSTAND_BLOCK_KEY)));
	}

	public static void registerItems() {
		NEWSPAPER = Registry.register(BuiltInRegistries.ITEM, NEWSPAPER_KEY,
				new NewspaperItem(new Item.Properties().stacksTo(1).setId(NEWSPAPER_KEY)));
		NEWSSTAND_ITEM = Registry.register(BuiltInRegistries.ITEM, NEWSSTAND_ITEM_KEY,
				new BlockItem(NEWSSTAND, new Item.Properties().useBlockDescriptionPrefix().setId(NEWSSTAND_ITEM_KEY)));
	}

	/** @param newsstand built by the loader for {@link #NEWSSTAND} (vanilla keeps the constructor private in some versions) */
	public static void registerBlockEntities(BlockEntityType<NewsstandBlockEntity> newsstand) {
		NEWSSTAND_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Observer.id("newsstand"), newsstand);
	}

	/** @param newsstand built by the loader: {@code new MenuType<>(NewsstandMenu::new, FeatureFlags.VANILLA_SET)}, which vanilla keeps private */
	public static void registerMenus(MenuType<NewsstandMenu> newsstand, MenuType<PawstmanMenu> pawstman) {
		NEWSSTAND_MENU = Registry.register(BuiltInRegistries.MENU, Observer.id("newsstand"), newsstand);
		PAWSTMAN_MENU = Registry.register(BuiltInRegistries.MENU, Observer.id("pawstman"), pawstman);
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
