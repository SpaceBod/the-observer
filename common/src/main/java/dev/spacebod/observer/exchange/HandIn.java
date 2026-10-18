package dev.spacebod.observer.exchange;

import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.news.Ledger;
import dev.spacebod.observer.news.Newsroom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Handing in a bounty, decided entirely on the server: the bounty is worked out again from the latest edition
 * rather than trusted from the client. On success the hand-in goes into the ledger (and the next edition's news)
 * and the player's copies are marked so their Exchange page shows it as done.
 */
public final class HandIn {
	private HandIn() {
	}

	public record Result(boolean ok, Component message) {
	}

	public static Result handIn(ServerPlayer player, int issue, int slot) {
		Edition edition = Newsroom.latest();
		Ledger.PlayerRecord record = Newsroom.record(player);
		if (edition == null || record == null || edition.issue() != issue) {
			return fail("This bounty has closed. The new edition has fresh ones.");
		}
		Optional<Bounties.Bounty> found = Bounties.forPlayer(edition.exchange(), player.getUUID()).stream()
				.filter(b -> b.slot() == slot).findFirst();
		if (found.isEmpty()) {
			return fail("There's no such bounty for you in this edition.");
		}
		Bounties.Bounty bounty = found.get();
		String key = issue + ":" + slot;
		if (record.bountiesDone.contains(key)) {
			return fail(bounty.who() + " has already been paid for that one.");
		}

		String deed;
		if (bounty.kind() == Bounties.Kind.DELIVER) {
			Item item = BuiltInRegistries.ITEM.getValue(bounty.target());
			int have = count(player.getInventory(), item);
			if (have < bounty.count()) {
				return fail("You have " + have + " of the " + bounty.count() + " " + name(item) + " " + bounty.who() + " needs.");
			}
			take(player.getInventory(), item, bounty.count());
			deed = "delivered " + bounty.count() + " " + name(item).toLowerCase(Locale.ROOT);
		} else {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(bounty.target());
			int now = player.getStats().getValue(Stats.ENTITY_KILLED, type);
			int before = edition.exchange().baselines().getOrDefault(player.getStringUUID(), Map.of()).getOrDefault(bounty.statKey(), now);
			if (now - before < bounty.count()) {
				return fail("So far " + (now - before) + " of " + bounty.count() + ". The Watch is counting on you.");
			}
			deed = "dealt with " + bounty.count() + " " + Component.translatable(type.getDescriptionId()).getString().toLowerCase(Locale.ROOT)
					+ (bounty.count() > 1 ? "s" : "");
		}

		for (ItemStack reward : reward(player, bounty)) {
			if (!player.getInventory().add(reward)) {
				player.spawnAtLocation(player.level(), reward);
			}
		}
		record.bountiesDone.add(key);
		Newsroom.onBountyDone(player, bounty.signature(), deed);
		markCopies(player.getInventory(), issue, slot);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
		return new Result(true, Component.literal(bounty.who() + " thanks you! You received " + Bounties.rewardText(bounty) + ".")
				.withStyle(ChatFormatting.GREEN));
	}

	/** The reward as stacks: an enchanted book or item when the prize carries an enchantment, otherwise as many stacks as needed. */
	private static List<ItemStack> reward(ServerPlayer player, Bounties.Bounty bounty) {
		Item item = BuiltInRegistries.ITEM.getValue(bounty.reward());
		List<ItemStack> stacks = new ArrayList<>();
		if (!bounty.rewardEnchantment().isEmpty()) {
			Holder<Enchantment> enchantment = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(bounty.rewardEnchantment())));
			if (item == Items.ENCHANTED_BOOK) {
				stacks.add(EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, bounty.rewardLevel())));
			} else {
				ItemStack stack = new ItemStack(item);
				stack.enchant(enchantment, bounty.rewardLevel());
				stacks.add(stack);
			}
			return stacks;
		}
		for (int left = bounty.rewardCount(); left > 0; ) {
			int size = Math.min(left, item.getDefaultMaxStackSize());
			stacks.add(new ItemStack(item, size));
			left -= size;
		}
		return stacks;
	}

	private static Result fail(String message) {
		return new Result(false, Component.literal(message).withStyle(ChatFormatting.YELLOW));
	}

	private static String name(Item item) {
		return Component.translatable(item.getDescriptionId()).getString();
	}

	public static int count(Inventory inventory, Item item) {
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void take(Inventory inventory, Item item, int amount) {
		for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int taken = Math.min(amount, stack.getCount());
				stack.shrink(taken);
				amount -= taken;
			}
		}
		inventory.setChanged();
	}

	/** Marks every copy of this issue the player carries, so the page they read shows the bounty as done. */
	private static void markCopies(Inventory inventory, int issue, int slot) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			Edition edition = stack.get(ObserverContent.EDITION);
			if (edition != null && edition.issue() == issue) {
				List<Integer> done = new ArrayList<>(stack.getOrDefault(ObserverContent.HANDED_IN, List.of()));
				if (!done.contains(slot)) {
					done.add(slot);
					stack.set(ObserverContent.HANDED_IN, List.copyOf(done));
				}
			}
		}
	}
}
