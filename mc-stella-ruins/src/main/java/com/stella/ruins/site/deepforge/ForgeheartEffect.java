package com.stella.ruins.site.deepforge;

import com.stella.ruins.reward.EffectHook;
import com.stella.ruins.world.RuinClock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * The Forgeheart Hammer. Every third hit on one target inside a window bursts fire: hostile mobs near the target
 * take damage and burn. The window counts in {@link RuinClock} ticks, so a test can move time.
 */
public final class ForgeheartEffect implements EffectHook {
	public static final int HITS = 3;
	public static final int WINDOW_TICKS = 80;
	public static final float BURST_DAMAGE = 4.0F;
	public static final double BURST_RADIUS = 3.0;
	public static final int BURST_FIRE_TICKS = 80;

	private record Track(UUID target, int hits, long since) {
	}

	private final Map<UUID, Track> tracks = new HashMap<>();

	@Override
	public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
		if (!(user.level() instanceof ServerLevel level)) {
			return;
		}
		long now = RuinClock.now(level);
		Track last = this.tracks.get(user.getUUID());
		Track next;
		if (last == null || !last.target().equals(target.getUUID()) || now - last.since() > WINDOW_TICKS) {
			next = new Track(target.getUUID(), 1, now);
		} else {
			next = new Track(last.target(), last.hits() + 1, last.since());
		}
		if (next.hits() >= HITS) {
			this.tracks.remove(user.getUUID());
			burst(level, user, target);
		} else {
			this.tracks.put(user.getUUID(), next);
		}
	}

	/** Hits the hostile mobs round the target. The target itself counts when it is hostile. */
	static void burst(final ServerLevel level, final LivingEntity user, final LivingEntity target) {
		AABB area = target.getBoundingBox().inflate(BURST_RADIUS);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, area, e -> e instanceof Enemy && e != user && e.isAlive())) {
			if (near.position().distanceTo(target.position()) <= BURST_RADIUS) {
				// The burst comes straight after the blow, so the target's own invulnerability must not eat it.
				near.damageCooldownTime = 0;
				near.hurtServer(level, level.damageSources().indirectMagic(user, user), BURST_DAMAGE);
				near.igniteForTicks(BURST_FIRE_TICKS);
			}
		}
		level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.8, target.getZ(), 30, BURST_RADIUS / 3.0, 0.4, BURST_RADIUS / 3.0, 0.05);
	}

	/** Forgets a wielder, for tests. */
	void reset() {
		this.tracks.clear();
	}
}
