package com.stella.mobs.shield;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The loaded Shield Generators and the boxes they guard. A box is {@code 2 * HALF_SIZE + 1} blocks wide on each axis,
 * centered on its generator, in any dimension. The registry lives in memory and follows the block entities as the
 * world loads and drops them.
 */
public final class ShieldZones {
	/** Blocks from the generator to the edge of its box, edge included. */
	public static final int HALF_SIZE = 16;
	private static final Set<ShieldGeneratorBlockEntity> GENERATORS = ConcurrentHashMap.newKeySet();

	private ShieldZones() {
	}

	public static void register() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ShieldGeneratorBlockEntity generator) {
				GENERATORS.add(generator);
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ShieldGeneratorBlockEntity generator) {
				GENERATORS.remove(generator);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GENERATORS.clear());
	}

	/** True when {@code pos} lies inside the box of a live generator in {@code level}. */
	public static boolean covers(final Level level, final BlockPos pos) {
		if (GENERATORS.isEmpty()) {
			return false;
		}
		for (ShieldGeneratorBlockEntity generator : GENERATORS) {
			if (generator.isRemoved()) {
				GENERATORS.remove(generator);
				continue;
			}
			if (generator.getLevel() == level && inside(generator.getBlockPos(), pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean covers(final Entity entity) {
		return covers(entity.level(), entity.blockPosition());
	}

	/** True when {@code pos} lies in the box centered on {@code center}. */
	public static boolean inside(final BlockPos center, final BlockPos pos) {
		return Math.abs(pos.getX() - center.getX()) <= HALF_SIZE
			&& Math.abs(pos.getY() - center.getY()) <= HALF_SIZE
			&& Math.abs(pos.getZ() - center.getZ()) <= HALF_SIZE;
	}

	/** True when a hostile mob may not enter its level at its position. */
	public static boolean refusesSpawn(final Entity entity) {
		return entity instanceof Mob && entity instanceof Enemy && covers(entity);
	}

	/**
	 * True when {@code mob} may not hold {@code target}. Aggro stays free when both stand inside a box,
	 * or both stand outside. Across the edge, any mob refuses a player inside,
	 * and a hostile mob inside refuses every target outside.
	 */
	public static boolean refusesTarget(final Mob mob, final LivingEntity target) {
		if (target == null || GENERATORS.isEmpty()) {
			return false;
		}
		boolean mobInside = covers(mob);
		boolean targetInside = covers(target);
		if (mobInside == targetInside) {
			return false;
		}
		return targetInside ? target instanceof Player : mob instanceof Enemy;
	}

	/** The number of live generators, for tests. */
	public static int liveCount() {
		return (int) GENERATORS.stream().filter(generator -> !generator.isRemoved()).count();
	}
}
