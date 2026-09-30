package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Guardian;
import org.jspecify.annotations.Nullable;

/**
 * O02: the elite guardian's slow attack, without a mixin. The beam is a private vanilla goal with a
 * private attack time, so the rate is changed from outside: this goal watches the target for a health
 * drop, which is a beam landing, then clears the target for {@link #HOLD_TICKS} so the next lock-on
 * starts from scratch. It claims no flag, so it can neither starve nor be starved by a vanilla goal.
 */
public class GuardianBeamCooldownGoal extends Goal {
	public static final int PRIORITY = 8;
	/** The window the target is held away after a beam lands, on top of the vanilla beam cycle. */
	public static final int HOLD_TICKS = 60;

	private final Guardian guardian;
	private @Nullable LivingEntity watched;
	private float lastHealth;
	private int holdTicks;

	public GuardianBeamCooldownGoal(final Guardian guardian) {
		this.guardian = guardian;
	}

	@Override
	public boolean canUse() {
		return Elite.isElite(this.guardian) && ReactiveState.get(this.guardian).playerInRange(this.guardian);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.holdTicks > 0) {
			this.holdTicks--;
			this.guardian.setTarget(null);
			return;
		}

		LivingEntity target = this.guardian.getTarget();

		if (target != null && target != this.watched) {
			this.watched = target;
			this.lastHealth = target.getHealth();
			return;
		}

		if (this.watched == null || !this.watched.isAlive()) {
			this.watched = null;
			return;
		}

		float health = this.watched.getHealth();

		// The beam is the guardian's only attack, so a drop on the watched target is a beam landing.
		if (health < this.lastHealth) {
			this.holdTicks = HOLD_TICKS;
			this.watched = null;
			this.lastHealth = 0.0F;
			this.guardian.setTarget(null);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=guardianBeamHold t={} HOLD ticks={}", this.guardian.level().getGameTime(), HOLD_TICKS
			);
			return;
		}

		this.lastHealth = health;
	}
}
