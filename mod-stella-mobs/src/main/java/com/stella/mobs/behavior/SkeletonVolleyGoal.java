package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A skeleton archer's bow, in place of the vanilla bow goal at rank 4. Its ordinary shot is one arrow that
 * slows what it hits for two seconds, harder from an elite or a boss. Every six seconds the shot is a volley
 * instead: a narrow fan of flaming arrows, six from a plain skeleton, twelve from an elite and eighteen from
 * a boss, each leaving a cloud where it lands. The first release of a fight is the volley. Each release follows
 * a full bow draw, and the cadence counts the goal's own ticks, so it holds wherever the game clock stands.
 */
public class SkeletonVolleyGoal extends Goal {
	public static final int ATTACK_PRIORITY = 4;
	/** The vanilla bow goal's own range. */
	public static final float RANGE = 15.0F;
	public static final int ARROWS_PER_VOLLEY = 6;
	public static final int ELITE_ARROWS_PER_VOLLEY = 12;
	public static final int BOSS_ARROWS_PER_VOLLEY = 18;
	/** The whole fan's width, left edge to right edge: narrow, so the volley lands as one. */
	public static final double CONE_DEGREES = 12.0;
	public static final int VOLLEY_COOLDOWN_TICKS = 120;
	/** From one release to the next. */
	public static final int SHOT_INTERVAL_TICKS = 40;
	/** The vanilla bow's own draw, so each release is seen coming. */
	public static final int DRAW_TICKS = 20;
	/** How long a volley arrow burns, and so how long it sets its target alight. */
	public static final int FLAME_TICKS = 100;
	private static final float POWER = 1.6F;
	private static final float UNCERTAINTY = 0.0F;
	private static final double BALLISTIC_LIFT = 0.2;
	private static final double TARGET_AIM_HEIGHT = 0.3333333333333333;
	private static final double CHASE_SPEED = 1.0;

	private final Mob mob;
	private int attackTime = DRAW_TICKS;
	/** Ticks since the last volley; starts full, so the first release of a fight is a volley. */
	private int sinceVolley = VOLLEY_COOLDOWN_TICKS;

	public SkeletonVolleyGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Replaces the vanilla bow goal at its own rank, from the installer and never from a goal callback. A
	 * weapon swap re-adds the vanilla goal at this rank, and the earlier insertion wins the tie.
	 */
	public static void install(final Mob mob) {
		if (mob.getType() != EntityTypes.SKELETON || BossAbilities.copiesOf(mob, SkeletonVolleyGoal.class) > 0) {
			return;
		}

		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedBowAttackGoal);
		mob.getGoalSelector().addGoal(ATTACK_PRIORITY, new SkeletonVolleyGoal(mob));
	}

	/** How many arrows this skeleton's volley holds. */
	public static int arrowsFor(final Mob mob) {
		if (Bosses.isBoss(mob)) {
			return BOSS_ARROWS_PER_VOLLEY;
		}
		return Elite.isElite(mob) ? ELITE_ARROWS_PER_VOLLEY : ARROWS_PER_VOLLEY;
	}

	/** The angle between neighbouring arrows of a volley. */
	public static double spacingDegrees(final int arrows) {
		return arrows <= 1 ? 0.0 : CONE_DEGREES / (arrows - 1);
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && this.mob.isHolding(Items.BOW);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		super.start();
		this.mob.setAggressive(true);
	}

	@Override
	public void stop() {
		super.stop();
		this.mob.setAggressive(false);
		this.mob.getNavigation().stop();
		this.mob.stopUsingItem();
		this.attackTime = DRAW_TICKS;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}

		this.sinceVolley = Math.min(VOLLEY_COOLDOWN_TICKS, this.sinceVolley + 1);
		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean inReach = this.mob.distanceToSqr(target) <= (double) (RANGE * RANGE)
			&& this.mob.getSensing().hasLineOfSight(target);
		if (!inReach) {
			this.mob.stopUsingItem();
			this.mob.getNavigation().moveTo(target, CHASE_SPEED);
			return;
		}

		this.mob.getNavigation().stop();
		--this.attackTime;
		if (!this.mob.isUsingItem() && this.attackTime <= DRAW_TICKS) {
			this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
		}

		if (this.attackTime <= 0) {
			this.mob.stopUsingItem();
			this.attackTime = SHOT_INTERVAL_TICKS;
			if (this.sinceVolley >= VOLLEY_COOLDOWN_TICKS) {
				this.sinceVolley = 0;
				this.loose(target, arrowsFor(this.mob), true);
			} else {
				this.loose(target, 1, false);
			}
		}
	}

	/** One release: a single slowing arrow, or a volley's fan of flaming ones. One ammo item per arrow. */
	private void loose(final LivingEntity target, final int count, final boolean volley) {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack bow = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
		// Monster.getProjectile reads the off hand first, so a tipped stack tips every arrow.
		ItemStack ammo = this.mob.getProjectile(bow);
		double xd = target.getX() - this.mob.getX();
		double zd = target.getZ() - this.mob.getZ();
		double lift = Math.sqrt(xd * xd + zd * zd) * BALLISTIC_LIFT;
		double turn = Math.toRadians(spacingDegrees(count));
		List<UUID> arrows = new ArrayList<>();
		List<Vec3> velocities = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			double angle = (i - (count - 1) / 2.0) * turn;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			ItemStack one = ammo.copyWithCount(1);
			Arrow arrow = new Arrow(level, this.mob, one, bow);
			if (volley) {
				arrow.addTag(SkeletonVolley.VOLLEY_TAG);
				arrow.igniteForTicks(FLAME_TICKS);
			} else {
				arrow.addTag(SkeletonVolley.SLOW_TAG);
			}
			double yd = target.getY(TARGET_AIM_HEIGHT) - arrow.getY();
			Projectile.spawnProjectileUsingShoot(
				arrow, level, one, xd * cos - zd * sin, yd + lift, xd * sin + zd * cos, POWER, UNCERTAINTY
			);
			if (level.getEntity(arrow.getUUID()) == arrow) {
				arrows.add(arrow.getUUID());
				velocities.add(arrow.getDeltaMovement());
			}
		}

		ammo.shrink(count);
		this.mob.playSound(
			SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F)
		);
		if (volley) {
			SkeletonVolley.recorder().volley(this.mob, arrows, velocities);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=skeletonVolley VOLLEY tick={} arrows={} target={}",
				this.mob.tickCount, arrows.size(), String.format(Locale.ROOT, "%.1f", Math.sqrt(this.mob.distanceToSqr(target)))
			);
		} else {
			SkeletonVolley.recorder().shot(this.mob, arrows);
		}
	}
}
