package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;

/**
 * G05 to G08 for the snow golem. It keeps its snow out of the sky, its ball carries the cold, it arrives
 * elite one time in ten, and an elite's ball is a literal point of damage with a cold snap around the
 * target. A snow golem is not an Enemy, so the arrival rolls here rather than through the shipped path.
 */
public final class SnowGolemCold {
	public static final Identifier TYPE = BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.SNOW_GOLEM);
	/** G07: the one in ten the shipped config carries for this mob under its own per-type key. */
	public static final double DEFAULT_CHANCE = 0.10;
	public static final int FREEZE_TICKS = 100;
	public static final int SLOW_TICKS = 100;
	public static final int SLOW_AMPLIFIER = 0;
	/** G08: a literal, so neither an attribute nor the level's damage share can move it. */
	public static final float ELITE_BALL_DAMAGE = 1.0F;
	/** G08: the cold snap is measured from the target the ball hit, not from the thrower. */
	public static final double COLD_SNAP_RADIUS = 4.0;

	private SnowGolemCold() {
	}

	/**
	 * G05: indoors is vanilla's own shelter test, a sky light level below fifteen over the golem's own
	 * block, and it only means something in a dimension that has a sky. Vanilla's melt rule is the
	 * environment attribute gameplay/snow_golem_melts, which the desert, the badlands, the savanna and the
	 * nether set true, so a cold biome needs no code here and the nether keeps melting as it should.
	 */
	public static boolean keepsItsSnow(final Mob mob) {
		return mob.level().dimensionType().hasSkyLight() && !mob.level().canSeeSky(mob.blockPosition());
	}

	/**
	 * G07: the arrival. The shipped roll refuses a snow golem because Elite.canBeElite requires Enemy, so
	 * the chance is read from the same per-type config key and the same shared marker is written.
	 */
	public static void rollElite(final Mob mob) {
		if (!(mob instanceof SnowGolem golem) || EliteState.isSummoned(golem) || Elite.isElite(golem)) {
			return;
		}

		double chance = GearConfigStore.get().rollFor(TYPE).eliteChance();
		if (chance <= 0.0 || !(golem.getRandom().nextFloat() < (float) chance)) {
			return;
		}

		Elite.mark(golem, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=snowGolemElite t={} ARRIVE chance={} health={} elite={}",
			golem.level().getGameTime(), chance, golem.getHealth(), Elite.isElite(golem)
		);
	}

	/** G06 and G08: the payload is added to vanilla's own hit, which still deals its zero. */
	public static void onSnowballHit(final Snowball ball, final Entity hit) {
		if (!(ball.getOwner() instanceof SnowGolem golem) || !(ball.level() instanceof ServerLevel level)) {
			return;
		}

		if (!(hit instanceof LivingEntity target)) {
			return;
		}

		chill(target, golem);
		boolean elite = Elite.isElite(golem);

		if (!elite) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=snowGolemBall t={} HIT target={} elite=false frozen={} slow={} damage={}",
				level.getGameTime(), target.getType(), target.getTicksFrozen(),
				target.hasEffect(MobEffects.SLOWNESS), 0.0F
			);
			return;
		}

		// G08: the one damage in the mod that is a literal. A melee path would have multiplied the golem's
		// attack damage, which the level's own share scales, so the amount is handed to the server as given.
		boolean hurt = target.hurtServer(level, level.damageSources().thrown(ball, golem), ELITE_BALL_DAMAGE);
		int caught = coldSnap(level, target, golem);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=snowGolemBall t={} ELITE_HIT target={} damage={} hurt={} inTheSnap={} radius={}",
			level.getGameTime(), target.getType(), ELITE_BALL_DAMAGE, hurt, caught, COLD_SNAP_RADIUS
		);
	}

	/** G08: freezing and slowness for everything the snap reaches, the target included. */
	private static int coldSnap(final ServerLevel level, final LivingEntity centre, final SnowGolem golem) {
		int caught = 0;

		for (LivingEntity nearby : level.getEntitiesOfClass(
			LivingEntity.class, centre.getBoundingBox().inflate(COLD_SNAP_RADIUS)
		)) {
			if (nearby == golem) {
				continue;
			}

			chill(nearby, golem);
			caught++;
		}

		return caught;
	}

	private static void chill(final LivingEntity target, final SnowGolem golem) {
		if (target.canFreeze()) {
			// The vanilla fullness reading: the freeze lasts FREEZE_TICKS past the point of being frozen.
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + FREEZE_TICKS));
		}

		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER), golem);
	}
}
