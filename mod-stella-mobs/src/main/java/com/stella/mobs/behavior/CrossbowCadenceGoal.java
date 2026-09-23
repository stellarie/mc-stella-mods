package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * T02: a crossbow mob fires fully charged, as fast as its attack speed allows. Vanilla never reads
 * ATTACK_SPEED here: its goal charges through CrossbowItem.getChargeDuration and then holds a plain
 * random 20 + nextInt(20) ticks. This goal replaces it at its own rank, keeps the charge cycle, drops
 * the random hold, and scales the interval by the attack speed attribute.
 */
public class CrossbowCadenceGoal extends Goal {
	/** Vanilla's crossbow goal rank, which this goal takes: it replaces the goal and preempts nothing. */
	public static final int ATTACK_PRIORITY = 2;
	/** The vanilla hold this goal drops, at attack speed 1.0. Kept so a test can state what was removed. */
	public static final int REMOVED_HOLD_TICKS = 20;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final Monster mob;
	private final CrossbowAttackMob crossbowMob;
	private boolean charging;
	private boolean charged;
	private int holdTicks = -1;

	public CrossbowCadenceGoal(final Monster mob) {
		this.mob = mob;
		this.crossbowMob = (CrossbowAttackMob) mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Replaces vanilla's crossbow goal at its own rank. The removal runs here and not from a goal
	 * callback: a goal that mutates the selector while the selector iterates it corrupts that iteration.
	 */
	public static void install(final Mob mob) {
		if (!(mob instanceof Monster monster) || !(mob instanceof CrossbowAttackMob) || !holdsCrossbow(mob)) {
			return;
		}

		if (mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof CrossbowCadenceGoal)) {
			return;
		}

		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedCrossbowAttackGoal);
		mob.getGoalSelector().addGoal(ATTACK_PRIORITY, new CrossbowCadenceGoal(monster));
	}

	/** The exemption's own question: the mob carries a crossbow, so its shot governs and no band does. */
	public static boolean holdsCrossbow(final Mob mob) {
		return mob.getMainHandItem().is(Items.CROSSBOW) || mob.getOffhandItem().is(Items.CROSSBOW);
	}

	/**
	 * The delay at this mob's attack speed, in ticks, which is the interval vanilla spends on a random
	 * hold. A pillager carries no attack speed instance, so the read falls back to the value the game
	 * registers for the attribute itself: getAttributeValue refuses the missing attribute outright.
	 */
	public static int holdTicks(final Mob mob) {
		AttributeInstance instance = mob.getAttribute(Attributes.ATTACK_SPEED);
		double attacksPerSecond = instance == null
			? Attributes.ATTACK_SPEED.value().getDefaultValue()
			: instance.getValue();
		return Math.max(1, (int) Math.ceil((double) REMOVED_HOLD_TICKS / attacksPerSecond));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && holdsCrossbow(this.mob);
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
		this.crossbowMob.setChargingCrossbow(false);
		this.charging = false;
		this.charged = false;
		this.holdTicks = -1;

		if (this.mob.isUsingItem()) {
			this.mob.stopUsingItem();
		}
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

		this.mob.getLookControl().setLookAt(target, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		this.mob.getNavigation().stop();

		if (!this.charging && !this.charged) {
			startCharge();
			return;
		}

		if (this.charging) {
			chargeTick();
			return;
		}

		// The random hold is what vanilla spends here, and this goal spends a shorter, fixed one instead.
		if (--this.holdTicks > 0) {
			return;
		}

		this.charged = false;
		this.holdTicks = -1;

		if (target.isAlive()) {
			((RangedAttackMob) this.mob).performRangedAttack(target, 1.0F);
		}
	}

	private void startCharge() {
		ItemStack crossbow = this.mob.getMainHandItem();

		if (!CrossbowItem.isCharged(crossbow)) {
			// The hand comes from the weapon, so an off hand crossbow charges where it is held.
			this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.CROSSBOW));
			this.charging = true;
			this.crossbowMob.setChargingCrossbow(true);
			return;
		}

		this.charged = true;
		this.holdTicks = holdTicks(this.mob);
	}

	/**
	 * One tick of the vanilla charge cycle: the use item's own charge duration decides when the shot is
	 * ready, through the entity aware call, so an enchantment that shortens the charge still counts.
	 */
	private void chargeTick() {
		if (!this.mob.isUsingItem()) {
			this.charging = false;
			return;
		}

		ItemStack useItem = this.mob.getUseItem();

		if (this.mob.getTicksUsingItem() < CrossbowItem.getChargeDuration(useItem, this.mob)) {
			return;
		}

		this.mob.releaseUsingItem();
		this.charging = false;
		this.charged = true;
		this.holdTicks = holdTicks(this.mob);
		this.crossbowMob.setChargingCrossbow(false);
	}
}
