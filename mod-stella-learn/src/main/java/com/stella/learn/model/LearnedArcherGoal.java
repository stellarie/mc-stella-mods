package com.stella.learn.model;

import com.stella.learn.LearnConfig;
import com.stella.learn.LearnConfigStore;
import com.stella.learn.LearnLog;
import com.stella.learn.StellaLearn;
import com.stella.learn.mixin.MoveControlAccessor;
import com.stella.mobs.behavior.ArcherKit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a bow archer from an action source. It decides every `decision_ticks` ticks and applies the latest action every
 * tick, under limits no model can pass: a turn of 30 degrees a tick, a move speed of 1.2 times the mob's own, a release
 * only at a full 20 tick draw, and a volley only after its 120 tick cooldown. It holds MOVE, LOOK and JUMP at priority 0.
 */
public final class LearnedArcherGoal extends Goal {
	public static final int PRIORITY = 0;
	public static final float MAX_TURN_DEGREES = 30.0F;
	public static final double SPEED_MODIFIER = 1.2;
	/** Below this length a move input counts as standing still. */
	private static final double MIN_INPUT = 1.0E-3;
	/** A shot leaves at this draw, where the bow gives full power. */
	public static final int FULL_DRAW_TICKS = 20;
	/** Vanilla's skeleton shot power; a full draw looses at this. */
	public static final float POWER = 1.6F;
	/** Vanilla's Hard difficulty skeleton inaccuracy. */
	public static final float UNCERTAINTY = 2.0F;

	private final Mob mob;
	private final ArcherState state;
	private final int decisionTicks;
	private long kitNanos;

	LearnedArcherGoal(final Mob mob, final ArcherState state) {
		this.mob = mob;
		this.state = state;
		this.decisionTicks = Contract.archer().decisionTicks();
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (this.state.retiring) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		return this.stillAnArcher();
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	/** A weapon swap can leave a mob with no bow; the driver then hands its goals back. */
	private boolean stillAnArcher() {
		if (ArcherKit.isKitArcher(this.mob)) {
			return true;
		}

		ModelDriver.fallBack(this.mob, "gear");
		return false;
	}

	@Override
	public void start() {
		this.mob.setAggressive(true);
		this.mob.getNavigation().stop();
		this.state.pitch = this.mob.getXRot();
		int every = LearnConfigStore.get().modelActLogTicks();
		// Each archer logs on its own beat, so a crowd does not write all its lines in one tick.
		if (this.state.nextActLog == 0 && every > 0) {
			this.state.nextActLog = 1 + Math.floorMod(this.mob.getUUID().hashCode(), every);
		}
	}

	@Override
	public void stop() {
		this.mob.setAggressive(false);
		this.mob.stopUsingItem();
		this.mob.setSpeed(0.0F);
		this.mob.setZza(0.0F);
		this.mob.setXxa(0.0F);
		this.state.drawTicks = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!ModelPerf.recording()) {
			this.run();
			return;
		}

		long began = System.nanoTime();
		this.run();
		// The kit's arrow release is the mobs mod's cost, the same one its own volley goal pays, so it is counted apart.
		ModelPerf.record(System.nanoTime() - began - this.kitNanos);
		this.kitNanos = 0L;
	}

