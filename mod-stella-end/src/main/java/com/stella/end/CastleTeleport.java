package com.stella.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a castle mob may blink to, and the blink itself. A landing spot is safe when the whole mob fits there, with
 * whatever rides it, something solid lies just under its feet, no fluid fills the cell, no other creature stands in
 * it, and the cell lies inside the castle volume. The wither skeleton chase, the archer escape, and any later castle
 * mob that blinks share this class.
 */
public final class CastleTeleport {
	public static final double NEAR_MIN = 2.5;
	public static final double NEAR_MAX = 4.5;
	/** How far above and below the target feet a landing is tried. */
	private static final int RISE = 2;
	private static final int DROP = 3;
	/** A mezzanine ledge stands six blocks over a floor, so an escape scans this far each way. */
	private static final int LEDGE_SCAN = 7;
	private static final int ATTEMPTS = 32;
	/** The floor is looked for this far under the feet: a spot sits on the top face of a block. */
	private static final double GROUND_REACH = 0.5;

	private CastleTeleport() {
	}

	/** The whole body of a mover, passengers too, as one box placed with its feet at {@code at}. */
	private static AABB bodyAt(final Entity mover, final Vec3 at) {
		AABB body = mover.getBoundingBox();
		for (Entity rider : mover.getIndirectPassengers()) {
			body = body.minmax(rider.getBoundingBox());
		}
		return body.move(at.subtract(mover.position()));
	}

	public static boolean safe(final ServerLevel level, final Entity mover, final Vec3 at) {
		if (!EndCastle.volumeContains(BlockPos.containing(at))) {
			return false;
		}
		AABB body = bodyAt(mover, at);
		BlockPos under = BlockPos.containing(at.x, at.y - GROUND_REACH, at.z);
		// A chain or a fence post has a floor too small to stand on, so the block under the middle must be whole on top.
		if (!level.noCollision(mover, body) || !level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
			return false;
		}
		for (int y = Mth.floor(body.minY); y <= Mth.floor(body.maxY); y++) {
			if (!level.getFluidState(BlockPos.containing(at.x, y, at.z)).isEmpty()) {
				return false;
			}
		}
		return level.getEntities(mover, body, other -> other != mover && !mover.hasIndirectPassenger(other) && other.isAlive()).isEmpty();
	}

	/** A safe landing {@link #NEAR_MIN} to {@link #NEAR_MAX} blocks from the target, or null when none turns up. */
	public static @Nullable Vec3 near(final ServerLevel level, final Entity mover, final Entity target) {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double radius = NEAR_MIN + level.getRandom().nextDouble() * (NEAR_MAX - NEAR_MIN);
			int x = Mth.floor(target.getX() + Math.cos(angle) * radius);
			int z = Mth.floor(target.getZ() + Math.sin(angle) * radius);
			Vec3 spot = column(level, mover, x, Mth.floor(target.getY()) + RISE, z, RISE + DROP);
			if (spot != null && spot.distanceTo(target.position()) >= NEAR_MIN) {
				return spot;
			}
		}
		return null;
	}

	/**
	 * A safe landing between {@code min} and {@code max} blocks from the mover, picked as the farthest from the threat
	 * among the spots that turn up. Without a threat the first one serves.
	 */
	public static @Nullable Vec3 away(final ServerLevel level, final Entity mover, final @Nullable Entity threat, final double min, final double max) {
		Vec3 best = null;
		double bestDistance = -1.0;
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double radius = min + level.getRandom().nextDouble() * (max - min);
			int x = Mth.floor(mover.getX() + Math.cos(angle) * radius);
			int z = Mth.floor(mover.getZ() + Math.sin(angle) * radius);
			Vec3 spot = column(level, mover, x, Mth.floor(mover.getY()) + LEDGE_SCAN, z, 2 * LEDGE_SCAN);
			if (spot == null || spot.distanceTo(mover.position()) < min) {
				continue;
			}
			double distance = threat == null ? 0.0 : spot.distanceTo(threat.position());
			if (best == null || distance > bestDistance) {
				best = spot;
				bestDistance = distance;
			}
			if (threat == null) {
				break;
			}
		}
		return best;
	}

	/** The highest safe stand in a column, scanning down from {@code top} for {@code depth} blocks. */
	private static @Nullable Vec3 column(final ServerLevel level, final Entity mover, final int x, final int top, final int z, final int depth) {
		for (int y = top; y >= top - depth; y--) {
			Vec3 spot = new Vec3(x + 0.5, y, z + 0.5);
			if (safe(level, mover, spot)) {
				return spot;
			}
		}
		return null;
	}

	/** Moves the mover, with its riders, to a spot, and clears the path and the speed it had. */
	public static void jump(final ServerLevel level, final Entity mover, final Vec3 to) {
		Vec3 from = mover.position();
		puff(level, from);
		mover.teleportTo(to.x, to.y, to.z);
		mover.setDeltaMovement(Vec3.ZERO);
		mover.fallDistance = 0.0;
		if (mover instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		puff(level, to);
	}

	private static void puff(final ServerLevel level, final Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 16, 0.4, 0.8, 0.4, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.0F);
	}
}
