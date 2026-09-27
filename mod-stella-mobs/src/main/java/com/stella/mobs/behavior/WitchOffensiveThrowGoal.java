package com.stella.mobs.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import org.jspecify.annotations.Nullable;

/**
 * W01 and M11: on aggro the witch splashes invisibility on itself, then throws one random harmful potion
 * at its target every three seconds. W12: an elite witch's throw is its lingering poison cloud whenever
 * that cloud's own rearm allows. The cadence counts the goal's own ticks, so it holds wherever the game
 * clock stands. No flags, so it never contends for MOVE or LOOK.
 */
public class WitchOffensiveThrowGoal<T extends Witch> extends Goal {
	public static final int OFFENSE_INTERVAL_TICKS = 60;
	public static final double THROW_RANGE = 10.0;
	private static final int ACTION_TICKS = 10;

	/** W12: one cloud per rearm, because the cloud itself lives 600 ticks. */
	public static final int LINGER_REARM_TICKS = 600;

	private final T witch;
	private final WitchBag<WitchPotions.Carried> bag = new WitchBag<>(WitchPotions.offensive());
	private final WitchBag<WitchPotions.Carried> eliteBag = new WitchBag<>(WitchPotions.eliteOffensive());
	private @Nullable LivingEntity target;
	private int ticksToThrow = OFFENSE_INTERVAL_TICKS;
	private int ticksToLinger;

	public WitchOffensiveThrowGoal(final T witch) {
		this.witch = witch;
	}

	@Override
	public boolean canUse() {
		LivingEntity found = this.liveTarget();
		if (found == null) {
			return false;
		}

		this.target = found;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.liveTarget() != null;
	}

	private @Nullable LivingEntity liveTarget() {
		if (!ReactiveState.get(this.witch).playerInRange(this.witch)) {
			return null;
		}

		LivingEntity found = this.witch.getTarget();
		if (found == null || !found.isAlive() || this.witch.distanceToSqr(found) > THROW_RANGE * THROW_RANGE) {
			return null;
		}

		return found;
	}

	@Override
	public void start() {
		ReactiveState state = ReactiveState.get(this.witch);
		if (!state.isWitchInvisibilityCast()) {
			state.setWitchInvisibilityCast(true);
			state.setWitchActionUntilTick(this.witch.level().getGameTime() + ACTION_TICKS);
			WitchThrow.atSelf(this.witch, WitchPotions.stack(WitchPotions.invisibility()));
			this.ticksToThrow = OFFENSE_INTERVAL_TICKS;
		}
	}

	@Override
	public void stop() {
		this.target = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.ticksToLinger = Math.max(0, this.ticksToLinger - 1);
		this.target = this.witch.getTarget();
		LivingEntity current = this.target;
		if (current == null || --this.ticksToThrow > 0) {
			return;
		}

		this.ticksToThrow = OFFENSE_INTERVAL_TICKS;
		ReactiveState.get(this.witch).setWitchActionUntilTick(this.witch.level().getGameTime() + ACTION_TICKS);
		if (WitchElite.isElite(this.witch) && this.ticksToLinger == 0) {
			this.ticksToLinger = LINGER_REARM_TICKS;
			WitchThrow.lingerAtTarget(this.witch, WitchPotions.lingeringPoison(), current);
			return;
		}

		// An elite or boss witch never blinds.
		boolean elite = WitchElite.isElite(this.witch) || Bosses.isBoss(this.witch);
		WitchThrow.atTarget(this.witch, WitchPotions.stack((elite ? this.eliteBag : this.bag).next(this.witch.getRandom())), current);
	}
}
