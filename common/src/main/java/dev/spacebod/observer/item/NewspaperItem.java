package dev.spacebod.observer.item;

import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.edition.Edition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A printed edition. Use it to read; the whole issue travels on the stack's {@link ObserverContent#EDITION} component. */
public class NewspaperItem extends Item {
	/** Opens the reading screen. Replaced by the client entrypoint; a dedicated server never opens anything. */
	public static Consumer<ItemStack> reader = stack -> {
	};

	public NewspaperItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.get(ObserverContent.EDITION) == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			reader.accept(stack);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getName(ItemStack stack) {
		Edition edition = stack.get(ObserverContent.EDITION);
		return edition != null ? Component.literal(edition.paper()) : super.getName(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		Edition edition = stack.get(ObserverContent.EDITION);
		if (edition == null) {
			builder.accept(Component.translatable("item.spacebod_observer.newspaper.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		builder.accept(Component.translatable("item.spacebod_observer.newspaper.issue", edition.issue(), edition.day())
				.withStyle(ChatFormatting.GRAY));
		builder.accept(Component.literal("“" + edition.lead().headline() + "”")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
