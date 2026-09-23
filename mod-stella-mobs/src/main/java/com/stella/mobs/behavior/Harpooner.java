package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.Vec3;

/**
 * P2: an elite drowned's trident pulls what it hits toward the thrower. Loyalty only returns the
 * trident and the vanilla trident hit does not pull, so this hook is the whole ability. The rearm
 * keeps a fast thrower from becoming a tractor beam.
 */
public final class Harpooner {
	/** The trident's own hit knocks the victim away at about 0.40, so the reel has to beat that. */
	public static final double PULL_POWER = 1.0;
	public static final int REARM_TICKS = 20;

	private Harpooner() {
	}

	public static void afterDamage(
		final LivingEntity entity,
		final DamageSource source,
		final float baseDamageTaken,
		final float damageTaken,
		final boolean blocked
	) {
		if (blocked || !(source.getDirectEntity() instanceof ThrownTrident trident)) {
			return;
		}

		if (!(trident.getOwner() instanceof Drowned drowned) || !Elite.isElite(drowned)) {
			return;
		}

		ReactiveState state = ReactiveState.get(drowned);
		long now = drowned.level().getGameTime();
		if (now < state.getHarpoonReadyTick()) {
			return;
		}

		Vec3 toward = new Vec3(drowned.getX() - entity.getX(), 0.0, drowned.getZ() - entity.getZ());
		if (toward.lengthSqr() < 1.0e-4) {
			return;
		}

		state.setHarpoonReadyTick(now + REARM_TICKS);
		Vec3 pull = toward.normalize().scale(PULL_POWER);
		Vec3 before = entity.getDeltaMovement();
		entity.push(pull.x, 0.05, pull.z);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=harpoonPull t={} PULL power={} victim={} distance={} before={} after={}",
			now, PULL_POWER, entity.getType(), drowned.distanceTo(entity), before, entity.getDeltaMovement()
		);
	}
}