	private void run() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}

		this.state.goalTicks++;
		this.state.sinceVolley = Math.min(ArcherState.VOLLEY_COOLDOWN_TICKS, this.state.sinceVolley + 1);
		LearnConfig config = LearnConfigStore.get();
		if (!config.modelArcher()) {
			ModelDriver.fallBack(this.mob, "switch");
			return;
		}

		if ((this.state.goalTicks - 1) % this.decisionTicks == 0 && !this.decide(target, config)) {
			return;
		}

		long began = ModelPerf.recording() ? System.nanoTime() : 0L;
		this.turn();
		this.move();
		this.bow();
		if (began != 0L) {
			ModelPerf.phase(ModelPerf.Phase.ACT, System.nanoTime() - began);
		}
	}

	/** Returns false when the source failed and the mob is on its way back to its goals. */
	private boolean decide(final LivingEntity target, final LearnConfig config) {
		ActionSource source = ModelDriver.sourceFor(this.mob);
		if (source == null) {
			ModelDriver.fallBack(this.mob, "missing");
			return false;
		}

		boolean timing = ModelPerf.recording();
		long began = timing ? System.nanoTime() : 0L;
		float[] observation = this.state.observation(Contract.archer().observationSize());
		ArcherAction action;
		long observed;
		// A fault in the observation or in a source must not throw out of the goal selector and stop the server tick.
		try {
			ArcherObservation.build(this.mob, target, this.state, observation);
			observed = timing ? System.nanoTime() : 0L;
			action = source.decide(this.mob, observation);
		} catch (RuntimeException exception) {
			StellaLearn.LOGGER.error("The model of {} failed; the mob gets its goals back", LearnLog.who(this.mob), exception);
			ModelDriver.fallBack(this.mob, "error");
			return false;
		}

		if (timing) {
			ModelPerf.phase(ModelPerf.Phase.OBSERVE, observed - began);
			ModelPerf.phase(ModelPerf.Phase.DECIDE, System.nanoTime() - observed);
		}
		if (action == null || !action.isFinite()) {
			ModelDriver.fallBack(this.mob, "nan");
			return false;
		}

		this.state.action = action;
		float[] previous = this.state.previous;
		previous[0] = action.forward();
		previous[1] = action.strafe();
		previous[2] = action.yaw();
		previous[3] = action.pitch();
		previous[4] = action.jumpIndex();
		previous[5] = action.bow().ordinal();
		int every = config.modelActLogTicks();
		if (every > 0 && this.state.goalTicks >= this.state.nextActLog) {
			ModelLog.act(this.mob, target, action, this.state.goalTicks);
			this.state.nextActLog = this.state.goalTicks + every;
		}

		return true;
	}

	private void turn() {
		ArcherAction action = this.state.action;
		float yaw = this.mob.getYRot() + unit(action.yaw()) * MAX_TURN_DEGREES;
		this.state.pitch = Mth.clamp(this.state.pitch + unit(action.pitch()) * MAX_TURN_DEGREES, -90.0F, 90.0F);
		this.mob.setYRot(yaw);
		this.mob.setYHeadRot(yaw);
		this.mob.setYBodyRot(yaw);
		this.mob.setXRot(this.state.pitch);
	}

	private void move() {
		ArcherAction action = this.state.action;
		float forward = unit(action.forward());
		float strafe = unit(action.strafe());
		double length = Math.hypot(forward, strafe);
		// The move control passes its input on unscaled and takes the speed from the modifier, so a scaled input plus a
		// scaled modifier would square the output. It gets a unit direction, and the modifier carries the magnitude.
		// It counts a positive sideways input as the mob's left; the contract counts it as the right.
		if (length > MIN_INPUT) {
			this.mob.getMoveControl().strafe((float) (forward / length), (float) (-strafe / length));
		} else {
			this.mob.getMoveControl().strafe(0.0F, 0.0F);
		}

		((MoveControlAccessor) this.mob.getMoveControl()).stella$setSpeedModifier(SPEED_MODIFIER * Math.min(1.0, length));
		if (action.jump() && this.mob.onGround()) {
			this.mob.getJumpControl().jump();
		}
	}

	private void bow() {
		boolean drawing = this.mob.isUsingItem();
		if (drawing) {
			this.state.drawTicks++;
		}

		switch (this.state.action.bow()) {
			case IDLE -> {
				if (drawing) {
					this.mob.stopUsingItem();
				}

				this.state.drawTicks = 0;
			}
			case DRAW -> {
				if (!drawing) {
					this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
					this.state.drawTicks = 0;
				}
			}
			case FIRE -> this.fire(false, drawing);
			case FIRE_VOLLEY -> this.fire(true, drawing);
		}
	}

	/** Starts a draw when none runs, keeps drawing, and looses at a full draw. IDLE in between cancels it. */
	private void fire(final boolean volleyAsked, final boolean drawing) {
		if (!drawing) {
			this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
			this.state.drawTicks = 0;
			return;
		}

		if (this.state.drawTicks < FULL_DRAW_TICKS) {
			return;
		}

		int drawTicks = this.state.drawTicks;
		float power = POWER * BowItem.getPowerForTime(drawTicks);
		boolean volley = volleyAsked && this.state.sinceVolley >= ArcherState.VOLLEY_COOLDOWN_TICKS;
		if (volley) {
			this.state.sinceVolley = 0;
			this.state.volleys++;
		}

		boolean timing = ModelPerf.recording();
		long began = timing ? System.nanoTime() : 0L;
		Vec3 look = this.state.lookVector(this.mob.getYRot());
		List<UUID> arrows = ArcherKit.release(this.mob, look, power, UNCERTAINTY, volley);
		if (timing) {
			this.kitNanos += System.nanoTime() - began;
			ModelPerf.phase(ModelPerf.Phase.RELEASE, System.nanoTime() - began);
		}

		this.mob.stopUsingItem();
		this.state.drawTicks = 0;
		this.state.releases++;
		ModelLog.release(this.mob, drawTicks, power, volleyAsked, volley, arrows.size());
	}

	private static float unit(final float value) {
		return Mth.clamp(value, -1.0F, 1.0F);
	}
}
