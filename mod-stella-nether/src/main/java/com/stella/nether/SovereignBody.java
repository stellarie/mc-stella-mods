package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Sovereign never passes through blocks. Whatever it moves itself into, in a dive or a charge, it breaks
 * as it goes, dropping nothing; only unbreakable blocks stand. If it is ever shut in and suffocating, it
 * bursts: an explosion that breaks the blocks around it, and whatever the explosion leaves is broken too.
 */
public final class SovereignBody {
	public static final float SUFFOCATION_BLAST = 4.0F;
	/** How far past its body the burst clears what the explosion left. */
	public static final double SUFFOCATION_CLEAR = 1.0;

	/** How many times each Sovereign has burst out, by its identity; read by tests. */
	private static final Map<UUID, Integer> BURSTS = new ConcurrentHashMap<>();

	private SovereignBody() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.is(DamageTypes.IN_WALL) && entity instanceof Blaze blaze && BlazeSovereign.isSovereign(blaze)
				&& blaze.level() instanceof ServerLevel level) {
				burst(level, blaze);
				return false;
			}
			return true;
		});
	}

	/** Breaks every breakable block the box touches, dropping nothing. What was broken, for the record. */
	public static List<BlockPos> carve(final ServerLevel level, final Blaze blaze, final AABB box) {
		List<BlockPos> broken = new ArrayList<>();
		BlockPos.betweenClosedStream(box.deflate(1.0E-4)).map(BlockPos::immutable).forEach(at -> {
			BlockState state = level.getBlockState(at);
			if (!state.getCollisionShape(level, at).isEmpty() && state.getDestroySpeed(level, at) >= 0.0F && level.destroyBlock(at, false, blaze, 512)) {
				broken.add(at);
			}
		});
		return broken;
	}

	/** Moves the Sovereign by a step, breaking what stands where it is going first. */
	public static List<BlockPos> moveThrough(final ServerLevel level, final Blaze blaze, final double x, final double y, final double z) {
		AABB next = blaze.getBoundingBox().move(x - blaze.getX(), y - blaze.getY(), z - blaze.getZ());
		List<BlockPos> broken = carve(level, blaze, next);
		blaze.setPos(x, y, z);
		return broken;
	}

	static void burst(final ServerLevel level, final Blaze blaze) {
		double y = blaze.getY() + blaze.getBbHeight() * 0.5;
		level.explode(blaze, blaze.getX(), y, blaze.getZ(), SUFFOCATION_BLAST, Level.ExplosionInteraction.BLOCK);
		carve(level, blaze, blaze.getBoundingBox().inflate(SUFFOCATION_CLEAR));
		int count = BURSTS.merge(blaze.getUUID(), 1, Integer::sum);
		BehaviorLog.info("[NETHER] sovereign suffocating, burst out at={} bursts={}", blaze.blockPosition(), count);
	}

	public static int burstsOf(final Blaze blaze) {
		return BURSTS.getOrDefault(blaze.getUUID(), 0);
	}
}
