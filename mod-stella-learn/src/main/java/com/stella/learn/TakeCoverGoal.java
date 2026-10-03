package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A melee mob that has a known shooter for a target and stands in that shooter's sight, farther than a few blocks,
 * walks to the nearest cover ahead of it. When it arrives it is out of sight, the goal no longer applies, and the
 * vanilla attack walks it on until it is seen again, so it advances from cover to cover.
 */
public final class TakeCoverGoal extends Goal {
	private static final double MINIMUM_RANGE = 6.0;
	private static final double ARRIVED = 1.5;
	private static final int MAX_WALK_TICKS = 80;
	private static final int REPATH_TICKS = 10;
	private static final double SPEED = 1.1;

	private final Mob mob;
	private @Nullable Vec3 spot;
	private int walked;
	private int restUntilTick;
	/** Ticks a mob waits after a search finds no cover. */
	static final int SEARCH_REST_TICKS = 10;
	/** Test seam: how many cover searches canUse has run. */
	static int searches;

	public TakeCoverGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (ReactiveState.get(this.mob).getRole() != CombatRole.MELEE || this.mob.tickCount < this.restUntilTick) {
			return false;
		}

		if (!(this.mob.getTarget() instanceof ServerPlayer player) || !player.isAlive() || !this.threatened(player)) {
			return false;
		}

		searches++;
		this.spot = Cover.find(this.mob, player);
		if (this.spot == null) {
			this.restUntilTick = this.mob.tickCount + SEARCH_REST_TICKS;
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.spot != null
			&& this.walked < MAX_WALK_TICKS
			&& this.mob.position().distanceTo(this.spot) > ARRIVED
			&& this.mob.getTarget() instanceof ServerPlayer player
			&& Cover.shooterPressure(player) >= LearnConfigStore.get().coverThreshold();
	}

	@Override
	public void start() {
		this.walked = 0;
		this.walkToSpot();
		if (this.spot != null && this.mob.getTarget() instanceof ServerPlayer player) {
			LearnLog.info("learnCover", "WALK", LearnLog.who(this.mob) + " " + LearnLog.who(player) + " shooter=" + LearnLog.num(Cover.shooterPressure(player))
				+ " spot=" + LearnLog.num(this.spot.x) + "," + LearnLog.num(this.spot.y) + "," + LearnLog.num(this.spot.z)
				+ " distance_to_spot=" + LearnLog.num(this.mob.position().distanceTo(this.spot)) + " distance_to_player=" + LearnLog.num(this.mob.distanceTo(player)));
		}
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		if (this.spot != null) {
			boolean arrived = this.mob.position().distanceTo(this.spot) <= ARRIVED;
			LearnLog.info("learnCover", arrived ? "ARRIVE" : "GIVE_UP", LearnLog.who(this.mob) + " walked=" + this.walked
				+ (arrived ? "" : " reason=" + (this.walked >= MAX_WALK_TICKS ? "timeout" : "abandoned")));
		}

		this.spot = null;
		this.restUntilTick = this.mob.tickCount + LearnConfigStore.get().coverRestTicks();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (++this.walked % REPATH_TICKS == 0) {
			this.walkToSpot();
		}
	}

	private void walkToSpot() {
		if (this.spot != null) {
			this.mob.getNavigation().moveTo(this.spot.x, this.spot.y, this.spot.z, SPEED);
		}
	}

	/** A known shooter can see this mob and is far enough off that closing on it directly costs arrows. */
	private boolean threatened(final ServerPlayer player) {
		return Cover.shooterPressure(player) >= LearnConfigStore.get().coverThreshold()
			&& this.mob.distanceTo(player) > MINIMUM_RANGE
			&& Cover.exposed(this.mob.level(), player, this.mob.getEyePosition());
	}
}
