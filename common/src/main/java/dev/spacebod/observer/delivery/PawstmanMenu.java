package dev.spacebod.observer.delivery;

import dev.spacebod.observer.Observer;
import dev.spacebod.observer.ObserverContent;
import dev.spacebod.observer.news.Newsroom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Paying the Pawstman: his fee goes in the coin slot, then the reader pays and takes the paper, or turns him away. */
public class PawstmanMenu extends AbstractContainerMenu {
	public static final int FEE = 0;
	public static final int ISSUE = 1;
	/** 1 if this is the reader's first copy, on the house. */
	public static final int FIRST = 2;
	/** What a copy costs after the first. */
	public static final int PRICE = 3;
	public static final int PAY = 0;
	public static final int DECLINE = 1;

	public static final int WIDTH = 190;
	public static final int HEIGHT = 168;
	public static final int COIN_X = 20;
	public static final int COIN_Y = 52;
	public static final int INVENTORY_X = 15;
	public static final int INVENTORY_Y = 87;

	private final @Nullable DeliveryDog dog;
	private final Container coins = new SimpleContainer(1);
	private final ContainerData data;

	/** Client side. */
	public PawstmanMenu(int id, Inventory inventory) {
		this(id, inventory, null, new SimpleContainerData(4));
	}

	PawstmanMenu(int id, Inventory inventory, DeliveryDog dog) {
		this(id, inventory, dog, new SimpleContainerData(4));
		data.set(FEE, dog.fee());
		data.set(ISSUE, dog.issue());
		data.set(FIRST, inventory.player instanceof ServerPlayer reader && Newsroom.firstCopy(reader) ? 1 : 0);
		data.set(PRICE, Observer.config().paperPrice);
	}

	private PawstmanMenu(int id, Inventory inventory, @Nullable DeliveryDog dog, ContainerData data) {
		super(ObserverContent.PAWSTMAN_MENU, id);
		this.dog = dog;
		this.data = data;
		addSlot(new Slot(coins, 0, COIN_X, COIN_Y) {
			@Override
			public boolean isActive() {
				return fee() > 0;
			}

			@Override
			public boolean mayPlace(ItemStack stack) {
				return fee() > 0 && stack.is(Items.EMERALD);
			}
		});
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
		addDataSlots(data);
	}

	public int fee() {
		return data.get(FEE);
	}

	public int issue() {
		return data.get(ISSUE);
	}

	public boolean firstCopy() {
		return data.get(FIRST) == 1;
	}

	public int price() {
		return data.get(PRICE);
	}

	public boolean enoughCoins() {
		return coins.getItem(0).getCount() >= fee();
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		if (!(player instanceof ServerPlayer reader) || dog == null) {
			return true;
		}
		if (button == PAY && enoughCoins()) {
			coins.getItem(0).shrink(fee());
			dog.paid(reader);
			reader.closeContainer();
		} else if (button == DECLINE) {
			dog.declined(reader);
			reader.closeContainer();
		}
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
		} else if (!stack.is(Items.EMERALD) || fee() <= 0 || !moveItemStackTo(stack, 0, 1, false)) {
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
		if (player instanceof ServerPlayer) {
			clearContainer(player, coins);
			if (dog != null) {
				dog.counterClosed();
			}
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return dog == null || dog.waitingFor(player);
	}
}
