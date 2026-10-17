package dev.spacebod.herald.delivery;

import dev.spacebod.herald.Herald;
import dev.spacebod.herald.HeraldContent;
import dev.spacebod.herald.edition.Edition;
import dev.spacebod.herald.news.Ledger;
import dev.spacebod.herald.news.Newsroom;
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
 * Gets each printed edition into each player's hands. A delivery dog is sent out of sight and walks it over; if
 * no hidden spot with a route to the player turns up within a few seconds, or he takes too long, the paper
 * goes straight into the inventory instead. A player who logs off before it arrives gets it on their return.
 */
public final class Delivery {
	/** Starting spots to try (one a second) before giving up on a delivery dog. */
	private static final int SPAWN_ATTEMPTS = 6;
	/** A delivery dog who has not arrived by then is called off. */
	private static final int TIMEOUT_TICKS = 90 * 20;
	/** Ticks after a player joins before a delivery dog sets out, so the world around them has loaded. */
	private static final int JOIN_DELAY_TICKS = 5 * 20;

	private static final Map<UUID, Order> ORDERS = new HashMap<>();

	private static final class Order {
		final Edition edition;
		final int startTick;
		int attempts;
		@Nullable UUID dog;

		Order(Edition edition, int startTick) {
			this.edition = edition;
			this.startTick = startTick;
		}
	}

	private Delivery() {
	}

	public static void send(ServerPlayer player, Edition edition) {
		if (!Herald.config().deliveryDog) {
			hand(player, edition);
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
				DeliveryDog dog = order.attempts < SPAWN_ATTEMPTS ? DeliveryDog.dispatch(player, stack(order.edition), order.edition.issue()) : null;
				if (dog != null) {
					order.dog = dog.getUUID();
				} else if (++order.attempts >= SPAWN_ATTEMPTS) {
					hand(player, order.edition);
					it.remove();
				}
				continue;
			}
			Entity found = findDog(server, order.dog);
			if (found instanceof DeliveryDog dog && dog.delivered()) {
				it.remove();
			} else if (found == null || now - order.startTick > TIMEOUT_TICKS) {
				// Unloaded, lost or stuck: the paper turns up in the inventory and the delivery dog heads home.
				dismiss(server, order);
				hand(player, order.edition);
				it.remove();
			}
		}
	}

	/** A delivery dog found no way to his player from where he appeared; another spot is tried next second. */
	static void routeFailed(ServerPlayer player) {
		Order order = ORDERS.get(player.getUUID());
		if (order != null) {
			order.dog = null;
			order.attempts++;
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
		player.sendSystemMessage(Component.translatable("message.spacebod_herald.arrived", issue).withStyle(ChatFormatting.GOLD), true);
	}

	public static ItemStack stack(Edition edition) {
		ItemStack stack = new ItemStack(HeraldContent.NEWSPAPER);
		stack.set(HeraldContent.EDITION, edition);
		return stack;
	}

	/** A copy for this player: with their Exchange hand-ins already marked, for reprints. */
	public static ItemStack stack(Edition edition, ServerPlayer player) {
		ItemStack stack = stack(edition);
		Ledger.PlayerRecord record = Newsroom.record(player);
		if (record != null) {
			List<Integer> done = record.bountiesDone.stream()
					.filter(key -> key.startsWith(edition.issue() + ":"))
					.map(key -> Integer.parseInt(key.substring(key.indexOf(':') + 1)))
					.toList();
			if (!done.isEmpty()) {
				stack.set(HeraldContent.HANDED_IN, done);
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
