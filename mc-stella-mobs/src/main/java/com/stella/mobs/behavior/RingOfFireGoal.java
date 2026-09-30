package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss blaze's ring of fire: twelve small fireballs on the twelve compass points, and five seconds
 * of fire on everything within four blocks. It claims no flag, so it can never tie with the blaze's own
 * attack goal at rank 4, and a small fireball never explodes, so the ring breaks no block.
 */
public class RingOfFireGoal extends Goal {
	public static final int PRIORITY = 6;
	public static final int FIREBALLS = 12;
	public static final int INTERVAL_TICKS = 100;
	public static final double BURN_RADIUS = 4.0;
	public static final float BURN_SECONDS = 5.0F;

	private final Blaze blaze;
	private long nextRingTick;

	public RingOfFireGoal(final Blaze blaze) {
		this.blaze = blaze;
	}

	@Override
	public boolean canUse() {
		return this.blaze.isAlive() && ReactiveState.get(this.blaze).playerInRange(this.blaze);
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
		long now = this.blaze.level().getGameTime();
		if (now < this.nextRingTick) {
			return;
		}

		this.nextRingTick = now + INTERVAL_TICKS;
		this.fireRing();
	}

	private void fireRing() {
		if (!(this.blaze.level() instanceof ServerLevel level)) {
			return;
		}

		for (int i = 0; i < FIREBALLS; i++) {
			double angle = 2.0 * Math.PI * i / FIREBALLS;
			Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			SmallFireball ball = new SmallFireball(level, this.blaze, direction);
			ball.setPos(ball.getX(), this.blaze.getY(0.5) + 0.5, ball.getZ());
			level.addFreshEntity(ball);
		}

		AABB box = this.blaze.getBoundingBox().inflate(BURN_RADIUS);

		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != this.blaze)) {
			entity.igniteForSeconds(BURN_SECONDS);
		}

		// A box query never returns players, so the players are read from the level's own list.
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && player.distanceToSqr(this.blaze) <= BURN_RADIUS * BURN_RADIUS) {
				player.igniteForSeconds(BURN_SECONDS);
			}
		}
	}
}
