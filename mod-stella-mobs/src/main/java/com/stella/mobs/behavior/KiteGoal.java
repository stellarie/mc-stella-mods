package com.stella.mobs.behavior;

import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The melee kite. A hurt melee mob in the KITE stance keeps 4 to 8 blocks from a player who looks at it. It stands still
 * inside that band and faces the player. It walks away when the player comes closer, and walks in when the player stands
 * farther. The kite ends in two cases, and the melee goal then closes in and strikes. One case is that the player looks
 * away. The other is that the mob has held its ground for {@link #HOLD_TICKS}: a constant gaze must not stall the mob for
 * good. Either way the mob keeps the KITE stance for {@link #STRIKE_TICKS}. Without that hold, a gap under the chase range
 * would read as a chase, and the strike would turn into a retreat.
 *
 * <p>The goal sits at the shield rank. A bearer's guard at the same rank cannot take the flags from it, because a tie
 * never preempts and this goal is added first. Every timer counts the mob's own ticks.
 */
public class KiteGoal<T extends PathfinderMob> extends Goal {
	public static final double WATCH_HALF_ANGLE = 55.0;
	public static final double NEAR = 4.0;
	public static final double FAR = 8.0;
	public static final int HOLD_TICKS = 60;
	public static final int STRIKE_TICKS = 60;

	private static final double SPEED_MODIFIER = 1.0;
	private static final int REPATH_TICKS = 10;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private int startTick;
	private int repathTick;
	private int restUntilTick;

	public KiteGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.mob.tickCount < this.restUntilTick) {
			return false;
		}

		Player player = this.kiteTarget();
		if (player == null) {
			return false;
		}

		if (!Gaze.looksAt(player, this.mob, WATCH_HALF_ANGLE)) {
			// An unwatched kiter strikes.
			// Hold KITE: a gap under 6 is no chase.
			Stance.requestKite(this.mob, STRIKE_TICKS);
			return false;
		}

		return true;
	}

	@Override
	public boolean canContinueToUse() {
		Player player = this.kiteTarget();
		return player != null
			&& this.mob.tickCount - this.startTick < HOLD_TICKS
			&& Gaze.looksAt(player, this.mob, WATCH_HALF_ANGLE);
	}

	@Override
	public void start() {
		this.startTick = this.mob.tickCount;
		this.repathTick = this.mob.tickCount;
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.restUntilTick = this.mob.tickCount + STRIKE_TICKS;
		// A stance that moved on stays as it is.
		if (Stance.of(this.mob) == Stance.KITE) {
			Stance.requestKite(this.mob, STRIKE_TICKS);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Player player = this.kiteTarget();
		if (player == null) {
			return;
		}

		this.mob.getLookControl().setLookAt(player, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		if (this.mob.tickCount < this.repathTick) {
			return;
		}

		this.repathTick = this.mob.tickCount + REPATH_TICKS;
		double distance = this.mob.distanceTo(player);
		if (distance < NEAR) {
			Vec3 away = FleePositions.away(this.mob, player.position());
			if (away == null) {
				this.mob.getNavigation().stop();
			} else {
				this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED_MODIFIER);
			}
		} else if (distance > FAR) {
			this.mob.getNavigation().moveTo(player, SPEED_MODIFIER);
		} else {
			this.mob.getNavigation().stop();
		}
	}

	/** The player that the mob kites, or null. */
	private @Nullable Player kiteTarget() {
		if (Stance.of(this.mob) != Stance.KITE
			|| ReactiveState.get(this.mob).getRole() != CombatRole.MELEE
			|| !this.mob.onGround() && !this.mob.isInLiquid() && !this.mob.isPassenger()) {
			return null;
		}

		return this.mob.getTarget() instanceof Player player && player.isAlive() ? player : null;
	}
}
