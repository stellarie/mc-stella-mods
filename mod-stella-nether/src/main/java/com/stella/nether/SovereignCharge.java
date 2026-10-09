package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Sovereign's answer to a player keeping their distance: any player within {@link #RANGE} but farther than
 * {@link #MIN_DISTANCE} is charged, at 8 blocks a second, through whatever blocks stand between, until the
 * Sovereign is within {@link #STOP_DISTANCE} and sees them. It fires a volley of nine at once, over three
 * seconds. It charges again, as often as it is kept away, after {@link #COOLDOWN_TICKS}.
 */
public final class SovereignCharge {
	public enum Phase {
		CHARGE,
		VOLLEY,
		DONE
	}

	public static final double RANGE = 96.0;
	/** A player nearer than this is close enough to fight; only one keeping away is charged. */
	public static final double MIN_DISTANCE = 20.0;
	/** Eight blocks a second. */
	public static final double SPEED = 8.0 / 20.0;
	public static final double STOP_DISTANCE = 10.0;
	/** A charge that has not reached its target by then gives up, so an unbreakable wall cannot hold it forever. */
	public static final int MAX_CHARGE_TICKS = 15 * 20;
	public static final int VOLLEY_SHOTS = 9;
	public static final int VOLLEY_TICKS = 3 * 20;
	/** Ticks after a volley ends before the Sovereign may charge again. */
	public static final int COOLDOWN_TICKS = 10 * 20;

	/** One charge as made: its phase, and what it did. */
	public static final class Charge {
		private final Blaze blaze;
		private final LivingEntity target;
		private final Vec3 start;
		private Phase phase = Phase.CHARGE;
		private int age;
		private int chargeTicks;
		private int volleyStartAge = -1;
		private final List<BlockPos> broken = new ArrayList<>();
		private final List<Integer> shotAges = new ArrayList<>();
		private final List<SmallFireball> shots = new ArrayList<>();

		Charge(final Blaze blaze, final LivingEntity target) {
			this.blaze = blaze;
			this.target = target;
			this.start = blaze.position();
		}

		public Phase phase() {
			return this.phase;
		}

		public LivingEntity target() {
			return this.target;
		}

		public Vec3 start() {
			return this.start;
		}

		public int chargeTicks() {
			return this.chargeTicks;
		}

		public int volleyStartAge() {
			return this.volleyStartAge;
		}

		public List<BlockPos> broken() {
			return this.broken;
		}

		public List<Integer> shotAges() {
			return this.shotAges;
		}

		public List<SmallFireball> shots() {
			return this.shots;
		}
	}

	/** Each Sovereign's charging: the charge under way or last made, how many it has made, and its cooldown. */
	private static final class State {
		@Nullable Charge current;
		int made;
		int cooldown;
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private SovereignCharge() {
	}

	private static State state(final Blaze blaze) {
		return STATES.computeIfAbsent(blaze.getUUID(), id -> new State());
	}

	public static @Nullable Charge of(final Blaze blaze) {
		State state = STATES.get(blaze.getUUID());
		return state == null ? null : state.current;
	}

	public static int madeBy(final Blaze blaze) {
		State state = STATES.get(blaze.getUUID());
		return state == null ? 0 : state.made;
	}

	/** Whether a charge holds the Sovereign: its attack and specials wait while it runs. */
	public static boolean running(final Blaze blaze) {
		Charge charge = of(blaze);
		return charge != null && charge.phase != Phase.DONE;
	}

	public static boolean charging(final Blaze blaze) {
		Charge charge = of(blaze);
		return charge != null && charge.phase == Phase.CHARGE;
	}

	/** Begins a charge at a target at once, whatever the distance or cooldown. */
	public static Charge start(final Blaze blaze, final LivingEntity target) {
		State state = state(blaze);
		Charge charge = new Charge(blaze, target);
		state.current = charge;
		state.made++;
		blaze.setTarget(target);
		BehaviorLog.info("[NETHER] sovereign charge target={} distance={} made={}", target.getScoreboardName(), (int) blaze.distanceTo(target), state.made);
		return charge;
	}

	/** Test hook: the cooldown left, set to a value. */
	static void setCooldown(final Blaze blaze, final int ticks) {
		state(blaze).cooldown = ticks;
	}

	/** Runs the charge a tick, or watches for a player keeping away. Returns whether it holds the Sovereign. */
	static boolean tick(final ServerLevel raid, final Blaze blaze) {
		State state = state(blaze);
		Charge charge = state.current;
		if (charge == null || charge.phase == Phase.DONE) {
			if (state.cooldown > 0) {
				state.cooldown--;
				return false;
			}
			if (SovereignSpecials.paused(blaze) || SovereignSpecials.casting(blaze) || SovereignSpecials.shielded(blaze)) {
				return false;
			}
			ServerPlayer away = keepingAway(raid, blaze);
			if (away == null) {
				return false;
			}
			charge = start(blaze, away);
		}
		if (!charge.target.isAlive() || charge.target.level() != raid) {
			end(state, charge);
			return false;
		}
		switch (charge.phase) {
			case CHARGE -> charge(raid, charge);
			case VOLLEY -> volley(raid, state, charge);
			default -> {
			}
		}
		charge.age++;
		return true;
	}

	/** The nearest player within range but beyond fighting distance. */
	private static @Nullable ServerPlayer keepingAway(final ServerLevel raid, final Blaze blaze) {
		ServerPlayer nearest = null;
		double best = RANGE * RANGE;
		for (ServerPlayer player : raid.players()) {
			double distance = player.distanceToSqr(blaze);
			if (!player.isSpectator() && !player.isCreative() && player.isAlive() && distance <= best) {
				best = distance;
				nearest = player;
			}
		}
		return nearest != null && best > MIN_DISTANCE * MIN_DISTANCE ? nearest : null;
	}

	private static void charge(final ServerLevel raid, final Charge charge) {
		Blaze blaze = charge.blaze;
		LivingEntity target = charge.target;
		if ((blaze.distanceTo(target) <= STOP_DISTANCE && blaze.hasLineOfSight(target)) || charge.chargeTicks >= MAX_CHARGE_TICKS) {
			charge.phase = Phase.VOLLEY;
			// The volley runs from the next tick, so it starts there.
			charge.volleyStartAge = charge.age + 1;
			blaze.setDeltaMovement(Vec3.ZERO);
			raid.playSound(null, blaze.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0F, 0.6F);
			return;
		}
		// Feet to feet, so it keeps its height over the ground and does not bore into the floor.
		Vec3 toward = target.position().subtract(blaze.position());
		double length = toward.length();
		if (length < 1.0E-4) {
			return;
		}
		Vec3 step = toward.scale(Math.min(SPEED, length) / length);
		blaze.setDeltaMovement(Vec3.ZERO);
		charge.broken.addAll(SovereignBody.moveThrough(raid, blaze, blaze.getX() + step.x, blaze.getY() + step.y, blaze.getZ() + step.z));
		blaze.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), blaze.getY() + blaze.getBbHeight() * 0.5, blaze.getZ(), 12, 0.6, 1.0, 0.6, 0.02);
		charge.chargeTicks++;
	}

	private static void volley(final ServerLevel raid, final State state, final Charge charge) {
		Blaze blaze = charge.blaze;
		int into = charge.age - charge.volleyStartAge;
		blaze.setDeltaMovement(Vec3.ZERO);
		blaze.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, charge.target.getEyePosition());
		// Nine shots spread evenly over the three seconds.
		while (charge.shotAges.size() < VOLLEY_SHOTS && charge.shotAges.size() * VOLLEY_TICKS / VOLLEY_SHOTS <= into) {
			shoot(raid, charge);
		}
		if (into + 1 >= VOLLEY_TICKS) {
			end(state, charge);
			BehaviorLog.info("[NETHER] sovereign charge done ticks={} broken={} shots={}", charge.chargeTicks, charge.broken.size(), charge.shotAges.size());
		}
	}

	private static void shoot(final ServerLevel raid, final Charge charge) {
		Blaze blaze = charge.blaze;
		Vec3 from = new Vec3(blaze.getX(), blaze.getY() + blaze.getBbHeight() * 0.5, blaze.getZ());
		Vec3 aim = charge.target.getBoundingBox().getCenter().subtract(from);
		charge.shotAges.add(charge.age);
		if (aim.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 heading = aim.normalize();
		SmallFireball ball = new SmallFireball(raid, blaze, heading);
		ball.setItem(new ItemStack(NetherItems.SOUL_FIRE_CHARGE));
		ball.setPos(from.x + heading.x * blaze.getBbWidth(), from.y, from.z + heading.z * blaze.getBbWidth());
		if (raid.addFreshEntity(ball)) {
			charge.shots.add(ball);
		}
		raid.playSound(null, blaze.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.8F);
	}

	private static void end(final State state, final Charge charge) {
		charge.phase = Phase.DONE;
		state.cooldown = COOLDOWN_TICKS;
	}

	/** Forgets one Sovereign's charges. */
	static void forget(final UUID sovereign) {
		STATES.remove(sovereign);
	}
}
