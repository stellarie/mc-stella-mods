package com.stella.mobs.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import org.jspecify.annotations.Nullable;

/**
 * W01, W02, W07, and W08: on aggro the witch splashes invisibility on itself, then throws the
 * offensive list at its target three at a time. No flags, so it never contends for MOVE or LOOK.
 */
public class WitchOffensiveThrowGoal<T extends Witch> extends Goal {
	public static final int BURST_SIZE = 3;
	public static final int BURST_SPACING_TICKS = 4;
	public static final int REARM_TICKS = 40;
	public static final double THROW_RANGE = 10.0;
	private static final int ACTION_TICKS = 10;

	private final T witch;
	private @Nullable LivingEntity target;
	private int shotsLeft;
	private long nextShotTick;

	public WitchOffensiveThrowGoal(final T witch) {
		this.witch = witch;
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.witch);
		if (!state.playerInRange(this.witch)) {
			return false;
		}

		long now = this.witch.level().getGameTime();
		if (now < state.getWitchActionUntilTick()) {
			return false;
		}

		LivingEntity found = this.witch.getTarget();
		if (found == null || !found.isAlive()) {
			return false;
		}

		if (this.witch.distanceToSqr(found) > THROW_RANGE * THROW_RANGE) {
			return false;
		}

		if (!state.isWitchInvisibilityCast()) {
			return true;
		}

		this.target = found;
		return now >= state.getWitchNextOffensiveTick();
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity current = this.target;
		return this.shotsLeft > 0 && current != null && current.isAlive();
	}

	@Override
	public void start() {
		ReactiveState state = ReactiveState.get(this.witch);
		state.setWitchActionUntilTick(this.witch.level().getGameTime() + ACTION_TICKS);

		if (!state.isWitchInvisibilityCast()) {
			state.setWitchInvisibilityCast(true);
			state.setWitchNextOffensiveTick(this.witch.level().getGameTime() + REARM_TICKS);
			this.shotsLeft = 0;
			WitchThrow.atSelf(this.witch, WitchPotions.stack(WitchPotions.invisibility()));
			return;
		}

		this.shotsLeft = BURST_SIZE;
		this.nextShotTick = this.witch.level().getGameTime();
	}

	@Override
	public void stop() {
		this.target = null;
		this.shotsLeft = 0;
		ReactiveState state = ReactiveState.get(this.witch);
		state.setWitchNextOffensiveTick(this.witch.level().getGameTime() + REARM_TICKS);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (current == null) {
			return;
		}

		long now = this.witch.level().getGameTime();
		if (now < this.nextShotTick) {
			return;
		}

		ReactiveState state = ReactiveState.get(this.witch);
		int index = state.getWitchThrowIndex() % WitchPotions.offensive().size();
		WitchPotions.Carried carried = WitchPotions.offensive().get(index);
		state.setWitchThrowIndex(state.getWitchThrowIndex() + 1);
		state.setWitchActionUntilTick(now + ACTION_TICKS);

		WitchThrow.atTarget(this.witch, WitchPotions.stack(carried), current);

		this.shotsLeft--;
		this.nextShotTick = now + BURST_SPACING_TICKS;
	}
}
