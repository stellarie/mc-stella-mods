package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.illager.Pillager;

/** M4: a Pillager holding a melee weapon closes and strikes instead of standing still. */
public class PillagerMeleeGoal extends MeleeAttackGoal {
	public static final int PRIORITY = CrossbowCadenceGoal.ATTACK_PRIORITY;

	public PillagerMeleeGoal(final Pillager pillager) {
		super(pillager, 1.0, true);
	}

	public static boolean canWield(final Mob mob) {
		return false;
	}

	public static void install(final Mob mob) {
	}
}
