package dev.spacebod.observer.stand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The newsstand: a vending box on a post. Using it opens its counter, where players collect or buy a copy. */
public class NewsstandBlock extends NewsstandBlockBase {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** The paint, changed by using a dye on it. */
	public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

	public NewsstandBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOR, DyeColor.RED));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, COLOR);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new NewsstandBlockEntity(pos, state);
	}

	/** A dye repaints the stand, like dyeing a sheep. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
										  BlockHitResult hitResult) {
		DyeColor dye = stack.get(DataComponents.DYE);
		if (dye == null) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		if (state.getValue(COLOR) == dye) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(COLOR, dye), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			if (player instanceof ServerPlayer reader) {
				Newsstands.hint(reader);
			}
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new NewsstandMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
					Component.translatable("container.spacebod_observer.newsstand")));
		}
		return InteractionResult.SUCCESS;
	}
}
