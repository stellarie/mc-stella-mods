package com.stella.mobs.behavior;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.phys.AABB;

/**
 * M11: every two seconds the witch throws one random buff or heal at one random ally within sixteen
 * blocks, whatever that ally's health. The heal is the harm splash for an undead ally, whose inversion
 * turns it into a heal. The cadence counts the goal's own ticks. No flags.
 */
public class WitchSupportHealGoal<T extends Witch> extends Goal {
	public static final double ALLY_RADIUS = 16.0;
	public static final int SUPPORT_INTERVAL_TICKS = 40;
	private static final double ALLY_BOX_HEIGHT = 8.0;
	private static final int ACTION_TICKS = 10;

	private final T witch;
	private final WitchBag<WitchPotions.Carried> bag;
	private int ticksToThrow = SUPPORT_INTERVAL_TICKS;

	public WitchSupportHealGoal(final T witch) {
		this.witch = witch;
		List<WitchPotions.Carried> kit = new ArrayList<>(WitchPotions.buffs());
		kit.add(WitchPotions.healing());
		this.bag = new WitchBag<>(kit);
	}

	@Override
	public boolean canUse() {
		return ReactiveState.get(this.witch).playerInRange(this.witch);
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
		if (--this.ticksToThrow > 0) {
			return;
		}

		this.ticksToThrow = SUPPORT_INTERVAL_TICKS;
		List<Mob> allies = this.scan();
		if (allies.isEmpty()) {
			return;
		}

		Mob ally = allies.get(this.witch.getRandom().nextInt(allies.size()));
		WitchPotions.Carried carried = this.bag.next(this.witch.getRandom());
		if (carried.equals(WitchPotions.healing()) && ally.isInvertedHealAndHarm()) {
			carried = WitchPotions.harming();
		}

		ReactiveState.get(this.witch).setWitchActionUntilTick(this.witch.level().getGameTime() + ACTION_TICKS);
		WitchThrow.atTarget(this.witch, WitchPotions.stack(carried), ally);
	}

	private List<Mob> scan() {
		AABB box = this.witch.getBoundingBox().inflate(ALLY_RADIUS, ALLY_BOX_HEIGHT, ALLY_RADIUS);
		return this.witch.level().getEntitiesOfClass(Mob.class, box, this::isAlly);
	}

	/** Only hostile mobs are allies; players are excluded by the type, and a witch never buffs a cow. */
	private boolean isAlly(final Mob candidate) {
		return candidate != this.witch
			&& candidate instanceof Enemy
			&& candidate.isAlive()
			&& candidate != this.witch.getTarget()
			&& candidate.distanceToSqr(this.witch) <= ALLY_RADIUS * ALLY_RADIUS;
	}
}
