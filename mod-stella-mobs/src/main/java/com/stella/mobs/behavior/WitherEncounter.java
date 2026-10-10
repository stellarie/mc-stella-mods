package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Wither that joins a level fresh is an encounter: it is a levelled boss, set by the players present. A Wither read
 * from disk without a {@link WitherState} is a legacy one and stays vanilla.
 */
public final class WitherEncounter {
	/** The vanilla Wither shoots from rank 2, so the pursuit takes the same rank and wins the tie by order. */
	public static final int PURSUE_PRIORITY = 2;

	private WitherEncounter() {
	}

	/** Tags a minion the Wither summoned. */
	public static final String MINION_TAG = "mod-stella-mobs.wither_minion";

	public static boolean isMinion(final Entity entity) {
		return entity.entityTags().contains(MINION_TAG);
	}

	/** True while a rage runs. Volleys and waves wait for it. */
	public static boolean isRaging(final Mob mob) {
		WitherState state = WitherState.find(mob);
		return state != null && state.rageKind() != WitherState.RAGE_NONE;
	}

	public static boolean isEncounter(final Mob mob) {
		return WitherState.hasWitherFlag(mob);
	}

	/** True once the charge-up is over. */
	public static boolean isActive(final Mob mob) {
		WitherState state = WitherState.find(mob);
		return state != null && state.activated();
	}

	/**
	 * The summon level: the base, or the highest player level plus a margin, whichever is higher. Players count within
	 * the scaling floor radius. With none that near, the highest player in the level counts. Spectators never count.
	 */
	public static int levelFor(
		final List<? extends Player> players, final Vec3 at, final ScalingConfig scaling, final WitherConfig config
	) {
		double radiusSquared = (double) scaling.floorRadius() * scaling.floorRadius();
		int near = -1;
		int any = -1;
		for (Player player : players) {
			if (player.isSpectator()) {
				continue;
			}
			any = Math.max(any, player.experienceLevel);
			if (player.position().distanceToSqr(at) <= radiusSquared) {
				near = Math.max(near, player.experienceLevel);
			}
		}
		int highest = near >= 0 ? near : any;
		int base = config.getInt(WitherConfig.LEVEL_BASE);
		if (highest < 0) {
			return base;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(base, (long) highest + config.getInt(WitherConfig.LEVEL_OVER_PLAYER)));
	}

	/** Marks a fresh Wither as an encounter and fixes its level, before the spawn scaling reads it. */
	public static void begin(final Mob wither, final ServerLevel level) {
		int summonLevel = levelFor(level.players(), wither.position(), ScalingConfigStore.get(), WitherConfigStore.get());
		((AttachmentTarget) wither).setAttached(ThreatState.TYPE, new ThreatState(summonLevel, 0, 0, 0));
		WitherState.set(wither, WitherState.fresh());
		WitherStats.apply(wither);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherEncounter BEGIN uuid={} level={}", wither.getUUID(), summonLevel);
	}

	/**
	 * Swaps the vanilla ranged goal and the nearest-living target goal for a pursuit of the nearest player. It runs at
	 * load and never inside a goal tick. A Wither with no state keeps its vanilla goals.
	 */
	public static void install(final Mob mob) {
		if (!(mob instanceof WitherBoss wither) || !isEncounter(mob)) {
			return;
		}
		boolean installed = wither.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof WitherPursueGoal);
		if (installed) {
			return;
		}
		WitherMinions.watch(wither);
		wither.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedAttackGoal);
		wither.getGoalSelector().addGoal(PURSUE_PRIORITY, new WitherPursueGoal(wither));
		wither.getGoalSelector().addGoal(PURSUE_PRIORITY, new WitherVolleyGoal(wither));
		GoalSelector targets = ((MobTargetSelectorAccessor) wither).stellaMobs$targetSelector();
		targets.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
		targets.addGoal(PURSUE_PRIORITY, new NearestAttackableTargetGoal<>(wither, Player.class, false, false));
	}

	/** Runs on the Wither's server tick. The first tick after the charge-up marks it active. */
	public static void tick(final WitherBoss wither) {
		WitherState state = WitherState.find(wither);
		if (state == null || state.activated() || wither.getInvulnerableTicks() > 0) {
			return;
		}
		WitherState.set(wither, state.withActivated(wither.getY()));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherEncounter ACTIVATE uuid={} anchorY={}", wither.getUUID(), wither.getY());
	}

	/**
	 * The highest height the Wither may fly at: the ground under it plus the configured margin. With no ground within
	 * the search depth, the height it stood at on activation stands in for the ground.
	 */
	public static double ceilingFor(final WitherBoss wither) {
		WitherConfig config = WitherConfigStore.get();
		double ground = groundTop(wither.level(), wither.getX(), wither.getY(), wither.getZ(), config.getInt(WitherConfig.MOVE_GROUND_SEARCH));
		WitherState state = WitherState.find(wither);
		if (Double.isNaN(ground)) {
			ground = state == null ? wither.getY() : state.anchorY();
		}
		return ground + config.get(WitherConfig.MOVE_CEILING);
	}

	/** The top of the first solid block at or below the point, or NaN when none lies within the search depth. */
	public static double groundTop(final Level level, final double x, final double y, final double z, final int depth) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
		int top = cursor.getY();
		int floor = Math.max(level.getMinY(), top - depth);
		for (int blockY = top; blockY >= floor; blockY--) {
			cursor.setY(blockY);
			var shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
			if (!shape.isEmpty()) {
				return blockY + shape.max(Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}

	/** Pulls an active Wither back under its ceiling and stops it rising. Vanilla lifts it toward its target. */
	public static void clampToCeiling(final WitherBoss wither) {
		if (!isActive(wither)) {
			return;
		}
		double ceiling = ceilingFor(wither);
		if (wither.getY() <= ceiling) {
			return;
		}
		wither.setPos(wither.getX(), ceiling, wither.getZ());
		Vec3 motion = wither.getDeltaMovement();
		if (motion.y > 0.0) {
			wither.setDeltaMovement(motion.x, 0.0, motion.z);
		}
	}

	/**
	 * Runs after the spawn scaling. The vanilla attribute caps max health, so the part it cannot hold is kept as a divisor
	 * on every hit the Wither takes. The health bar and the rage thresholds still read health over max health.
	 */
	public static void afterScaling(final Mob mob) {
		WitherState state = WitherState.find(mob);
		if (state == null) {
			return;
		}
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		double formula = health.getBaseValue() * SpawnScaling.healthMultiplier(ThreatState.level(mob));
		double divisor = Math.max(1.0, formula / mob.getMaxHealth());
		WitherState.set(mob, state.withHealthDivisor(divisor));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherEncounter SPAWN uuid={} level={} health={} formula={} divisor={}",
			mob.getUUID(), ThreatState.level(mob), mob.getMaxHealth(), formula, divisor);
	}

	/** The Wither is dead: its minions and its skulls in the air go with it. */
	public static void onDeath(final WitherBoss wither) {
		if (isEncounter(wither)) {
			WitherMinions.discardAll(wither);
			WitherShots.discardAll(wither);
			if (wither.level() instanceof ServerLevel level) {
				WitherLoot.drop(level, wither, wither.getRandom());
			}
		}
	}

	/** A hit's damage once the health the attribute could not hold is taken off. */
	public static float scaleDamage(final Mob wither, final float amount) {
		WitherState state = WitherState.find(wither);
		return state != null && state.healthDivisor() > 1.0 ? (float) (amount / state.healthDivisor()) : amount;
	}
}
