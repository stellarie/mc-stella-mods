package com.stella.rpg.job;

import com.stella.rpg.RpgCombat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Fighter innate. Each melee attack that lands has a {@link #CHANCE} to hit again a few ticks later, with the
 * same raw damage and swing charge. The second hit rolls its own dice and its own critical hit, and it gives the
 * target Nausea. The second hit never starts a third hit and never rolls the sword bleed again.
 * The second hit comes after the first hit has finished, so the hit hooks of other mods see two separate blows.
 */
public final class DoubleStrike {
	public static final double CHANCE = 0.25;
	public static final int DELAY_TICKS = 4;
	public static final int NAUSEA_TICKS = 10;
	private static final double REACH_SQR = 6.0 * 6.0;

	/** Test seam: each second hit that ran. */
	public interface Recorder {
		void struck(ServerPlayer attacker, LivingEntity victim, boolean landed);
	}

	private record Swing(ServerPlayer attacker, LivingEntity victim, float amount, float strength) {
	}

	private record Pending(UUID attacker, UUID victim, float amount, float strength, long due) {
	}

	private static final RpgCombat.Roller FAIR = (attacker, random, chance) -> chance > 0.0 && random.nextDouble() < chance;
	private static final Recorder SILENT = (attacker, victim, landed) -> {
	};
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, RpgCombat.Roller> ROLLERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Recorder> RECORDERS = new ConcurrentHashMap<>();
	private static Swing noted;
	private static boolean striking;
	private static long clock;

	private DoubleStrike() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DoubleStrike::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(DoubleStrike::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING.clear();
			ROLLERS.clear();
			RECORDERS.clear();
		});
	}

	/** A test sets a fixed roller for one attacker, so parallel cases do not meet. Null restores the fair one. */
	public static void setRoller(final UUID attacker, final RpgCombat.Roller newRoller) {
		if (newRoller == null) {
			ROLLERS.remove(attacker);
		} else {
			ROLLERS.put(attacker, newRoller);
		}
	}

	/** A test watches the second hits of one attacker. Null stops the watch. */
	public static void setRecorder(final UUID attacker, final Recorder newRecorder) {
		if (newRecorder == null) {
			RECORDERS.remove(attacker);
		} else {
			RECORDERS.put(attacker, newRecorder);
		}
	}

	/** Runs the waiting second hits of one attacker now, so a test sees the whole pair in one tick. */
	public static void settle(final ServerPlayer attacker) {
		List<Pending> mine = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending next = it.next();
			if (next.attacker().equals(attacker.getUUID())) {
				mine.add(next);
				it.remove();
			}
		}
		mine.forEach(next -> strike(attacker.level().getServer(), next));
	}

	/** True while a second hit runs, so other hit hooks of this mod skip it. */
	public static boolean striking() {
		return striking;
	}

	/** How many second hits of this attacker wait for their tick. */
	public static int pending(final UUID attacker) {
		int waiting = 0;
		for (Pending next : PENDING) {
			waiting += next.attacker().equals(attacker) ? 1 : 0;
		}
		return waiting;
	}

	/** The melee hit code calls this with the raw damage of a swing, before the dice. */
	public static void note(final ServerPlayer attacker, final LivingEntity victim, final float amount, final float strength) {
		noted = striking || !JobInnates.has(attacker, Innate.DOUBLE_STRIKE) ? null : new Swing(attacker, victim, amount, strength);
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked
	) {
		Swing swing = noted;
		noted = null;
		if (swing == null || striking || blocked || damageTaken <= 0.0F || swing.victim() != victim
			|| !source.is(DamageTypes.PLAYER_ATTACK) || source.getEntity() != swing.attacker()) {
			return;
		}
		ServerPlayer attacker = swing.attacker();
		if (ROLLERS.getOrDefault(attacker.getUUID(), FAIR).roll(attacker, attacker.getRandom(), CHANCE)) {
			PENDING.add(new Pending(attacker.getUUID(), victim.getUUID(), swing.amount(), swing.strength(), clock + DELAY_TICKS));
		}
	}

	private static void tick(final MinecraftServer server) {
		clock++;
		noted = null;
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> due = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending next = it.next();
			if (next.due() <= clock) {
				due.add(next);
				it.remove();
			}
		}
		due.forEach(next -> strike(server, next));
	}

	private static void strike(final MinecraftServer server, final Pending pending) {
		ServerPlayer attacker = server.getPlayerList().getPlayer(pending.attacker());
		if (attacker == null || !attacker.isAlive()) {
			return;
		}
		ServerLevel level = attacker.level();
		if (!(level.getEntity(pending.victim()) instanceof LivingEntity victim) || !victim.isAlive()
			|| attacker.distanceToSqr(victim) > REACH_SQR) {
			return;
		}
		boolean landed;
		striking = true;
		try {
			RpgCombat.beginAttack(attacker, pending.strength());
			victim.damageCooldownTime = 0;
			landed = victim.hurtServer(level, level.damageSources().playerAttack(attacker), pending.amount());
		} finally {
			RpgCombat.endAttack(attacker);
			striking = false;
		}
		if (landed && victim.isAlive()) {
			victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0), attacker);
		}
		RECORDERS.getOrDefault(attacker.getUUID(), SILENT).struck(attacker, victim, landed);
	}
}
