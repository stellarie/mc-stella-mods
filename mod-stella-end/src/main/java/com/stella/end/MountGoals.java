package com.stella.end;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;

/** The goal of the skeleton horse a castle wither skeleton rides: a slowing aura, and a clean exit once the rider is gone. The rider steers the horse. */
public final class MountGoals {
	private MountGoals() {
	}

	/**
	 * Players within {@link #RANGE} blocks of the horse grow slow. It runs on the horse's own clock, with the rider or
	 * without. A horse left without a rider for {@link #ORPHAN_TICKS} ticks goes, so a killed rider leaves no horse behind.
	 */
	public static final class Aura extends Goal {
		public static final double RANGE = 5.0;
		public static final int PERIOD = 20;
		public static final int SLOW_TICKS = 60;
		public static final int ORPHAN_TICKS = 200;

		private final SkeletonHorse horse;
		private int orphanFor;

		public Aura(final SkeletonHorse horse) {
			this.horse = horse;
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			orphanFor = horse.isVehicle() ? 0 : orphanFor + 1;
			if (orphanFor >= ORPHAN_TICKS) {
				BehaviorLog.info("[END] mountLost horse={}", horse.getUUID());
				horse.discard();
				return;
			}
			if (horse.tickCount % PERIOD != 0 || !(horse.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return;
			}
			int slowed = 0;
			for (ServerPlayer player : level.players()) {
				if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceTo(horse) <= RANGE) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0, true, true));
					slowed++;
				}
			}
			if (slowed > 0) {
				BehaviorLog.info("[END] mountAura horse={} slowed={}", horse.getUUID(), slowed);
			}
		}
	}
}
