package dev.spacebod.observer.stand;

import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverConfig;
import dev.spacebod.observer.Rewards;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.command.ObserverCommand;
import dev.spacebod.observer.delivery.Delivery;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.news.Ledger;
import dev.spacebod.observer.news.Newsroom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The newsstands' side of the paper: who has collected the current printing, handing out and selling copies,
 * each player's choice of delivery, and the operators' settings. Every loaded stand is told when any of that
 * changes, so the paper in its window appears and disappears for the right players.
 */
public final class Newsstands {
	/** What a stand's status lamp shows. */
	public enum Status {
		AWAITING_FIRST, ON_SALE, AT_PRESS
	}

	private static final Set<NewsstandBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	private Newsstands() {
	}

	static synchronized void loaded(NewsstandBlockEntity stand) {
		LOADED.add(stand);
	}

	static synchronized void unloaded(NewsstandBlockEntity stand) {
		LOADED.remove(stand);
	}

	/** Sends every loaded stand's display (printing on sale, who has it) to the players watching it. */
	public static synchronized void refresh() {
		for (NewsstandBlockEntity stand : List.copyOf(LOADED)) {
			if (stand.getLevel() != null && !stand.isRemoved()) {
				stand.getLevel().sendBlockUpdated(stand.getBlockPos(), stand.getBlockState(), stand.getBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	public static Status status() {
		if (Newsroom.printing()) {
			return Status.AT_PRESS;
		}
		return Newsroom.latest() == null ? Status.AWAITING_FIRST : Status.ON_SALE;
	}

	static int printingOnSale() {
		return Newsroom.printingOnSale();
	}

	static List<String> collectors() {
		return Newsroom.collectors();
	}

	public static boolean collected(ServerPlayer player) {
		return Newsroom.collected(player);
	}

	/** Whether this player collects their paper at a newsstand rather than having it brought. */
	public static boolean collectsAtStand(ServerPlayer player) {
		if (!Observer.config().deliveryDog) {
			return true;
		}
		Ledger.PlayerRecord record = Newsroom.record(player);
		return record != null && "stand".equals(record.delivery);
	}

	/** The player's copy of the current printing, if they haven't had it, for the paper's price. */
	static void take(ServerPlayer player, ItemStack coins) {
		Edition latest = Newsroom.latest();
		if (latest == null || collected(player)) {
			return;
		}
		int owed = Newsroom.priceFor(player);
		if (owed > 0) {
			if (!coins.is(Items.EMERALD) || coins.getCount() < owed) {
				return;
			}
			coins.shrink(owed);
		}
		Delivery.cancel(player);
		Delivery.hand(player, latest);
		dispensed(player);
		player.sendSystemMessage(Component.translatable("message.spacebod_observer.stand.taken", latest.issue()).withStyle(ChatFormatting.GOLD), true);
		changed();
	}

	/** Another copy for the price in emeralds, paid from the stand's coin slot. False if not enough was put in. */
	static boolean buy(ServerPlayer player, ItemStack coins) {
		Edition latest = Newsroom.latest();
		int price = Observer.config().paperPrice;
		if (latest == null || !coins.is(Items.EMERALD) && price > 0 || coins.getCount() < price) {
			return false;
		}
		coins.shrink(price);
		Delivery.hand(player, latest);
		dispensed(player);
		player.sendSystemMessage(Component.translatable("message.spacebod_observer.stand.bought", latest.issue()).withStyle(ChatFormatting.GOLD), true);
		changed();
		return true;
	}

	static void setDelivery(ServerPlayer player, boolean dog) {
		Ledger.PlayerRecord record = Newsroom.record(player);
		if (record == null || dog && !Observer.config().deliveryDog) {
			return;
		}
		record.delivery = dog ? "dog" : "stand";
		Newsroom.save();
		player.sendSystemMessage(Component.translatable(dog ? "message.spacebod_observer.stand.prefer_dog" : "message.spacebod_observer.stand.prefer_stand")
				.withStyle(ChatFormatting.GRAY), true);
	}

	/** The first time a Pawstman reader uses a newsstand, a word that they could collect here instead. */
	static void hint(ServerPlayer player) {
		Ledger.PlayerRecord record = Newsroom.record(player);
		if (record != null && !record.standHinted && !collectsAtStand(player)) {
			record.standHinted = true;
			player.sendSystemMessage(Component.translatable("message.spacebod_observer.stand.hint").withStyle(ChatFormatting.GRAY));
		}
	}

	public static void setFirstCopyFree(MinecraftServer server, boolean on) {
		ObserverConfig config = Observer.config();
		if (config.firstCopyFree == on) {
			return;
		}
		config.firstCopyFree = on;
		config.save();
		broadcast(server, Component.translatable(on ? "message.spacebod_observer.first_free_on" : "message.spacebod_observer.first_free_off")
				.withStyle(ChatFormatting.GOLD));
	}

	/** Operators and the owner of a singleplayer world. */
	public static boolean editor(ServerPlayer player) {
		return ObserverCommand.editor(player.createCommandSourceStack());
	}

	public static void setTone(MinecraftServer server, Tone tone) {
		ObserverConfig config = Observer.config();
		if (config.tone.equals(tone.id)) {
			return;
		}
		config.tone = tone.id;
		config.save();
		broadcast(server, Component.translatable("message.spacebod_observer.tone", tone.title).withStyle(ChatFormatting.GOLD));
	}

	public static void setDogDelivery(MinecraftServer server, boolean on) {
		ObserverConfig config = Observer.config();
		if (config.deliveryDog == on) {
			return;
		}
		config.deliveryDog = on;
		config.save();
		broadcast(server, Component.translatable(on ? "message.spacebod_observer.dogs_on" : "message.spacebod_observer.dogs_off").withStyle(ChatFormatting.GOLD));
	}

	public static void setAutoPublish(MinecraftServer server, boolean on) {
		ObserverConfig config = Observer.config();
		if (config.autoPublish == on) {
			return;
		}
		config.autoPublish = on;
		config.save();
		broadcast(server, Component.translatable(on ? "message.spacebod_observer.auto_on" : "message.spacebod_observer.auto_off",
				config.frequency().id).withStyle(ChatFormatting.GOLD));
	}

	public static void setRewards(MinecraftServer server, Rewards rewards) {
		ObserverConfig config = Observer.config();
		if (Rewards.of(config.exchangeRewards) == rewards) {
			return;
		}
		config.exchangeRewards = rewards.id;
		config.save();
		broadcast(server, Component.translatable("message.spacebod_observer.rewards", rewards.title.toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GOLD));
	}

	public static void setPrice(MinecraftServer server, int emeralds) {
		ObserverConfig config = Observer.config();
		config.paperPrice = Math.clamp(emeralds, 0, 64);
		config.save();
		broadcast(server, Component.translatable("message.spacebod_observer.price",
				config.paperPrice == 0 ? "nothing" : config.paperPrice + (config.paperPrice == 1 ? " emerald" : " emeralds")).withStyle(ChatFormatting.GOLD));
	}

	public static void setFrequency(MinecraftServer server, Frequency frequency) {
		ObserverConfig config = Observer.config();
		if (config.frequency() == frequency) {
			return;
		}
		config.frequency = frequency.id;
		config.save();
		Newsroom.reschedule(server);
		broadcast(server, Component.translatable("message.spacebod_observer.frequency", frequency.id).withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Real seconds until the next edition is due (sunrise on its day, at the game's normal speed), 0 if it is due
	 * now, or -1 while one is at the press or before the newsroom has seen the world.
	 */
	public static long secondsToNextEdition(MinecraftServer server) {
		long nextDay = Newsroom.nextEditionDay();
		if (Newsroom.printing() || nextDay < 0 || !Observer.config().autoPublish) {
			return -1;
		}
		long dueTick = (nextDay - 1) * Newsroom.TICKS_PER_DAY;
		long now = server.overworld().getOverworldClockTime();
		return Math.max(0, dueTick - now) / 20;
	}

	/** A new edition is on the stands: every loaded stand rings its bell and sparkles, so anyone nearby notices. */
	public static synchronized void newEdition() {
		for (NewsstandBlockEntity stand : List.copyOf(LOADED)) {
			if (stand.getLevel() instanceof ServerLevel level && !stand.isRemoved()) {
				BlockPos pos = stand.getBlockPos();
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 14, 0.35, 0.45, 0.35, 0.0);
				level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 1.5F);
				level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
		}
		refresh();
	}

	/** The current printing was handed to someone: their stands' windows change. */
	public static void changed() {
		refresh();
	}

	/** The stand drops a copy: a page rustle and the till's chime. */
	private static void dispensed(ServerPlayer player) {
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4F, 1.6F);
	}

	private static void broadcast(MinecraftServer server, Component message) {
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			player.sendSystemMessage(message);
		}
	}
}
