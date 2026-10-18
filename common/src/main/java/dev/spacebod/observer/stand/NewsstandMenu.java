package dev.spacebod.observer.stand;

import dev.spacebod.observer.Frequency;
import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.Rewards;
import dev.spacebod.observer.Tone;
import dev.spacebod.observer.edition.Edition;
import dev.spacebod.observer.news.Newsroom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A newsstand's counter. The screen reads everything it shows from the menu's data (synced by the game on its
 * own) and acts through menu buttons, which the server checks; the only slot of its own is the coin slot.
 */
public class NewsstandMenu extends AbstractContainerMenu {
	// Data: values travel as shorts, so anything that can pass 32767 is split into two 15-bit halves.
	public static final int STATUS = 0;
	public static final int ISSUE = 1;
	public static final int DAY = 2;
	public static final int NEXT_DAY = 4;
	/** Real seconds to the next edition, plus one; 0 when unknown or at the press. */
	public static final int SECONDS = 6;
	public static final int COLLECTED = 8;
	public static final int AT_STAND = 9;
	public static final int DOGS_ALLOWED = 10;
	public static final int TONE = 11;
	public static final int EDITOR = 12;
	public static final int PRICE = 13;
	public static final int AUTO = 14;
	public static final int FREQUENCY = 15;
	public static final int REWARDS = 16;
	public static final int FEE = 17;
	/** Emeralds for a reader's copy (the paper's price); 0: free. */
	public static final int OWED = 18;
	public static final int FIRST_FREE = 19;
	private static final int DATA_COUNT = 20;

	// Buttons
	public static final int TAKE = 0;
	public static final int BUY = 1;
	public static final int DELIVER_DOG = 2;
	public static final int DELIVER_STAND = 3;
	/** Followed by one button per tone, in {@link Tone} order. */
	public static final int TONE_FIRST = 10;
	public static final int DOGS_TOGGLE = 20;
	public static final int PUBLISH = 21;
	public static final int AUTO_TOGGLE = 22;
	/** Followed by one button per frequency, in {@link Frequency} order. */
	public static final int FREQUENCY_FIRST = 30;
	/** Followed by one button per level, in {@link Rewards} order. */
	public static final int REWARDS_FIRST = 40;
	public static final int FEE_CYCLE = 23;
	public static final int FIRST_FREE_TOGGLE = 24;

	// Layout, shared with the screen
	public static final int WIDTH = 240;
	public static final int HEIGHT = 250;
	public static final int COIN_X = 49;
	public static final int COIN_Y = 104;
	public static final int INVENTORY_X = 39;
	public static final int INVENTORY_Y = 168;

	private final ContainerLevelAccess access;
	private final Container coins = new SimpleContainer(1);
	private final ContainerData data;
	private final Player player;

