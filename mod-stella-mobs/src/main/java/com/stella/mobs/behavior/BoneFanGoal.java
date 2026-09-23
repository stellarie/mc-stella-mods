package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The boss skeleton's bone fan: five arrows in one spread every 20 ticks, in place of the vanilla bow
 * goal at rank 4. The tip costs no firing code, because the arrow's pickup stack carries it and the
 * ammo path reads the off hand first, so a tipped offhand stack tips every arrow in the fan.
 */
public class BoneFanGoal extends Goal {
	public static final int ATTACK_PRIORITY = 4;
	public static final int ARROWS_PER_VOLLEY = 5;
	public static final double SPREAD_DEGREES = 8.0;
	public static final int INTERVAL_TICKS = 20;
	/** The vanilla bow goal's own range, so the fan reaches exactly as far as the bow it replaces. */
	public static final float RANGE = 15.0F;
	private static final float POWER = 1.6F;
	private static final float UNCERTAINTY = 0.0F;
	private static final double BALLISTIC_LIFT = 0.2;
	private static final double TARGET_AIM_HEIGHT = 0.3333333333333333;

	private final Mob mob;
	private int attackTime = -1;

	public BoneFanGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Replaces the vanilla bow goal at its own rank, and adds the fan there instead. The removal runs
	 * here and not from a goal callback: a goal that mutates the selector while the selector iterates it
	 * corrupts that iteration. A weapon swap re-adds the vanilla goal at this rank, and the earlier
	 * insertion wins the tie, so the fan keeps rank 4.
	 */
	public static void install(final Mob mob) {
		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedBowAttackGoal);
		mob.getGoalSelector().addGoal(ATTACK_PRIORITY, new BoneFanGoal(mob));
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
		this.attackTime = -1;
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

		if (this.mob.distanceToSqr(target) > (double) (RANGE * RANGE) || !this.mob.getSensing().hasLineOfSight(target)) {
			return;
		}

		if (--this.attackTime <= 0) {
			this.attackTime = INTERVAL_TICKS;
			this.loose(target);
		}
	}

	/** One volley: the whole spread, then one ammo item spent per arrow. */
	private void loose(final LivingEntity target) {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack bow = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
		// Monster.getProjectile reads the off hand first and falls back to a plain arrow, so the fan
		// weakens to plain arrows when the tipped stack runs out.
		ItemStack ammo = this.mob.getProjectile(bow);
		double xd = target.getX() - this.mob.getX();
		double zd = target.getZ() - this.mob.getZ();
		double lift = Math.sqrt(xd * xd + zd * zd) * BALLISTIC_LIFT;
		double turn = Math.toRadians(SPREAD_DEGREES);

		for (int i = 0; i < ARROWS_PER_VOLLEY; i++) {
			double angle = (i - (ARROWS_PER_VOLLEY - 1) / 2.0) * turn;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			ItemStack one = ammo.copyWithCount(1);
			Arrow arrow = new Arrow(level, this.mob, one, bow);
			double yd = target.getY(TARGET_AIM_HEIGHT) - arrow.getY();
			Projectile.spawnProjectileUsingShoot(
				arrow, level, one, xd * cos - zd * sin, yd + lift, xd * sin + zd * cos, POWER, UNCERTAINTY
			);
		}

		ammo.shrink(ARROWS_PER_VOLLEY);
		this.mob.playSound(
			SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F)
		);
	}
}
