package com.stella.mobs.behavior;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * W03, W05, and W11: one scan of the allies, then one throw. An ally below 20 percent of its health
 * gets the heal splash, which is the harm splash for an undead ally whose inversion turns it into a
 * heal; an ally that is not hurt and lacks a buff gets that buff. No flags, scanned at most once
 * every 20 ticks, and cycled so a healthy ally collects invisibility, speed, then strength.
 */
public class WitchSupportHealGoal<T extends Witch> extends Goal {
	public static final double ALLY_HEALTH_FRACTION = 0.20;
	public static final double ALLY_RADIUS = 16.0;
	public static final int REARM_TICKS = 20;
	private static final double ALLY_BOX_HEIGHT = 8.0;
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final int ACTION_TICKS = 10;

	private final T witch;
	private @Nullable LivingEntity chosenTarget;
	private WitchPotions.@Nullable Carried chosenPotion;
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
		return this.choose(state);
	}

	@Override
	public void start() {
		LivingEntity ally = this.chosenTarget;
		WitchPotions.Carried carried = this.chosenPotion;
		this.chosenTarget = null;
		this.chosenPotion = null;

		if (ally == null || carried == null) {
			return;
		}

		ReactiveState state = ReactiveState.get(this.witch);
		long now = this.witch.level().getGameTime();
		state.setWitchActionUntilTick(now + ACTION_TICKS);
		this.readyTick = now + REARM_TICKS;
		WitchThrow.atTarget(this.witch, WitchPotions.stack(carried), ally);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	/**
	 * W05 first and W11 second: a hurt ally is healed, and only when none is hurt does the scan look
	 * for an ally that lacks a buff. The buff order starts at the stored index, so the two lanes
	 * cannot starve each other and the throw cycles through all three buffs.
	 */
	private boolean choose(final ReactiveState state) {
		List<Mob> candidates = this.scan();

		for (Mob candidate : candidates) {
			if (candidate.getHealth() <= candidate.getMaxHealth() * ALLY_HEALTH_FRACTION) {
				// Undead invert heal and harm, so the harm splash is the heal that works on them and it
				// takes the heal branch, which never writes lastHurtByMob.
				this.chosenTarget = candidate;
				this.chosenPotion = candidate.isInvertedHealAndHarm() ? WitchPotions.harming() : WitchPotions.healing();
				return true;
			}
		}

		// W11: a hurt ally is healed first, and only when none is hurt does the scan look for an ally
		// that lacks a buff. The buff order starts at the stored index, so the throw cycles all three.
		List<WitchPotions.Carried> buffs = WitchPotions.buffs();

		for (Mob candidate : candidates) {
			for (int step = 0; step < buffs.size(); step++) {
				int index = (state.getWitchBuffIndex() + step) % buffs.size();
				WitchPotions.Carried carried = buffs.get(index);

				if (!candidate.hasEffect(carried.effect())) {
					state.setWitchBuffIndex(index + 1);
					this.chosenTarget = candidate;
					this.chosenPotion = carried;
					return true;
				}
			}
		}

		return false;
	}

	private List<Mob> scan() {
		AABB box = this.witch.getBoundingBox().inflate(ALLY_RADIUS, ALLY_BOX_HEIGHT, ALLY_RADIUS);
		List<Mob> candidates = this.witch.level().getEntitiesOfClass(Mob.class, box, this::isAlly);
		// Nearest first, so the ally beside the witch is the one that gets the throw.
		candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(this.witch)));
		return candidates;
	}

	/** Players are excluded by the type, so only mobs reach this test. */
	private boolean isAlly(final Mob candidate) {
		return candidate != this.witch
			&& candidate.isAlive()
			&& candidate != this.witch.getTarget();
	}
}
