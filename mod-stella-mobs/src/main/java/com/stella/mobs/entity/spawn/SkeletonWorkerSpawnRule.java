package com.stella.mobs.entity.spawn;

import com.stella.mobs.entity.SkeletonWorker;
import com.stella.mobs.entity.StellaEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * The skeleton worker rule: a worker spawns only on a spot that lies inside a mineshaft piece.
 * Each cycle a player with mineshaft nearby gets at most one try, and at most {@link #MAX_NEAR_PLAYER}
 * workers stand around the player.
 */
public final class SkeletonWorkerSpawnRule implements SpawnRule {
	public static final SkeletonWorkerSpawnRule INSTANCE = new SkeletonWorkerSpawnRule();
	public static final int MAX_NEAR_PLAYER = 4;
	public static final double CHANCE_PER_CYCLE = 0.25;
	public static final int MIN_DISTANCE = 12;
	public static final int MAX_DISTANCE = 28;
	public static final int TRIES = 12;
	public static final double COUNT_RADIUS = 48.0;

	private SkeletonWorkerSpawnRule() {
	}

	@Override
	public String name() {
		return "skeleton_worker";
	}

	@Override
	public void reset() {
	}

	@Override
	public void cycle(final ServerLevel level, final List<ServerPlayer> players, final long clock) {
		RandomSource random = level.getRandom();
		for (ServerPlayer player : players) {
			if (random.nextDouble() >= CHANCE_PER_CYCLE) {
				continue;
			}
			AABB near = player.getBoundingBox().inflate(COUNT_RADIUS);
			if (level.getEntitiesOfClass(SkeletonWorker.class, near, SkeletonWorker::isAlive).size() >= MAX_NEAR_PLAYER) {
				continue;
			}
			trySpawn(level, player, random);
		}
	}

	/** Whether the spot lies inside a piece of a mineshaft. */
	public static boolean inMineshaft(final ServerLevel level, final BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, StructureTags.MINESHAFT);
		return start != null && start.isValid();
	}

	private boolean trySpawn(final ServerLevel level, final ServerPlayer player, final RandomSource random) {
		for (int attempt = 0; attempt < TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
			int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = player.getBlockY() + random.nextInt(9) - 4;
			BlockPos pos = new BlockPos(x, y, z);

			if (!inMineshaft(level, pos)
				|| !SpawnPlacements.isSpawnPositionOk(StellaEntities.SKELETON_WORKER, level, pos)
				|| !level.noCollision(StellaEntities.SKELETON_WORKER.getSpawnAABB(x + 0.5, y, z + 0.5))) {
				continue;
			}

			SkeletonWorker worker = StellaEntities.SKELETON_WORKER.create(level, EntitySpawnReason.EVENT);
			if (worker == null) {
				return false;
			}
			worker.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			worker.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			if (level.addFreshEntity(worker)) {
				return true;
			}
		}
		return false;
	}
}
