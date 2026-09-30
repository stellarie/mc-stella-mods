package com.stella.mobs.entity.spawn;

import com.stella.mobs.entity.Ghost;
import com.stella.mobs.entity.StellaEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * The ghost rule: a player below y=0 with no ghost of their own may draw one. A ghost that has not
 * yet attached counts, so one player never draws two. The despawn is the normal mob despawn.
 */
public final class GhostSpawnRule implements SpawnRule {
	public static final GhostSpawnRule INSTANCE = new GhostSpawnRule();
	public static final int MAX_Y = 0;
	public static final double CHANCE_PER_CYCLE = 0.1;
	public static final int MIN_DISTANCE = 10;
	public static final int MAX_DISTANCE = 18;
	public static final double FREE_GHOST_RADIUS = 48.0;
	public static final int TRIES = 12;

	private GhostSpawnRule() {
	}

	@Override
	public String name() {
		return "ghost";
	}

	@Override
	public void reset() {
		Ghost.clearAttachments();
	}

	@Override
	public void cycle(final ServerLevel level, final List<ServerPlayer> players, final long clock) {
		RandomSource random = level.getRandom();
		for (ServerPlayer player : players) {
			if (player.getBlockY() >= MAX_Y || Ghost.hasGhost(player.getUUID()) || random.nextDouble() >= CHANCE_PER_CYCLE) {
				continue;
			}

			AABB near = player.getBoundingBox().inflate(FREE_GHOST_RADIUS);
			boolean freeGhost = !level.getEntitiesOfClass(
				Ghost.class, near, ghost -> ghost.isAlive() && ghost.attachedPlayer() == null
			).isEmpty();
			if (!freeGhost) {
				trySpawn(level, player, random);
			}
		}
	}

	private boolean trySpawn(final ServerLevel level, final ServerPlayer player, final RandomSource random) {
		for (int attempt = 0; attempt < TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			double y = player.getY() + random.nextInt(5) - 2;
			BlockPos pos = BlockPos.containing(x, y, z);

			if (pos.getY() >= MAX_Y || !level.noCollision(StellaEntities.GHOST.getSpawnAABB(x, y, z))) {
				continue;
			}

			Ghost ghost = StellaEntities.GHOST.create(level, EntitySpawnReason.EVENT);
			if (ghost == null) {
				return false;
			}
			ghost.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
			ghost.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			if (level.addFreshEntity(ghost)) {
				return true;
			}
		}
		return false;
	}
}
