package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a mob has more than one sensible thing to do, this picks one from what has worked against the player it is
 * fighting, carries it out, and opens an episode so the outcome teaches the player's policy. A tactic that is only
 * carrying on, like trading blows, opens the episode and asks nothing of the mob.
 */
public final class TacticGoal extends Goal {
	/** Blocks within which a player's swing is a swing at this mob. */
	static final double SWING_RANGE = 4.5;
	static final int REST_TICKS = 10;
	private static final int SHIELD_TICKS = 20;
	private static final int STEP_TICKS = 40;
	/** Blocks from the player a mob told to back off tries to stand. */
	private static final double BACK_OFF_BLOCKS = 6.0;
	private static final double SPEED = 1.3;
	/** Blocks around a mob in which the player's arrows are looked for. */
	private static final double ARROW_SCAN = 10.0;
	/** Blocks from the mob an arrow's line may pass and still count as aimed at it. */
	private static final double ARROW_AIM = 1.5;
	/** Blocks a mob steps off an arrow's line. */
	private static final double EVADE_BLOCKS = 2.5;
	private static final int HIDE_TICKS = 60;
	private static final double ARRIVED = 1.5;

	private final Mob mob;
	private boolean exploring = true;
	private @Nullable Tactic running;
	private int ticks;
	private int restUntilTick;
	private @Nullable Vec3 destination;

	public TacticGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/** Meant for tests: with exploring off, a mob whose tactics have all been tried takes the best known one. */
	public void setExploring(final boolean value) {
		this.exploring = value;
	}

	@Override
	public boolean canUse() {
		if (this.mob.tickCount < this.restUntilTick || Episodes.active(this.mob) != null) {
			return false;
		}

		if (!(this.mob.getTarget() instanceof ServerPlayer player) || !player.isAlive()) {
			return false;
		}

		Situation situation = this.detect(player);
		if (situation == null) {
			return false;
		}

		List<Tactic> available = Tactics.available(this.mob, player, situation);
		if (available.isEmpty()) {
			return false;
		}

		String kind = BuiltInRegistries.ENTITY_TYPE.getKey(this.mob.getType()).toString();
		Policy policy = Policies.get(player);
		double epsilon = this.exploring ? Bandit.epsilon(policy.total(kind, situation), LearnConfigStore.get().rlEpsilon()) : 0.0;
		Bandit.Choice choice = Bandit.choose(policy, kind, situation, available, epsilon, this.mob.getRandom());
		if (situation == Situation.AFTER_SHOT) {
			Shots.spend(this.mob);
		}

		Episodes.begin(this.mob, player, kind, situation, choice.tactic());
		LearnLog.info("learnPolicy", "CHOOSE", LearnLog.who(this.mob) + " " + LearnLog.who(player) + " situation=" + situation + " tactic=" + choice.tactic()
			+ " why=" + choice.why() + " epsilon=" + LearnLog.num(epsilon) + " values=" + values(policy, kind, situation, available));
		if (!Tactics.acts(choice.tactic())) {
			return false;
		}

		this.running = choice.tactic();
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (this.running == null || !(this.mob.getTarget() instanceof ServerPlayer player)) {
			return false;
		}

		return switch (this.running) {
			case SHIELD -> this.ticks < SHIELD_TICKS;
			case BACK_OFF -> this.ticks < STEP_TICKS && this.mob.distanceTo(player) < BACK_OFF_BLOCKS;
			case EVADE -> this.ticks < STEP_TICKS && this.destination != null && this.flatDistanceTo(this.destination) > 0.5;
			case HIDE -> this.ticks < HIDE_TICKS && this.destination != null && this.flatDistanceTo(this.destination) > ARRIVED;
			default -> false;
		};
	}

	@Override
	public void start() {
		this.ticks = 0;
		this.destination = null;
		if (this.running == Tactic.SHIELD) {
			this.mob.getNavigation().stop();
			this.mob.startUsingItem(InteractionHand.OFF_HAND);
		} else if (this.running == Tactic.BACK_OFF && this.mob.getTarget() instanceof ServerPlayer player) {
			this.backOff(player);
		} else if (this.running == Tactic.EVADE && this.mob.getTarget() instanceof ServerPlayer player) {
			this.evade(player);
		} else if (this.running == Tactic.HIDE && this.mob.getTarget() instanceof ServerPlayer player) {
			this.hide(player);
		}
	}

