package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.AABB;

/**
 * R23: an elite spider breeds poison spiders on its own cadence, up to a cap, and stops when it dies.
 * The goal claims no flags, so it can neither starve a vanilla goal nor be starved by one. Its cadence
 * is counted in the goal's own ticks, because the harness clock barely advances. The cap counts only
 * summoned cave spiders near this spider, so a wild cave spider is never mistaken for brood.
 */
public class SpiderBroodGoal extends Goal {
	public static final int PRIORITY = 6;
	public static final int CADENCE_TICKS = 100;
	public static final int CAP = 4;
	// Six blocks, because the test runner packs neighbouring structures about eleven blocks apart, and a
	// wider box counts another rig's summoned cave spiders as this spider's brood.
	public static final double NEIGHBOUR_RADIUS = 6.0;
	private static final double SCATTER = 1.5;

	private final Spider spider;
	private final Set<UUID> spawned = new HashSet<>();
	private int ticks;

	public SpiderBroodGoal(final Spider spider) {
		this.spider = spider;
	}

	/** The identities this goal spawned itself, so a neighbour's brood can never appear in the answer. */
	public Set<UUID> spawnedIds() {
		return Set.copyOf(this.spawned);
	}

	@Override
	public boolean canUse() {
		return !this.spider.isRemoved() && Elite.isElite(this.spider);
	}

	@Override
	public boolean canContinueToUse() {
		return !this.spider.isRemoved() && this.spider.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.ticks++;

		if (this.ticks % CADENCE_TICKS != 0) {
			return;
		}

		if (!(this.spider.level() instanceof ServerLevel level)) {
			return;
		}

		int alive = broodNear(level);
		if (alive >= CAP) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=spiderBrood HOLD cadence={} cap={} alive={} tick={}",
				CADENCE_TICKS, CAP, alive, this.ticks
			);
			return;
		}

		spawnPoisonSpider(level);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=spiderBrood SPAWN cadence={} cap={} aliveBefore={} tick={}",
			CADENCE_TICKS, CAP, alive, this.ticks
		);
	}

	/** Only summoned cave spiders near this spider count, so a wild one never consumes the cap. */
	private int broodNear(final ServerLevel level) {
		AABB box = this.spider.getBoundingBox().inflate(NEIGHBOUR_RADIUS);
		return level.getEntities(EntityTypes.CAVE_SPIDER, box, EliteState::isSummoned).size();
	}

	private void spawnPoisonSpider(final ServerLevel level) {
		CaveSpider spider = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.EVENT);
		if (spider == null) {
			return;
		}

		RandomSource random = spider.getRandom();
		spider.snapTo(
			this.spider.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			this.spider.getY(),
			this.spider.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		spider.finalizeSpawn(
			level, level.getCurrentDifficultyAt(spider.blockPosition()), EntitySpawnReason.EVENT, null
		);
		// Both flags must exist before the add: ENTITY_LOAD fires inside addFreshEntity.
		EliteState.setSummoned(spider, true);
		ReactiveState.get(spider).setGearApplied(true);
		level.addFreshEntity(spider);
		this.spawned.add(spider.getUUID());
	}
}
