package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** M4: a Pillager holding a melee weapon closes and strikes instead of standing still. */
public class PillagerMeleeGoal extends MeleeAttackGoal {
	// The vanilla crossbow rank; the hand check keeps this goal and the crossbow goal from ever both wanting it.
	public static final int PRIORITY = CrossbowCadenceGoal.ATTACK_PRIORITY;
	public static final double SPEED = 1.0;

	private final Pillager pillager;

	public PillagerMeleeGoal(final Pillager pillager) {
		super(pillager, SPEED, true);
		this.pillager = pillager;
	}

	/** True when the main hand holds something to swing: not empty and not a bow or crossbow. */
	public static boolean canWield(final Mob mob) {
		ItemStack hand = mob.getMainHandItem();
		return !hand.isEmpty() && !hand.is(Items.CROSSBOW) && !hand.is(Items.BOW);
	}

	/** Installed on every Pillager, so a weapon picked up after spawn is used too. Idempotent. */
	public static void install(final Mob mob) {
		if (!(mob instanceof Pillager pillager)) {
			return;
		}

		if (mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof PillagerMeleeGoal)) {
			return;
		}

		mob.getGoalSelector().addGoal(PRIORITY, new PillagerMeleeGoal(pillager));
	}

	@Override
	public boolean canUse() {
		return canWield(this.pillager) && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return canWield(this.pillager) && super.canContinueToUse();
	}

	@Override
	public void start() {
		super.start();
		LivingEntity target = this.pillager.getTarget();
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=pillagerMelee START tick={} weapon={} target={}",
			this.pillager.tickCount, this.pillager.getMainHandItem().getItem(), target == null ? "none" : target.getType()
		);
	}

	@Override
	public void stop() {
		super.stop();
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=pillagerMelee STOP tick={} weapon={}", this.pillager.tickCount, this.pillager.getMainHandItem().getItem()
		);
	}
}
