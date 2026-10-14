package com.stella.learn;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

/**
 * Hostile monsters accept friendly fire and never turn on each other. A blow from one hostile monster still lands, but
 * the victim forgets who struck it and drops the attacker as a target, so the vanilla retaliation goal and the mobs mod's
 * ally defence find nothing to answer. The damage event fires before the game records the attacker, so the forgetting is
 * queued and runs at the end of the same tick. A servant, a village friend and a boss are not hostile here, see
 * {@link Formations#hostile(Mob)}, so a fight with them is left as it was.
 */
public final class FriendlyFire {
	private static final List<LivingEntity[]> PENDING = new ArrayList<>();

	private FriendlyFire() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(FriendlyFire::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> drain());
	}

	private static void drain() {
		if (PENDING.isEmpty()) {
			return;
		}

		List<LivingEntity[]> due = List.copyOf(PENDING);
		PENDING.clear();
		for (LivingEntity[] pair : due) {
			forgive((Mob) pair[0], pair[1]);
		}
	}

	static void afterDamage(
		final LivingEntity entity, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked
	) {
		if (entity instanceof Monster victim && source.getEntity() instanceof Monster attacker && attacker != victim
			&& Formations.hostile(victim) && Formations.hostile(attacker)) {
			forgive(victim, attacker);
			PENDING.add(new LivingEntity[] {victim, attacker});
		}
	}

	/** Drops a hostile monster as a target and as an attacker. The scan calls it for every monster, whoever set either. */
	static void sweep(final Mob mob) {
		if (mob.getTarget() instanceof Monster other && Formations.hostile(other)) {
			forgive(mob, other);
		}

		if (mob.getLastHurtByMob() instanceof Monster other && Formations.hostile(other)) {
			forgive(mob, other);
		}
	}

	private static void forgive(final Mob victim, final LivingEntity attacker) {
		boolean marked = victim.getLastHurtByMob() == attacker;
		boolean targeted = victim.getTarget() == attacker;
		if (marked) {
			victim.setLastHurtByMob(null);
		}

		if (targeted) {
			victim.setTarget(null);
		}

		if (marked || targeted) {
			LearnLog.info("learnFriendlyFire", "FORGIVE", LearnLog.who(victim) + " attacker=" + attacker.getUUID() + " marked=" + marked + " targeted=" + targeted);
		}
	}
}
