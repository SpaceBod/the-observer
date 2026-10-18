package dev.spacebod.observer.stand;

import dev.spacebod.observer.ObserverContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stores nothing of its own. Its job is to tell clients which printing is on sale and who already has it, so
 * each player's game shows a paper in the window only while theirs is waiting.
 */
public class NewsstandBlockEntity extends BlockEntity {
	/** What the stand's lamps show a player. */
	public enum Lamp {
		/** No edition yet. */
		OFF,
		/** Their copy is waiting. */
		WAITING,
		/** They have the copy on sale. */
		CLAIMED,
		/** The next edition is at the press. */
		PRINTING
	}

	private int printing;
	private boolean atPress;
	private Set<String> collectors = Set.of();

	public NewsstandBlockEntity(BlockPos pos, BlockState state) {
		super(ObserverContent.NEWSSTAND_ENTITY, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			Newsstands.loaded(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		Newsstands.unloaded(this);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("printing", Newsstands.printingOnSale());
		tag.putBoolean("at_press", Newsstands.status() == Newsstands.Status.AT_PRESS);
		tag.putString("collectors", String.join(",", Newsstands.collectors()));
		return tag;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		printing = input.getIntOr("printing", 0);
		atPress = input.getBooleanOr("at_press", false);
		String list = input.getStringOr("collectors", "");
		collectors = list.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(list.split(",")));
	}

	/** Client side: whether this player's copy of the current printing is still waiting in the window. */
	public boolean paperWaitingFor(UUID player) {
		return printing > 0 && !collectors.contains(player.toString());
	}

	/** Client side: the lamps' colour for this player. */
	public Lamp lampFor(UUID player) {
		if (atPress) {
			return Lamp.PRINTING;
		}
		if (printing == 0) {
			return Lamp.OFF;
		}
		return paperWaitingFor(player) ? Lamp.WAITING : Lamp.CLAIMED;
	}
}
