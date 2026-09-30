package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;

/**
 * The boss spider's brood: the first time its health reaches half it births four cave spiders, and the
 * durable flag is set before the spawn so a second hit cannot birth twice. The spiders take the shipped
 * create-and-flag recipe, so none of them can roll an elite or a squad of its own.
 */
public final class BroodMother {
	public static final int BROOD_SIZE = 4;
	public static final double HEALTH_FRACTION = 0.5;
	private static final double SCATTER = 1.5;

	private BroodMother() {
	}

	public static void afterDamage(
		final LivingEntity entity,
		final DamageSource source,
		final float dealt,
		final float taken,
		final boolean blocked
	) {
		if (!(entity instanceof Spider spider) || !(spider.level() instanceof ServerLevel level)) {
			return;
		}

		if (!EliteState.hasBossFlag(spider) || EliteState.isBroodBorn(spider)) {
			return;
		}

		if (spider.getHealth() > spider.getMaxHealth() * HEALTH_FRACTION) {
			return;
		}

		EliteState.setBroodBorn(spider, true);

		for (int i = 0; i < BROOD_SIZE; i++) {
			spawnCaveSpider(level, spider);
		}

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=broodBirthsFour BIRTH size={} health={} max={}",
			BROOD_SIZE, spider.getHealth(), spider.getMaxHealth()
		);
	}

	private static void spawnCaveSpider(final ServerLevel level, final Spider mother) {
		CaveSpider spider = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.EVENT);
		if (spider == null) {
			return;
		}

		RandomSource random = spider.getRandom();
		spider.snapTo(
			mother.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			mother.getY(),
			mother.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
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
	}
}
