package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A mob beside a player who jumps to land critical hits steps back the moment that player leaves the ground, so the
 * jump comes down on nothing. It walks a few blocks directly away for a short time and then the vanilla attack brings
 * it back, and a rest after each dodge keeps it from being kited by a player who hops in place.
 */
public final class DodgeCritGoal extends Goal {
	/** Blocks from the player inside which a jump is a threat to this mob. */
	static final double THREAT_RANGE = 4.0;
	/** Blocks from the player at which the mob stops backing off. */
	static final double SAFE_DISTANCE = 5.0;
	private static final int MAX_TICKS = 15;
	private static final int REST_TICKS = 30;
	private static final double SPEED = 1.3;

	private final Mob mob;
	private int walked;
	private int restUntilTick;

	public DodgeCritGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return ReactiveState.get(this.mob).getRole() == CombatRole.MELEE && this.mob.tickCount >= this.restUntilTick
			&& this.mob.getTarget() instanceof ServerPlayer player && this.threatened(player) && this.mob.distanceTo(player) <= THREAT_RANGE;
	}

	@Override
	public boolean canContinueToUse() {
		return this.walked < MAX_TICKS && this.mob.getTarget() instanceof ServerPlayer player && this.threatened(player) && this.mob.distanceTo(player) < SAFE_DISTANCE;
	}

	@Override
	public void start() {
		this.walked = 0;
		if (this.mob.getTarget() instanceof ServerPlayer player) {
			LearnLog.info("learnMove", "DODGE", LearnLog.who(this.mob) + " " + LearnLog.who(player) + " crit_jumper=" + LearnLog.num(Movement.critPressure(player))
				+ " distance=" + LearnLog.num(this.mob.distanceTo(player)));
			this.stepBack(player);
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
		if (this.walked % 5 == 0 && this.mob.getTarget() instanceof ServerPlayer player) {
			this.stepBack(player);
		}
	}

	/** A known crit-jumper who is in the air. */
	private boolean threatened(final ServerPlayer player) {
		return Movement.critPressure(player) >= LearnConfigStore.get().moveThreshold() && !player.onGround();
	}

	private void stepBack(final ServerPlayer player) {
		Vec3 away = new Vec3(this.mob.getX() - player.getX(), 0.0, this.mob.getZ() - player.getZ());
		away = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		Vec3 point = player.position().add(away.scale(SAFE_DISTANCE));
		this.mob.getNavigation().moveTo(point.x, point.y, point.z, SPEED);
	}
}
