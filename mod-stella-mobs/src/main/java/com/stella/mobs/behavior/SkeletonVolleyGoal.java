package com.stella.mobs.behavior;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.stella.mobs.StellaMobs;
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
 * M2: a plain skeleton's volley, three arrows six degrees apart every forty ticks, in place of the vanilla
 * bow goal at rank 4. Each volley follows a full bow draw, so a player sees it coming. Every arrow carries the volley tag, so it leaves a cloud where it lands. The
 * cadence counts the goal's own ticks, so it holds wherever the game clock stands.
 */
public class SkeletonVolleyGoal extends Goal {
	public static final int ARROWS_PER_VOLLEY = 3;
	public static final double SPREAD_DEGREES = 6.0;
	public static final int INTERVAL_TICKS = 40;
	/** The vanilla bow's own draw, so a volley is seen coming and each release follows a full draw. */
	public static final int DRAW_TICKS = 20;
	private static final float POWER = 1.6F;
	private static final float UNCERTAINTY = 0.0F;
	private static final double BALLISTIC_LIFT = 0.2;
	private static final double TARGET_AIM_HEIGHT = 0.3333333333333333;
	private static final double CHASE_SPEED = 1.0;

	private final Mob mob;
	private int attackTime = DRAW_TICKS;

	public SkeletonVolleyGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Replaces the vanilla bow goal at its own rank, from the installer and never from a goal callback. A
	 * weapon swap re-adds the vanilla goal at this rank, and the earlier insertion wins the tie. A boss
	 * never volleys: it keeps the bone fan, whose install removes this goal.
	 */
	public static void install(final Mob mob) {
		if (mob.getType() != EntityTypes.SKELETON || Bosses.isBoss(mob)
			|| BossAbilities.copiesOf(mob, SkeletonVolleyGoal.class) > 0) {
			return;
		}

		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedBowAttackGoal);
		mob.getGoalSelector().addGoal(BoneFanGoal.ATTACK_PRIORITY, new SkeletonVolleyGoal(mob));
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

		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean inReach = this.mob.distanceToSqr(target) <= (double) (BoneFanGoal.RANGE * BoneFanGoal.RANGE)
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
			this.attackTime = INTERVAL_TICKS;
			this.loose(target);
		}
	}

	/** One volley: the spread, then one ammo item spent per arrow, and a record of what was added. */
	private void loose(final LivingEntity target) {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack bow = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
		// Monster.getProjectile reads the off hand first, so a tipped stack tips every arrow in the volley.
		ItemStack ammo = this.mob.getProjectile(bow);
		double xd = target.getX() - this.mob.getX();
		double zd = target.getZ() - this.mob.getZ();
		double lift = Math.sqrt(xd * xd + zd * zd) * BALLISTIC_LIFT;
		double turn = Math.toRadians(SPREAD_DEGREES);
		List<UUID> arrows = new ArrayList<>();
		List<Vec3> velocities = new ArrayList<>();

		for (int i = 0; i < ARROWS_PER_VOLLEY; i++) {
			double angle = (i - (ARROWS_PER_VOLLEY - 1) / 2.0) * turn;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			ItemStack one = ammo.copyWithCount(1);
			Arrow arrow = new Arrow(level, this.mob, one, bow);
			arrow.addTag(SkeletonVolley.VOLLEY_TAG);
			double yd = target.getY(TARGET_AIM_HEIGHT) - arrow.getY();
			Projectile.spawnProjectileUsingShoot(
				arrow, level, one, xd * cos - zd * sin, yd + lift, xd * sin + zd * cos, POWER, UNCERTAINTY
			);
			if (level.getEntity(arrow.getUUID()) == arrow) {
				arrows.add(arrow.getUUID());
				velocities.add(arrow.getDeltaMovement());
			}
		}

		ammo.shrink(ARROWS_PER_VOLLEY);
		this.mob.playSound(
			SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F)
		);
		SkeletonVolley.recorder().volley(this.mob, arrows, velocities);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=skeletonVolley VOLLEY tick={} arrows={} target={}",
			this.mob.tickCount, arrows.size(), String.format(Locale.ROOT, "%.1f", Math.sqrt(this.mob.distanceToSqr(target)))
		);
	}
}
