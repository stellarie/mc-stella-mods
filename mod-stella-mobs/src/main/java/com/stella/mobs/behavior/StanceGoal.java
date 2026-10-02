package com.stella.mobs.behavior;

import java.util.Locale;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Reads the player that a mob hunts and sets the mob's {@link Stance}. The goal carries no flags and never runs. The
 * selector asks it about every second tick, and each ask that falls due runs one assessment and answers false. This is
 * the same poll that the learned band uses.
 *
 * <p>An assessment reads three things. The mob's health share counts against a line that its allies lower. The player's
 * health share counts against a line, with the shield bonus. The third is whether the player chases. {@link StanceRules}
 * turns them into a stance. Two rules then hold the stance steady. A retreat that has just ended leaves the mob in KITE for
 * {@link #KITE_TICKS}, even if the player still chases. A mob that enters RETREAT or PURSUE stays there for at least
 * {@link #DWELL_TICKS}. Every time counts the mob's own ticks, because the level clock barely moves in a test.
 */
public class StanceGoal<T extends PathfinderMob> extends Goal {
	public static final int ASSESS_TICKS = 5;
	public static final int DWELL_TICKS = 40;
	public static final int KITE_TICKS = 100;

	private final T mob;

	public StanceGoal(final T mob) {
		this.mob = mob;
	}

	@Override
	public boolean canUse() {
		// A mob that never hunted a player has no state.
		StanceState state = this.mob.getTarget() instanceof Player ? StanceState.get(this.mob) : StanceState.peek(this.mob);
		if (state == null || this.mob.tickCount < state.nextAssessTick()) {
			return false;
		}

		state.setNextAssessTick(this.mob.tickCount + ASSESS_TICKS);
		this.assess(state);
		return false;
	}

	private void assess(final StanceState state) {
		if (!(this.mob.getTarget() instanceof Player player) || !player.isAlive()) {
			state.setTargetId(null);
			state.reset(this.mob, "no player target");
			return;
		}

		if (!player.getUUID().equals(state.targetId())) {
			state.reset(this.mob, "new target");
			state.setTargetId(player.getUUID());
		}

		int now = this.mob.tickCount;
		double mobShare = this.mob.getHealth() / (double) this.mob.getMaxHealth();
		// Allies only lower the line.
		// Above the lone line, a count changes nothing.
		int allies = mobShare <= StanceRules.MORALE_ALONE + StanceRules.HYSTERESIS ? this.countAllies(player) : 0;
		boolean mobLow = StanceRules.mobLow(state.mobLow(), mobShare, allies);
		double playerShare = PlayerReadout.effectiveShare(player, this.mob);
		boolean playerLow = StanceRules.playerLow(state.playerLow(), playerShare);
		boolean chasing = PlayerReadout.chasing(player, this.mob);
		Stance wanted = StanceRules.decide(true, mobLow, playerLow, chasing, this.mob instanceof Zombie);
		if (wanted == Stance.RETREAT && now < state.kiteUntilTick()) {
			wanted = Stance.KITE;
		}

		Stance current = state.stance();
		boolean dwelling = (current == Stance.RETREAT || current == Stance.PURSUE) && now - state.sinceTick() < DWELL_TICKS;
		if (dwelling) {
			wanted = current;
		}

		state.setFlags(mobLow, playerLow);
		if (wanted != current) {
			state.change(this.mob, wanted, String.format(
				Locale.ROOT, "mobShare=%.2f playerShare=%.2f chasing=%b allies=%d", mobShare, playerShare, chasing, allies
			));
		}
	}

	/** The allies of this mob. */
	private int countAllies(final Player player) {
		double radiusSqr = StanceRules.MORALE_RADIUS * StanceRules.MORALE_RADIUS;
		return this.mob.level().getEntitiesOfClass(
			Monster.class,
			this.mob.getBoundingBox().inflate(StanceRules.MORALE_RADIUS),
			other -> other != this.mob && other.isAlive() && other.getTarget() == player && this.mob.distanceToSqr(other) <= radiusSqr
		).size();
	}
}
