package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.phys.AABB;

/**
 * P4: an elite pufferfish is a living mine. It keeps the vanilla puff's damage and poison and only
 * widens the reach: anything inside the mine radius while the fish is puffed is stung, with the
 * vanilla numbers rather than an explosion, so nothing is shoved. Flagless.
 */
public class PufferfishMineGoal extends Goal {
	public static final int PRIORITY = 8;
	/** Well past vanilla's 0.3 touch box, and the reach the widened mine is asserted on. */
	public static final double MINE_RADIUS = 3.0;
	private static final int POISON_TICKS_PER_STATE = 60;

	private final Pufferfish puffer;

	public PufferfishMineGoal(final Pufferfish puffer) {
		this.puffer = puffer;
	}

	@Override
	public boolean canUse() {
		return Elite.isElite(this.puffer);
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
		int puff = this.puffer.getPuffState();
		if (puff <= 0 || !(this.puffer.level() instanceof ServerLevel level)) {
			return;
		}

		AABB box = this.puffer.getBoundingBox().inflate(MINE_RADIUS);

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, this::isMineTarget)) {
			if (victim.hurtServer(level, this.puffer.damageSources().mobAttack(this.puffer), 1 + puff)) {
				victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS_PER_STATE * puff, 0), this.puffer);
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=pufferfishMine t={} STING victim={} puff={} poisonTicks={} radius={}",
					this.puffer.level().getGameTime(), victim.getType(), puff, POISON_TICKS_PER_STATE * puff, MINE_RADIUS
				);
			}
		}
	}

	private boolean isMineTarget(final LivingEntity candidate) {
		return candidate != this.puffer && candidate.isAlive() && !candidate.isAlliedTo(this.puffer);
	}
}
