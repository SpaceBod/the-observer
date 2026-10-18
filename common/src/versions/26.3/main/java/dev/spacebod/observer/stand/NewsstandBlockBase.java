package dev.spacebod.observer.stand;

import net.minecraft.world.level.block.BaseEntityBlock;

/** Version overlay: blocks in this series have no codec. */
abstract class NewsstandBlockBase extends BaseEntityBlock {
	NewsstandBlockBase(Properties properties) {
		super(properties);
	}
}
