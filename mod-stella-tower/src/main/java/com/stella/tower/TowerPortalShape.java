package com.stella.tower;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A crying obsidian frame, found from a spot inside it, the way vanilla finds an obsidian one: a rectangle
 * from 2 by 3 to 21 by 21 inside, closed on all four sides, empty but for fire.
 */
public record TowerPortalShape(Direction.Axis axis, BlockPos bottomLeft, int width, int height) {
	public static final int MIN_WIDTH = 2;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_SIZE = 21;

	public static Optional<TowerPortalShape> find(final LevelAccessor level, final BlockPos inside) {
		if (!isEmpty(level.getBlockState(inside))) {
			return Optional.empty();
		}
		for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
			Optional<TowerPortalShape> shape = find(level, inside, axis);
			if (shape.isPresent()) {
				return shape;
			}
		}
		return Optional.empty();
	}

	private static Optional<TowerPortalShape> find(final LevelAccessor level, final BlockPos inside, final Direction.Axis axis) {
		Direction left = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
		Direction right = left.getOpposite();
		BlockPos bottom = inside;
		for (int i = 0; i < MAX_SIZE && isEmpty(level.getBlockState(bottom.below())); i++) {
			bottom = bottom.below();
		}
		if (!isFrame(level.getBlockState(bottom.below()))) {
			return Optional.empty();
		}
		BlockPos corner = bottom;
		for (int i = 0; i < MAX_SIZE && isEmpty(level.getBlockState(corner.relative(left))); i++) {
			corner = corner.relative(left);
		}
		if (!isFrame(level.getBlockState(corner.relative(left)))) {
			return Optional.empty();
		}
		int width = 0;
		while (width <= MAX_SIZE && isEmpty(level.getBlockState(corner.relative(right, width)))) {
			width++;
		}
		int height = 0;
		while (height <= MAX_SIZE && isEmpty(level.getBlockState(corner.above(height)))) {
			height++;
		}
		if (width < MIN_WIDTH || width > MAX_SIZE || height < MIN_HEIGHT || height > MAX_SIZE) {
			return Optional.empty();
		}
		TowerPortalShape shape = new TowerPortalShape(axis, corner, width, height);
		return shape.isClosed(level, right) ? Optional.of(shape) : Optional.empty();
	}

	private boolean isClosed(final LevelAccessor level, final Direction right) {
		for (int w = -1; w <= this.width; w++) {
			for (int h = -1; h <= this.height; h++) {
				BlockPos pos = this.bottomLeft.relative(right, w).above(h);
				boolean edge = w == -1 || w == this.width || h == -1 || h == this.height;
				boolean corner = (w == -1 || w == this.width) && (h == -1 || h == this.height);
				BlockState state = level.getBlockState(pos);
				if (corner) {
					continue;
				}
				if (edge ? !isFrame(state) : !isEmpty(state)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Fills the frame with the blue portal. */
	public void createPortal(final LevelAccessor level) {
		Direction right = this.axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		BlockState portal = TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
		for (int w = 0; w < this.width; w++) {
			for (int h = 0; h < this.height; h++) {
				level.setBlock(this.bottomLeft.relative(right, w).above(h), portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}

	public static boolean isFrame(final BlockState state) {
		return state.is(Blocks.CRYING_OBSIDIAN);
	}

	private static boolean isEmpty(final BlockState state) {
		return state.isAir() || state.getBlock() instanceof BaseFireBlock;
	}
}
