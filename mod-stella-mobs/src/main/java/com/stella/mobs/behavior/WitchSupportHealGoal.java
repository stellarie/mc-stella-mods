package com.stella.mobs.behavior;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * W03 and W05: a nearby mob below 20 percent of its health gets a healing splash. One pass, no
 * flags, scanned at most once every 20 ticks.
 */
public class WitchSupportHealGoal<T extends Witch> extends Goal {
	public static final double ALLY_HEALTH_FRACTION = 0.20;
	public static final double ALLY_RADIUS = 16.0;
	public static final int REARM_TICKS = 20;
	private static final double ALLY_BOX_HEIGHT = 8.0;
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final int ACTION_TICKS = 10;

	private final T witch;
	private @Nullable LivingEntity ally;
	private long readyTick;

	public WitchSupportHealGoal(final T witch) {
		this.witch = witch;
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.witch);
		if (!state.playerInRange(this.witch)) {
			return false;
		}

		long now = this.witch.level().getGameTime();
		if (now < state.getWitchActionUntilTick() || now < this.readyTick) {
			return false;
		}

		if (now < state.getWitchNextAllyScanTick()) {
			return false;
		}

		state.setWitchNextAllyScanTick(now + SCAN_INTERVAL_TICKS);
		this.ally = this.findHurtAlly();
		return this.ally != null;
	}

	@Override
	public void start() {
		LivingEntity hurt = this.ally;
		this.ally = null;
		if (hurt == null) {
			return;
		}

		ReactiveState state = ReactiveState.get(this.witch);
		long now = this.witch.level().getGameTime();
		state.setWitchActionUntilTick(now + ACTION_TICKS);
		this.readyTick = now + REARM_TICKS;
		// Undead invert heal and harm, so the harm splash is the heal that works on them and it
		// takes the heal branch, which never writes lastHurtByMob.
		WitchPotions.Carried carried = hurt.isInvertedHealAndHarm() ? WitchPotions.harming() : WitchPotions.healing();
		WitchThrow.atTarget(this.witch, WitchPotions.stack(carried), hurt);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	private @Nullable LivingEntity findHurtAlly() {
		AABB box = this.witch.getBoundingBox().inflate(ALLY_RADIUS, ALLY_BOX_HEIGHT, ALLY_RADIUS);
		List<Mob> candidates = this.witch.level().getEntitiesOfClass(Mob.class, box, this::isHurtAlly);

		for (Mob candidate : candidates) {
			return candidate;
		}

		return null;
	}

	/** Players are excluded by the type, so only mobs reach this test. */
	private boolean isHurtAlly(final Mob candidate) {
		return candidate != this.witch
			&& candidate.isAlive()
			&& candidate != this.witch.getTarget()
			&& candidate.getHealth() <= candidate.getMaxHealth() * ALLY_HEALTH_FRACTION;
	}
}