	/** Client side: the data arrives from the server. */
	public NewsstandMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
	}

	public NewsstandMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		this(id, inventory, access, new Live((ServerPlayer) inventory.player));
	}

	private NewsstandMenu(int id, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
		super(ObserverContent.NEWSSTAND_MENU, id);
		this.access = access;
		this.data = data;
		this.player = inventory.player;
		addSlot(new Slot(coins, 0, COIN_X, COIN_Y) {
			@Override
			public boolean isActive() {
				return coinSlotOpen();
			}

			@Override
			public boolean mayPlace(ItemStack stack) {
				return coinSlotOpen() && stack.is(Items.EMERALD);
			}
		});
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
		addDataSlots(data);
	}

	/** Whether an edition is on sale here. */
	public boolean onSale() {
		return get(STATUS) != Newsstands.Status.AWAITING_FIRST.ordinal() && get(ISSUE) > 0;
	}

	/** Whether there is anything to pay for: the reader's own copy, or a spare once they have it. */
	public boolean coinSlotOpen() {
		return onSale() && (get(COLLECTED) == 1 ? get(PRICE) > 0 : get(OWED) > 0);
	}

	@Override
	public void broadcastChanges() {
		// Nothing to pay for any more: emeralds left in the closed slot go back to the reader.
		if (!coinSlotOpen() && !coins.getItem(0).isEmpty() && player instanceof ServerPlayer) {
			clearContainer(player, coins);
		}
		super.broadcastChanges();
	}

	public int get(int index) {
		return data.get(index);
	}

	/** A value sent as two halves. */
	public long getWide(int index) {
		return ((long) data.get(index + 1) << 15) | data.get(index);
	}

	public boolean hasCoins() {
		return !coins.getItem(0).isEmpty();
	}

	/** Emeralds in the coin slot. */
	public int coins() {
		return coins.getItem(0).getCount();
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		if (!(player instanceof ServerPlayer server)) {
			return true; // the client's copy; the server decides
		}
		boolean editor = Newsstands.editor(server);
		switch (button) {
			case TAKE -> Newsstands.take(server, coins.getItem(0));
			case BUY -> Newsstands.buy(server, coins.getItem(0));
			case DELIVER_DOG -> Newsstands.setDelivery(server, true);
			case DELIVER_STAND -> Newsstands.setDelivery(server, false);
			case DOGS_TOGGLE -> {
				if (editor) {
					Newsstands.setDogDelivery(server.level().getServer(), !Observer.config().deliveryDog);
				}
			}
			case PUBLISH -> {
				if (editor) {
					Newsroom.publish(server.level().getServer());
				}
			}
			case AUTO_TOGGLE -> {
				if (editor) {
					Newsstands.setAutoPublish(server.level().getServer(), !Observer.config().autoPublish);
				}
			}
			case FIRST_FREE_TOGGLE -> {
				if (editor) {
					Newsstands.setFirstCopyFree(server.level().getServer(), !Observer.config().firstCopyFree);
				}
			}
			case FEE_CYCLE -> {
				if (editor) {
					Newsstands.setPrice(server.level().getServer(), (Observer.config().paperPrice + 1) % 4);
				}
			}
			default -> {
				int rewards = button - REWARDS_FIRST;
				if (editor && rewards >= 0 && rewards < Rewards.values().length) {
					Newsstands.setRewards(server.level().getServer(), Rewards.values()[rewards]);
					break;
				}
				int frequency = button - FREQUENCY_FIRST;
				if (editor && frequency >= 0 && frequency < Frequency.values().length) {
					Newsstands.setFrequency(server.level().getServer(), Frequency.values()[frequency]);
					break;
				}
				int tone = button - TONE_FIRST;
				if (editor && tone >= 0 && tone < Tone.values().length) {
					Newsstands.setTone(server.level().getServer(), Tone.values()[tone]);
				}
			}
		}
		broadcastChanges();
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == 0) {
			if (!moveItemStackTo(stack, 1, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!stack.is(Items.EMERALD) || !coinSlotOpen() || !moveItemStackTo(stack, 0, 1, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, coins));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ObserverContent.NEWSSTAND);
	}

	/** The server's data for one player, worked out each time the game checks it for changes. */
	private record Live(ServerPlayer player) implements ContainerData {
		@Override
		public int get(int index) {
			Edition latest = Newsroom.latest();
			return switch (index) {
				case STATUS -> Newsstands.status().ordinal();
				case ISSUE -> latest != null ? latest.issue() : 0;
				case DAY, DAY + 1 -> half(latest != null ? latest.day() : 0, index - DAY);
				case NEXT_DAY, NEXT_DAY + 1 -> half(Math.max(0, Newsroom.nextEditionDay()), index - NEXT_DAY);
				case SECONDS, SECONDS + 1 -> half(Newsstands.secondsToNextEdition(player.level().getServer()) + 1, index - SECONDS);
				case COLLECTED -> Newsstands.collected(player) ? 1 : 0;
				case AT_STAND -> Newsstands.collectsAtStand(player) ? 1 : 0;
				case DOGS_ALLOWED -> Observer.config().deliveryDog ? 1 : 0;
				case TONE -> Tone.of(Observer.config().tone).ordinal();
				case EDITOR -> Newsstands.editor(player) ? 1 : 0;
				case PRICE -> Observer.config().paperPrice;
				case AUTO -> Observer.config().autoPublish ? 1 : 0;
				case FREQUENCY -> Observer.config().frequency().ordinal();
				case REWARDS -> Rewards.of(Observer.config().exchangeRewards).ordinal();
				case FEE -> Observer.config().paperPrice;
				case OWED -> Newsroom.priceFor(player);
				case FIRST_FREE -> Observer.config().firstCopyFree ? 1 : 0;
				default -> 0;
			};
		}

		private static int half(long value, int which) {
			long clamped = Math.clamp(value, 0, (1L << 30) - 1);
			return (int) (which == 0 ? clamped & 0x7FFF : clamped >> 15);
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	}
}
