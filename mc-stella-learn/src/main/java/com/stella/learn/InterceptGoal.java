package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A melee mob after a player who kites or strafes walks at the point ahead of them and not at where they stand, so it
 * cuts the corner instead of tailing. It works only while the player is a few blocks off: close in, the vanilla attack
 * is what lands the blow.
 */
public final class InterceptGoal extends Goal {
	private static final double MINIMUM_RANGE = 4.0;
	private static final double MAXIMUM_RANGE = 20.0;
	private static final int MAX_TICKS = 60;
	private static final int REPATH_TICKS = 5;
	private static final int REST_TICKS = 20;
	private static final double SPEED = 1.0;

	private final Mob mob;
	private int walked;
	private int untilRepath;
	private int restUntilTick;

	public InterceptGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.mob.tickCount >= this.restUntilTick && this.leadable() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.walked < MAX_TICKS && this.leadable() != null;
	}

	@Override
	public void start() {
		this.walked = 0;
		this.untilRepath = 0;
		if (this.mob.getTarget() instanceof ServerPlayer player) {
			Vec3 point = Intercept.pointFor(this.mob, player);
			if (point != null) {
				LearnLog.info("learnMove", "INTERCEPT", LearnLog.who(this.mob) + " " + LearnLog.who(player) + " pressure=" + LearnLog.num(Intercept.pressure(player))
					+ " lead_ticks=" + LearnConfigStore.get().interceptLeadTicks() + " point=" + Rally.text(point) + " lead_blocks=" + LearnLog.num(point.distanceTo(player.position())));
			}
		}
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.restUntilTick = this.mob.tickCount + REST_TICKS;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.walked++;
		if (--this.untilRepath > 0) {
			return;
		}

		this.untilRepath = REPATH_TICKS;
		Vec3 point = this.leadable();
		if (point != null) {
			this.mob.getNavigation().moveTo(point.x, point.y, point.z, SPEED);
		}
	}

	/** The point to aim at, or null when this mob has no reason to lead. */
	private Vec3 leadable() {
		if (ReactiveState.get(this.mob).getRole() != CombatRole.MELEE || !(this.mob.getTarget() instanceof ServerPlayer player) || !player.isAlive()) {
			return null;
		}

		double distance = this.mob.distanceTo(player);
		if (distance <= MINIMUM_RANGE || distance > MAXIMUM_RANGE) {
			return null;
		}

		return Intercept.pointFor(this.mob, player);
	}
}
