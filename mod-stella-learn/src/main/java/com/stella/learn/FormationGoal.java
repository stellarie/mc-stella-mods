package com.stella.learn;

import com.stella.learn.Formation.Role;
import com.stella.learn.Formation.Slot;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Walks a mob to the position its formation slot names. A back or guard position ends the walk on arrival and lets the
 * mob's own goals fight again. A flank ends it once, and the mob then fights from the side. A hurt mob that fell back
 * stays at its ally. A striker waits behind its shield bearer, strikes for {@link #STRIKE_TICKS} when the bearer is at the
 * player, then falls back for {@link #REST_TICKS}.
 *
 * <p>Rank 1: it preempts the distance goal, the standoff walk and the melee goals, and the retreat goal at rank 0 still
 * preempts it. A request from a callout wins, so the goal does not start while the mob holds one.
 */
public final class FormationGoal extends Goal {
	static final double ARRIVED = 1.5;
	static final double LEAVE = 2.5;
	/** A hurt mob stops this close to the ally it fell back to. */
	static final double ALLY_ARRIVED = 3.0;
	/** A hurt mob fights back when the player is this close. */
	static final double CORNERED = 2.5;
	/** Blocks from the player within which a walk starts, so a mob far off is left to its own goals. */
	static final double ENGAGE_RANGE = 20.0;
	/** A walk already under way goes on out to this range. */
	static final double ENGAGE_LEAVE = 24.0;
	static final int STRIKE_TICKS = 20;
	static final int REST_TICKS = 40;
	/** Ticks the goal leaves a mob alone after a walk that found no path or made no progress. */
	static final int GIVE_UP_TICKS = 100;
	private static final int PROGRESS_TICKS = 20;
	private static final double PROGRESS_BLOCKS = 0.5;
	private static final int REPATH_TICKS = 10;
	private static final double SPEED = 1.1;
	private static final Set<Mob> WALKING = Collections.newSetFromMap(new WeakHashMap<>());

	private final Mob mob;
	private int untilRepath;
	private int strikeUntil;
	private int restUntil;
	private int giveUpUntil;
	private int progressTick;
	private @Nullable Vec3 progressFrom;
	private @Nullable UUID flankedFor;
	private boolean arrived;

	public FormationGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	/** True while the goal walks this mob. The mobs mod reads it through the ObjectShare, to keep its standoff goal off. */
	static boolean walking(final Mob mob) {
		return WALKING.contains(mob);
	}

	@Override
	public boolean canUse() {
		Slot slot = Formations.slotOf(this.mob);
		if (slot == null || slot.role() != Role.FLANK) {
			this.flankedFor = null;
		}

		return slot != null && this.free() && this.inRange(slot, ENGAGE_RANGE) && this.wants(slot, LEAVE);
	}

	@Override
	public boolean canContinueToUse() {
		Slot slot = Formations.slotOf(this.mob);
		return slot != null && this.free() && this.inRange(slot, ENGAGE_LEAVE) && this.wants(slot, ARRIVED);
	}

	@Override
	public void start() {
		this.untilRepath = 0;
		this.arrived = false;
		this.progressFrom = null;
		WALKING.add(this.mob);
		Slot slot = Formations.slotOf(this.mob);
		if (slot != null && slot.pos() != null) {
			LearnLog.info("learnFormation", "WALK", LearnLog.who(this.mob) + " role=" + slot.role() + " pos=" + Rally.text(slot.pos())
				+ " distance=" + LearnLog.num(this.mob.position().distanceTo(slot.pos())));
		}
	}

	@Override
	public void stop() {
		WALKING.remove(this.mob);
		this.mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Slot slot = Formations.slotOf(this.mob);
		if (slot == null || slot.pos() == null) {
			return;
		}

		Vec3 pos = slot.pos();
		if (this.mob.position().distanceTo(pos) > this.arrivedAt(slot)) {
			if (--this.untilRepath <= 0) {
				this.untilRepath = REPATH_TICKS;
				if (!this.mob.getNavigation().moveTo(pos.x, pos.y, pos.z, SPEED)) {
					this.giveUp("no_path");
					return;
				}
			}

			if (this.stuck()) {
				this.giveUp("no_progress");
			}

			return;
		}

		this.mob.getNavigation().stop();
		Formation formation = Formations.of(this.mob);
		ServerPlayer focus = formation == null ? null : formation.focus();
		if (focus != null) {
			this.mob.getLookControl().setLookAt(focus);
		}

		if (!this.arrived) {
			this.arrived = true;
			LearnLog.info("learnFormation", "ARRIVE", LearnLog.who(this.mob) + " role=" + slot.role());
		}

		if (slot.role() == Role.FLANK && focus != null) {
			this.flankedFor = focus.getUUID();
		}
	}

	/** A mob whose walk fails is left to its own goals for a while, so a slot behind a wall does not hold it still. */
	private void giveUp(final String why) {
		this.giveUpUntil = this.mob.tickCount + GIVE_UP_TICKS;
		this.mob.getNavigation().stop();
		LearnLog.info("learnFormation", "GIVE_UP", LearnLog.who(this.mob) + " why=" + why + " ticks=" + GIVE_UP_TICKS);
	}

	private boolean stuck() {
		Vec3 now = this.mob.position();
		if (this.progressFrom == null) {
			this.progressFrom = now;
			this.progressTick = this.mob.tickCount + PROGRESS_TICKS;
			return false;
		}

		if (this.mob.tickCount < this.progressTick) {
			return false;
		}

		boolean still = now.distanceTo(this.progressFrom) < PROGRESS_BLOCKS;
		this.progressFrom = now;
		this.progressTick = this.mob.tickCount + PROGRESS_TICKS;
		return still;
	}

	/** A hurt mob falls back wherever it stands. Every other slot applies near the player only. */
	private boolean inRange(final Slot slot, final double limit) {
		if (slot.role() == Role.FALL_BACK) {
			return true;
		}

		Formation formation = Formations.of(this.mob);
		ServerPlayer focus = formation == null ? null : formation.focus();
		return focus != null && this.mob.distanceTo(focus) <= limit;
	}

	private boolean free() {
		return !this.mob.isPassenger() && this.mob.tickCount >= this.giveUpUntil && CalloutBoard.active(this.mob) == null;
	}

	private double arrivedAt(final Slot slot) {
		return slot.role() == Role.FALL_BACK ? ALLY_ARRIVED : ARRIVED;
	}

	private boolean wants(final Slot slot, final double threshold) {
		Vec3 pos = slot.pos();
		return switch (slot.role()) {
			case FRONT -> false;
			case FALL_BACK -> pos != null && !this.cornered();
			case STRIKER -> this.strikerWants(slot);
			case FLANK -> pos != null && !this.flanked() && this.mob.position().distanceTo(pos) > threshold;
			case BACK, GUARD -> pos != null && this.mob.position().distanceTo(pos) > threshold;
		};
	}

	private boolean flanked() {
		Formation formation = Formations.of(this.mob);
		ServerPlayer focus = formation == null ? null : formation.focus();
		return focus != null && focus.getUUID().equals(this.flankedFor);
	}

	private boolean cornered() {
		Formation formation = Formations.of(this.mob);
		ServerPlayer focus = formation == null ? null : formation.focus();
		return focus != null && this.mob.distanceTo(focus) < CORNERED;
	}

	/** A striker holds behind its bearer. A strike hands the mob to its melee goal for a burst, then it falls back. */
	private boolean strikerWants(final Slot slot) {
		int now = this.mob.tickCount;
		if (now < this.strikeUntil) {
			return false;
		}

		Vec3 pos = slot.pos();
		if (pos == null) {
			return false;
		}

		if (slot.engaged() && now >= this.restUntil && this.mob.position().distanceTo(pos) <= LEAVE) {
			this.strikeUntil = now + STRIKE_TICKS;
			this.restUntil = this.strikeUntil + REST_TICKS;
			LearnLog.info("learnFormation", "STRIKE", LearnLog.who(this.mob) + " ticks=" + STRIKE_TICKS);
			return false;
		}

		return true;
	}
}
