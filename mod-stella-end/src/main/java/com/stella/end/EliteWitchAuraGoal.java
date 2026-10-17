package com.stella.end;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;

/** The elite witch's aura: players near it grow weak. */
public final class EliteWitchAuraGoal extends Goal {
	public static final double RANGE = 8.0;
	public static final int PERIOD = 40;
	public static final int WEAKNESS_TICKS = 100;

	private final Witch witch;
	private int nextAt;

	public EliteWitchAuraGoal(final Witch witch) {
		this.witch = witch;
	}

	/** The goal selector asks every other tick, so the period is kept on the witch's own clock. */
	@Override
	public boolean canUse() {
		return witch.tickCount >= nextAt && !playersInRange().isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		nextAt = witch.tickCount + PERIOD;
		for (ServerPlayer player : playersInRange()) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, true, true));
		}
	}

	private java.util.List<ServerPlayer> playersInRange() {
		if (!(witch.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return java.util.List.of();
		}
		return level.players().stream()
			.filter(player -> player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceTo(witch) <= RANGE)
			.toList();
	}
}
