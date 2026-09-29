package com.stella.learn;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Watches fights and adds to the profile of the player in them: a blocked blow, a sword hit, or an arrow or bolt hit. */
public final class HabitRecorder {
	private HabitRecorder() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(LearnClock::bind);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LearnClock.bind(null));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HabitRecorder::afterDamage);
	}

	static void afterDamage(
		final LivingEntity entity, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked
	) {
		if (entity instanceof ServerPlayer defender) {
			if (blocked) {
				Profiles.record(defender, Habit.BLOCK);
			}

			return;
		}

		if (!(source.getEntity() instanceof ServerPlayer attacker)) {
			return;
		}

		if (source.getDirectEntity() instanceof Projectile) {
			Profiles.record(attacker, Habit.RANGED);
		} else if (source.getDirectEntity() == attacker) {
			Profiles.record(attacker, Habit.MELEE);
		}
	}
}
