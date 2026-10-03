package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.Stance;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A melee mob after a player who falls back does not go through a narrow gap alone. It walks to a staging point a few
 * blocks before the gap and waits there until enough monsters after the same player have gathered, or until its
 * patience runs out, and then the vanilla attack takes it in with the rest. After a group goes, the goal rests for a
 * while so the group is not held again on the way through. A mob in the PURSUE stance never starts a hold: it presses a
 * hurt player, and a hold would let that player get away.
 */
public final class HoldAtChokeGoal extends Goal {
	/** Ticks a released mob is left alone, so a group that gathered has time to pass. */
	static final int GO_TICKS = 120;
	private static final double ARRIVED = 2.0;
	private static final int REPATH_TICKS = 10;
	private static final int RESCAN_TICKS = 20;
	private static final double SPEED = 1.0;

	private final Mob mob;
	private Choke.@Nullable Mouth mouth;
	private int held;
	private int untilRepath;
	private int untilRescan;
	private int restUntilTick;
	private boolean gathered;

	public HoldAtChokeGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (ReactiveState.get(this.mob).getRole() != CombatRole.MELEE || this.mob.tickCount < this.restUntilTick || Stance.of(this.mob) == Stance.PURSUE) {
			return false;
		}

		if (!(this.mob.getTarget() instanceof ServerPlayer player) || !player.isAlive() || !this.retreater(player)) {
			return false;
		}

		this.mouth = Choke.findMouth(this.mob, player);
		return this.mouth != null && this.gatheredAt(this.mouth, player) < LearnConfigStore.get().chokeGroup();
	}

	@Override
	public boolean canContinueToUse() {
		if (this.held >= LearnConfigStore.get().chokePatienceTicks() || !(this.mob.getTarget() instanceof ServerPlayer player) || !this.retreater(player)) {
			return false;
		}

		if (--this.untilRescan <= 0) {
			this.untilRescan = RESCAN_TICKS;
			Choke.Mouth found = Choke.findMouth(this.mob, player);
			// A mob that has come through, or a way that has changed, has nothing left to wait for.
			this.mouth = found == null ? this.mouth : found;
			if (found == null) {
				return false;
			}
		}

		this.gathered = this.mouth != null && this.gatheredAt(this.mouth, player) >= LearnConfigStore.get().chokeGroup();
		return !this.gathered;
	}

	@Override
	public void start() {
		this.held = 0;
		this.untilRepath = 0;
		this.untilRescan = RESCAN_TICKS;
		this.gathered = false;
		if (this.mouth != null && this.mob.getTarget() instanceof ServerPlayer player) {
			LearnLog.info("learnChoke", "HOLD", LearnLog.who(this.mob) + " " + LearnLog.who(player) + " retreater=" + LearnLog.num(Choke.retreatPressure(player))
				+ " gap=" + this.mouth.gap().toShortString() + " gathered=" + this.gatheredAt(this.mouth, player)
				+ " group=" + LearnConfigStore.get().chokeGroup() + " patience=" + LearnConfigStore.get().chokePatienceTicks());
		}
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		boolean patient = this.held >= LearnConfigStore.get().chokePatienceTicks();
		LearnLog.info("learnChoke", "GO", LearnLog.who(this.mob) + " reason=" + (this.gathered ? "group" : patient ? "patience" : "other") + " held=" + this.held);
		// A mob let go by its group or by its patience has decided to go in, and is left to get through.
		boolean decidedToGo = this.gathered || this.held >= LearnConfigStore.get().chokePatienceTicks();
		this.restUntilTick = this.mob.tickCount + (decidedToGo ? GO_TICKS : LearnConfigStore.get().chokeRestTicks());
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.held++;
		if (this.mouth == null) {
			return;
		}

		Vec3 staging = this.mouth.staging();
		if (this.mob.position().distanceTo(staging) > ARRIVED) {
			if (--this.untilRepath <= 0) {
				this.untilRepath = REPATH_TICKS;
				this.mob.getNavigation().moveTo(staging.x, staging.y, staging.z, SPEED);
			}
		} else {
			this.mob.getNavigation().stop();
		}
	}

	private boolean retreater(final ServerPlayer player) {
		return Choke.retreatPressure(player) >= LearnConfigStore.get().chokeThreshold();
	}

	/**
	 * Monsters after this player that have gathered before the gap and not yet gone through it. The count is measured
	 * from the gap itself, which every mob of a group shares, and not from its own staging point, which differs with
	 * the way each one came, so a group agrees on when it is complete.
	 */
	private long gatheredAt(final Choke.Mouth at, final ServerPlayer player) {
		Vec3 gap = Vec3.atCenterOf(at.gap());
		double gapToPlayer = gap.distanceTo(player.position());
		double reach = Choke.STAGING_BACK + Choke.GATHER_RADIUS;
		return this.mob.level().getEntitiesOfClass(
			Monster.class, this.mob.getBoundingBox().inflate(reach * 2.0, 4.0, reach * 2.0),
			other -> other.isAlive() && other.getTarget() == player && other.position().distanceTo(gap) <= reach
				&& other.position().distanceTo(player.position()) >= gapToPlayer
		).size();
	}
}
