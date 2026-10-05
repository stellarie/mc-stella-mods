package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.reward.EffectHook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Cutthroat's Cloak effect. After the wearer takes a hit, Invisibility for 60 ticks. The cloak then rests for
 * 600 ticks. The cooldown counts on its own tick counter, because the clock of a game test level barely advances.
 */
public final class CutthroatEffect implements EffectHook {
	public static final String ID = "cutthroat";
	public static final int INVISIBLE_TICKS = 60;
	public static final int COOLDOWN_TICKS = 600;

	private static final Map<UUID, Long> READY = new ConcurrentHashMap<>();
	private static long ticks;
	private static LongSupplier clock = () -> ticks;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> ticks++);
	}

	/** Replaces the tick source and returns the old one, so a test can restore it in a finally block. */
	public static LongSupplier replaceClock(final LongSupplier replacement) {
		LongSupplier old = clock;
		clock = replacement;
		return old;
	}

	/** Forgets the cooldown of one wearer. */
	public static void reset(final UUID user) {
		READY.remove(user);
	}

	/** The tick at which the wearer's cloak is ready again, or 0 when it never fired. */
	public static long readyAt(final UUID user) {
		return READY.getOrDefault(user, 0L);
	}

	@Override
	public void onHurt(final LivingEntity user, final DamageSource source, final float taken) {
		long now = clock.getAsLong();
		Long ready = READY.get(user.getUUID());
		if (taken <= 0.0F || (ready != null && now < ready)) {
			return;
		}
		READY.put(user.getUUID(), now + COOLDOWN_TICKS);
		user.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVISIBLE_TICKS, 0, false, false));
	}
}
