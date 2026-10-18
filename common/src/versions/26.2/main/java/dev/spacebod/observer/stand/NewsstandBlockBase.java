package dev.spacebod.observer.stand;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** Version overlay: blocks in this series declare a codec. */
abstract class NewsstandBlockBase extends BaseEntityBlock {
	private static final MapCodec<NewsstandBlock> CODEC = simpleCodec(NewsstandBlock::new);

	NewsstandBlockBase(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
}
