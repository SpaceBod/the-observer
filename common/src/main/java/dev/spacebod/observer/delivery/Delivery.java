package dev.spacebod.observer.delivery;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.news.Ledger;
import dev.spacebod.observer.news.Newsroom;
import dev.spacebod.observer.stand.Newsstands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gets each printed edition to the players who have the Pawstman deliver it. He sets out from somewhere out of
 * sight (or beside the reader, if there is nowhere hidden) and, like a tamed wolf, teleports to them when the way
 * is blocked. A reader he can't reach for five minutes, or who sends him away, collects their copy at a newsstand
 * instead. A player who logs off before he arrives gets him on their return.
 */
public final class Delivery {
	/**
	 * Like a tamed wolf, the Pawstman keeps trying to reach a reader who has nowhere for him to stand (flying,
	 * swimming, on a pillar); after this long the copy waits at the newsstands instead.
	 */
	private static final int GIVE_UP_TICKS = 5 * 60 * 20;
	/** Ticks after a player joins before a delivery dog sets out, so the world around them has loaded. */
	private static final int JOIN_DELAY_TICKS = 5 * 20;

	private static final Map<UUID, Order> ORDERS = new HashMap<>();

	private static final class Order {
		final Edition edition;
		final int startTick;
		@Nullable UUID dog;

		Order(Edition edition, int startTick) {
			this.edition = edition;
			this.startTick = startTick;
		}
	}

	private Delivery() {
	}

	public static void send(ServerPlayer player, Edition edition) {
		if (Newsstands.collectsAtStand(player)) {
			player.sendSystemMessage(Component.translatable("message.spacebod_observer.stand.on_sale", edition.issue()).withStyle(ChatFormatting.GOLD));
			Newsroom.markOffered(player);
			return;
		}
		Order previous = ORDERS.put(player.getUUID(), new Order(edition, player.level().getServer().getTickCount() + JOIN_DELAY_TICKS));
		if (previous != null) {
			dismiss(player.level().getServer(), previous);
		}
	}

	public static void tick(MinecraftServer server) {
		if (ORDERS.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Order>> it = ORDERS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Order> entry = it.next();
			Order order = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				dismiss(server, order); // logged off: they get it next time they join
				it.remove();
				continue;
			}
			int now = server.getTickCount();
			if (now < order.startTick) {
				continue;
			}
			if (order.dog == null) {
				if (now % 20 != 0) {
					continue;
				}
				DeliveryDog dog = DeliveryDog.dispatch(player, stack(order.edition, player), order.edition.issue());
				if (dog != null) {
					order.dog = dog.getUUID();
				} else if (now - order.startTick > GIVE_UP_TICKS) {
					fallBack(player, order.edition);
					it.remove();
				}
				continue;
			}
			Entity found = findDog(server, order.dog);
			if (found instanceof DeliveryDog dog && (dog.delivered() || dog.gaveUp())) {
				it.remove();
			} else if (found instanceof DeliveryDog dog && dog.awaitingPayment()) {
				continue; // at the reader's heels; it decides when to give up
			} else if (now - order.startTick > GIVE_UP_TICKS) {
				dismiss(server, order);
				fallBack(player, order.edition);
				it.remove();
			} else if (found == null) {
				order.dog = null; // unloaded or lost: another sets out
			}
		}
	}

	/** The Pawstman never got there: a failed delivery; the copy is collected at a newsstand. */
	private static void fallBack(ServerPlayer player, Edition edition) {
		player.sendSystemMessage(Component.translatable("message.spacebod_observer.pawstman.no_route", edition.issue()).withStyle(ChatFormatting.GRAY));
		Newsroom.markOffered(player);
	}

	/** Calls off this player's delivery, if one is on its way: they have collected their copy themselves. */
	public static void cancel(ServerPlayer player) {
		Order order = ORDERS.remove(player.getUUID());
		if (order != null) {
			dismiss(player.level().getServer(), order);
		}
	}

	/** The Pawstman found no way to his reader and nowhere beside them to stand; he sets out again next second. */
	static void routeFailed(ServerPlayer player) {
		Order order = ORDERS.get(player.getUUID());
		if (order != null) {
			order.dog = null;
		}
	}

	/** Puts a copy straight into the inventory (or at their feet if it is full). */
	public static void hand(ServerPlayer player, Edition edition) {
		ItemStack stack = stack(edition, player);
		if (!player.getInventory().add(stack)) {
			player.spawnAtLocation(player.level(), stack);
		}
		received(player, edition.issue());
	}

	/** Called once the paper is in the player's hands, or at their feet. */
	static void received(ServerPlayer player, int issue) {
		Newsroom.markReceived(player, issue);
		player.sendSystemMessage(Component.translatable("message.spacebod_observer.arrived", issue).withStyle(ChatFormatting.GOLD), true);
	}

	public static ItemStack stack(Edition edition) {
		ItemStack stack = new ItemStack(ObserverContent.NEWSPAPER);
		stack.set(ObserverContent.EDITION, edition);
		return stack;
	}

	/** A copy for this player: with their own Exchange notices, and their hand-ins already marked for reprints. */
	public static ItemStack stack(Edition printed, ServerPlayer player) {
		Edition edition = Newsroom.withReader(printed, player);
		ItemStack stack = stack(edition);
		Ledger.PlayerRecord record = Newsroom.record(player);
		if (record != null) {
			List<Integer> done = record.bountiesDone.stream()
					.filter(key -> key.startsWith(edition.issue() + ":"))
					.map(key -> Integer.parseInt(key.substring(key.indexOf(':') + 1)))
					.toList();
			if (!done.isEmpty()) {
				stack.set(ObserverContent.HANDED_IN, done);
			}
		}
		return stack;
	}

	public static void clear() {
		ORDERS.clear();
	}

	private static void dismiss(MinecraftServer server, Order order) {
		if (order.dog != null && findDog(server, order.dog) instanceof DeliveryDog dog) {
			dog.leave();
		}
	}

	private static @Nullable Entity findDog(MinecraftServer server, UUID id) {
		for (var level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}
}
