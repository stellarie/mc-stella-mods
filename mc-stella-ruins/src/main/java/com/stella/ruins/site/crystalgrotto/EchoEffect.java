package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.reward.EffectHook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The Echo Prism's effect. When the holder hits, there is a 15 percent chance that the hit lands again, at half its
 * damage, 10 ticks later. The echo does not echo.
 * <p>
 * The echo waits on its own tick counter, because the clock of a game test level barely advances.
 */
public final class EchoEffect implements EffectHook {
	public static final String ID = "echo";
	public static final double CHANCE = 0.15;
	public static final float FRACTION = 0.5F;
	public static final int DELAY_TICKS = 10;

	private record Pending(long due, ServerLevel level, UUID user, UUID target, float amount) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static long ticks;
	private static boolean echoing;
	/** A random source for one user's rolls. Tests set it. Without one, the roll uses the level's own source. */
	private static final Map<UUID, Supplier<RandomSource>> SOURCES = new ConcurrentHashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** One roll at the echo chance. */
	public static boolean roll(final RandomSource source) {
		return source.nextDouble() < CHANCE;
	}

	/** Sets the random source for one user's rolls, or clears it with null. */
	public static void setRandom(final UUID user, final Supplier<RandomSource> source) {
		if (source == null) {
			SOURCES.remove(user);
		} else {
			SOURCES.put(user, source);
		}
	}

	@Override
	public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
		if (echoing || dealt <= 0.0F || !(user.level() instanceof ServerLevel level)) {
			return;
		}
		Supplier<RandomSource> source2 = SOURCES.get(user.getUUID());
		if (roll(source2 == null ? level.getRandom() : source2.get())) {
			synchronized (PENDING) {
				PENDING.add(new Pending(ticks + DELAY_TICKS, level, user.getUUID(), target.getUUID(), dealt * FRACTION));
			}
		}
	}

	/** The echoes of one user that still wait for their tick. */
	public static int pending(final UUID user) {
		synchronized (PENDING) {
			return (int) PENDING.stream().filter(pending -> pending.user().equals(user)).count();
		}
	}

	private static void tick() {
		ticks++;
		List<Pending> due = new ArrayList<>();
		synchronized (PENDING) {
			PENDING.removeIf(pending -> {
				if (pending.due() <= ticks) {
					due.add(pending);
					return true;
				}
				return false;
			});
		}
		for (Pending pending : due) {
			land(pending);
		}
	}

	private static void land(final Pending pending) {
		Entity target = pending.level().getEntity(pending.target());
		Entity user = pending.level().getEntity(pending.user());
		if (!(target instanceof LivingEntity victim) || !victim.isAlive() || !(user instanceof LivingEntity attacker)) {
			return;
		}
		DamageSource source = attacker instanceof Player player
			? pending.level().damageSources().playerAttack(player)
			: pending.level().damageSources().mobAttack(attacker);
		echoing = true;
		try {
			victim.damageCooldownTime = 0;
			victim.hurtServer(pending.level(), source, pending.amount());
		} finally {
			echoing = false;
		}
	}
}
