package com.stella.mobs.behavior;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.phys.AABB;

/**
 * The boss wither skeleton's decay aura: Wither I on everything within six blocks, every 40 ticks. It
 * claims no flag. The filter is not decoration: nothing in the mob effect itself refuses the undead, so
 * without it the aura would wither the boss's own elite companion and every zombie and skeleton nearby.
 */
public class DecayAuraGoal extends Goal {
	public static final int INTERVAL_TICKS = 40;
	public static final int EFFECT_TICKS = 200;
	public static final int AMPLIFIER = 0;
	public static final double RADIUS = 6.0;

	private final Mob skeleton;
	private long nextAuraTick;

	public DecayAuraGoal(final Mob skeleton) {
		this.skeleton = skeleton;
	}

	@Override
	public boolean canUse() {
		return this.skeleton.isAlive() && ReactiveState.get(this.skeleton).playerInRange(this.skeleton);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		long now = this.skeleton.level().getGameTime();
		if (now < this.nextAuraTick) {
			return;
		}

		this.nextAuraTick = now + INTERVAL_TICKS;
		this.spreadDecay();
	}

	private void spreadDecay() {
		AABB box = this.skeleton.getBoundingBox().inflate(RADIUS);

		for (LivingEntity neighbour : this.skeleton.level().getEntitiesOfClass(LivingEntity.class, box)) {
			if (neighbour == this.skeleton || neighbour.is(EntityTypeTags.UNDEAD)) {
				continue;
			}

			neighbour.addEffect(new MobEffectInstance(MobEffects.WITHER, EFFECT_TICKS, AMPLIFIER), this.skeleton);
		}
	}
}
