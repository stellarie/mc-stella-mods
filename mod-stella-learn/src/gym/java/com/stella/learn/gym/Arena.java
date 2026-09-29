package com.stella.learn.gym;

import com.stella.learn.LearnLog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One fight arena: a 16 by 16 by 16 open space over a floor of two dirt layers on two stone layers, sealed in barrier
 * blocks. The origin is the lowest corner of the open space.
 */
public final class Arena {
	public static final int OPEN = 16;
	public static final int FLOOR = 4;
	/** Blocks written straight, with no neighbour updates: the client update flag alone. */
	private static final int FLAGS = 2;

	private final int index;
	private final ServerLevel level;
	private final BlockPos origin;
	private final AABB bounds;

	public Arena(final int index, final ServerLevel level, final BlockPos origin) {
		this.index = index;
		this.level = level;
		this.origin = origin;
		this.bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + OPEN, origin.getY() + OPEN, origin.getZ() + OPEN);
	}

	public int index() {
		return index;
	}

	public ServerLevel level() {
		return level;
	}

	public BlockPos origin() {
		return origin;
	}

	/** The open space; the floor lies below it. */
	public AABB bounds() {
		return bounds;
	}

	/** The y of the floor surface, where an entity stands. */
	public double surfaceY() {
		return origin.getY();
	}

	public boolean contains(final Vec3 at) {
		return at.x >= bounds.minX && at.x < bounds.maxX && at.y >= bounds.minY - 1.0 && at.y < bounds.maxY && at.z >= bounds.minZ && at.z < bounds.maxZ;
	}

	private BlockState expected(final int dx, final int dy, final int dz) {
		if (dy >= 0) {
			return Blocks.AIR.defaultBlockState();
		}

		return dy >= -2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/** Writes the barrier shell, the floor, and the open space. Run once, when the arena is made. */
	public void build() {
		long start = System.nanoTime();
		BlockState barrier = Blocks.BARRIER.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int blocks = 0;
		for (int dx = -1; dx <= OPEN; dx++) {
			for (int dy = -FLOOR - 1; dy <= OPEN; dy++) {
				for (int dz = -1; dz <= OPEN; dz++) {
					boolean shell = dx == -1 || dx == OPEN || dz == -1 || dz == OPEN || dy == -FLOOR - 1 || dy == OPEN;
					pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					level.setBlock(pos, shell ? barrier : expected(dx, dy, dz), FLAGS);
					blocks++;
				}
			}
		}

		double ms = (System.nanoTime() - start) / 1.0e6;
		GymLog.info(GymLog.ARENA, "BUILD", "arena=" + index + " origin=" + origin.toShortString() + " blocks=" + blocks + " ms=" + LearnLog.num(ms));
	}

	/**
	 * Restores every floor and open-space block and removes every entity inside the bounds except players. Returns the
	 * milliseconds it took.
	 */
	public double reset() {
		long start = System.nanoTime();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (int dx = 0; dx < OPEN; dx++) {
			for (int dy = -FLOOR; dy < OPEN; dy++) {
				for (int dz = 0; dz < OPEN; dz++) {
					pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState want = expected(dx, dy, dz);
					if (level.getBlockState(pos) != want) {
						level.setBlock(pos, want, FLAGS);
						changed++;
					}
				}
			}
		}

		int removed = clearEntities();
		double ms = (System.nanoTime() - start) / 1.0e6;
		GymLog.info(GymLog.ARENA, "RESET", "arena=" + index + " changed=" + changed + " removed=" + removed + " ms=" + LearnLog.num(ms));
		return ms;
	}

	/** Removes every non-player entity in the open space and the floor, arrows and drops included. */
	public int clearEntities() {
		AABB box = bounds.expandTowards(0.0, -FLOOR, 0.0).inflate(0.5, 0.0, 0.5);
		List<Entity> entities = level.getEntities((Entity) null, box, entity -> !(entity instanceof Player));
		for (Entity entity : entities) {
			entity.discard();
		}

		return entities.size();
	}

	/** A standing point on the floor surface, at least `margin` blocks from every wall. */
	public Vec3 randomSpot(final RandomSource random, final double margin) {
		double span = OPEN - 2.0 * margin;
		return new Vec3(origin.getX() + margin + random.nextDouble() * span, origin.getY(), origin.getZ() + margin + random.nextDouble() * span);
	}
}