	@Override
	public void stop() {
		if (this.running == Tactic.SHIELD) {
			this.mob.stopUsingItem();
		}

		this.mob.getNavigation().stop();
		this.destination = null;
		this.running = null;
		this.restUntilTick = this.mob.tickCount + REST_TICKS;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.ticks++;
		// A ranged mob's standoff goal is flagless and clears the path every tick it holds a band, so a walk that lost its
		// path is pushed straight at the spot through the move control, which that goal does not touch.
		if (this.destination != null && this.mob.getNavigation().isDone()) {
			this.mob.getMoveControl().setWantedPosition(this.destination.x, this.destination.y, this.destination.z, SPEED);
		}

		if (this.mob.getTarget() != null) {
			this.mob.getLookControl().setLookAt(this.mob.getTarget(), 30.0F, 30.0F);
		}
	}

	private void backOff(final ServerPlayer player) {
		Vec3 away = new Vec3(this.mob.getX() - player.getX(), 0.0, this.mob.getZ() - player.getZ());
		away = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		Vec3 point = player.position().add(away.scale(BACK_OFF_BLOCKS + 1.0));
		this.destination = point;
		this.walkTo(point);
	}

	/** Sends the mob toward a point and logs whether the navigation found a way. */
	private void walkTo(final Vec3 point) {
		boolean found = this.mob.getNavigation().moveTo(point.x, point.y, point.z, SPEED);
		LearnLog.info("learnPolicy", "MOVE", LearnLog.who(this.mob) + " tactic=" + this.running + " found=" + found + " from=" + LearnLog.num(this.mob.getX()) + "," + LearnLog.num(this.mob.getZ())
			+ " to=" + LearnLog.num(point.x) + "," + LearnLog.num(point.z));
	}

	private double flatDistanceTo(final Vec3 point) {
		return Math.hypot(point.x - this.mob.getX(), point.z - this.mob.getZ());
	}

	/** Steps sideways off the line of the arrow on its way, to whichever side the mob already stands on. */
	private void evade(final ServerPlayer player) {
		AbstractArrow arrow = this.incoming(player);
		if (arrow == null) {
			return;
		}

		Vec3 flight = new Vec3(arrow.getDeltaMovement().x, 0.0, arrow.getDeltaMovement().z);
		if (flight.lengthSqr() < 1.0E-6) {
			return;
		}

		Vec3 across = new Vec3(-flight.z, 0.0, flight.x).normalize();
		double side = this.mob.position().subtract(arrow.position()).dot(across) >= 0.0 ? 1.0 : -1.0;
		this.destination = this.mob.position().add(across.scale(side * EVADE_BLOCKS));
		this.walkTo(this.destination);
	}

	private void hide(final ServerPlayer player) {
		this.destination = Cover.findHide(this.mob, player);
		if (this.destination != null) {
			this.walkTo(this.destination);
		}
	}

	/** An arrow of this player's that is flying at this mob, or null. */
	private @Nullable AbstractArrow incoming(final ServerPlayer player) {
		Vec3 centre = this.mob.getBoundingBox().getCenter();
		for (AbstractArrow arrow : this.mob.level().getEntitiesOfClass(AbstractArrow.class, this.mob.getBoundingBox().inflate(ARROW_SCAN), a -> a.getOwner() == player)) {
			Vec3 flight = arrow.getDeltaMovement();
			double speedSqr = flight.lengthSqr();
			if (speedSqr < 0.01) {
				continue;
			}

			double along = centre.subtract(arrow.position()).dot(flight) / speedSqr;
			if (along > 0.0 && arrow.position().add(flight.scale(along)).distanceTo(centre) <= ARROW_AIM) {
				return arrow;
			}
		}

		return null;
	}

	/** The situation this mob is in, or null when it is in none that calls for a choice. */
	private @Nullable Situation detect(final ServerPlayer player) {
		ReactiveState state = ReactiveState.get(this.mob);
		if (player.isSwinging() && this.mob.distanceTo(player) <= SWING_RANGE) {
			return Situation.SWING;
		}

		if (this.incoming(player) != null) {
			return Situation.PROJECTILE;
		}

		if (state.getRole() == CombatRole.RANGED && Shots.fresh(this.mob)) {
			return Situation.AFTER_SHOT;
		}

		return null;
	}

	private static String values(final Policy policy, final String kind, final Situation situation, final List<Tactic> available) {
		return available.stream().map(tactic -> {
			Policy.Entry entry = policy.get(Policy.key(kind, situation, tactic));
			return tactic + "=" + LearnLog.num(entry.value()) + "(" + entry.tries() + ")";
		}).collect(Collectors.joining(","));
	}
}
