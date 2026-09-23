package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.phys.AABB;

/**
 * The boss brute's warlord's roar: ten seconds of Strength to itself and to every piglin within twelve
 * blocks, and Weakness to every player there, renewed on a ten second cadence. It claims no flag, so it
 * can never tie with another goal, and a summoned brute stays silent so one warband has one warlord.
 */
public class WarlordRoarGoal extends Goal {
	public static final int INTERVAL_TICKS = 200;
	public static final int EFFECT_TICKS = 200;
	public static final int STRENGTH_AMPLIFIER = 1;
	/** The brute's own targeting range, which is 12.0 and private, so the number is written here. */
	public static final double RADIUS = 12.0;
	private static final int WEAKNESS_AMPLIFIER = 0;

	private final Mob brute;
	private long nextRoarTick;

	public WarlordRoarGoal(final Mob brute) {
		this.brute = brute;
	}

	@Override
	public boolean canUse() {
		return !EliteState.isSummoned(this.brute) && ReactiveState.get(this.brute).playerInRange(this.brute);
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
		long now = this.brute.level().getGameTime();
		if (now < this.nextRoarTick) {
			return;
		}

		this.nextRoarTick = now + INTERVAL_TICKS;
		this.roar();
	}

	private void roar() {
		AABB box = this.brute.getBoundingBox().inflate(RADIUS);
		this.brute.addEffect(new MobEffectInstance(MobEffects.STRENGTH, EFFECT_TICKS, STRENGTH_AMPLIFIER), this.brute);

		for (AbstractPiglin piglin : this.brute.level().getEntitiesOfClass(AbstractPiglin.class, box)) {
			piglin.addEffect(
				new MobEffectInstance(MobEffects.STRENGTH, EFFECT_TICKS, STRENGTH_AMPLIFIER), this.brute
			);
		}

		// A box query never returns players, so the players are read from the level's own list.
		if (this.brute.level() instanceof ServerLevel level) {
			for (ServerPlayer player : level.players()) {
				if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(this.brute) <= RADIUS * RADIUS) {
					player.addEffect(
						new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, WEAKNESS_AMPLIFIER), this.brute
					);
				}
			}
		}
	}
}
