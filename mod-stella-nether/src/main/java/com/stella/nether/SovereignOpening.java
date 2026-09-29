package com.stella.nether;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Sovereign's opening, once in its life: the first time a player comes within {@link #RANGE} or draws its
 * aggro, it rushes at them through whatever blocks stand between, until it sees them; it charges for three
 * seconds, and opens with {@link #SALVO_STRIKES} strikes scattered around them over five seconds.
 */
public final class SovereignOpening {
	public enum Phase {
		RUSH,
		CHARGE,
		SALVO,
		DONE
	}

	public static final double RANGE = 32.0;
	/** Twelve blocks a second. */
	public static final double RUSH_SPEED = 12.0 / 20.0;
	/** A rush that has not found its target by then gives up, so an unbreakable wall cannot hold it forever. */
	public static final int RUSH_MAX_TICKS = 10 * 20;
	public static final int CHARGE_TICKS = 3 * 20;
	public static final int SALVO_STRIKES = 15;
	public static final int SALVO_TICKS = 5 * 20;
	/** The strikes land this far around the target. */
	public static final double SALVO_RADIUS = 6.0;
	public static final double STRIKE_RADIUS = 2.0;
	public static final float STRIKE_DAMAGE = 5.0F;
	public static final int STRIKE_BURN_SECONDS = 4;

	/** Set once a Sovereign has made its opening, so it never makes another, loaded again or not. */
	public static final AttachmentType<Boolean> OPENED = AttachmentRegistry.createPersistent(StellaNether.id("sovereign_opened"), Codec.BOOL);

	/** One opening as made: its phase, and what it did, kept for the Sovereign's life. */
	public static final class Opening {
		private final Blaze blaze;
		private final LivingEntity target;
		private final Vec3 start;
		private Phase phase = Phase.RUSH;
		private int age;
		private int rushTicks;
		private int chargeStartAge = -1;
		private int salvoStartAge = -1;
		private final List<BlockPos> broken = new ArrayList<>();
		private final List<Vec3> strikes = new ArrayList<>();
		private final List<Integer> strikeAges = new ArrayList<>();
		private final Set<UUID> hits = new HashSet<>();

		Opening(final Blaze blaze, final LivingEntity target) {
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

		public int rushTicks() {
			return this.rushTicks;
		}

		public int chargeStartAge() {
			return this.chargeStartAge;
		}

		public int salvoStartAge() {
			return this.salvoStartAge;
		}

		public List<BlockPos> broken() {
			return this.broken;
		}

		public List<Vec3> strikes() {
			return this.strikes;
		}

		public List<Integer> strikeAges() {
			return this.strikeAges;
		}

		public Set<UUID> hits() {
			return this.hits;
		}
	}

	private static final Map<UUID, Opening> OPENINGS = new ConcurrentHashMap<>();

	private SovereignOpening() {
	}

	public static @Nullable Opening of(final Blaze blaze) {
		return OPENINGS.get(blaze.getUUID());
	}

	/** Whether the opening holds the Sovereign: its attack and specials wait while it runs. */
	public static boolean running(final Blaze blaze) {
		Opening opening = OPENINGS.get(blaze.getUUID());
		return opening != null && opening.phase != Phase.DONE;
	}

	public static boolean rushing(final Blaze blaze) {
		Opening opening = OPENINGS.get(blaze.getUUID());
		return opening != null && opening.phase == Phase.RUSH;
	}

	/** Begins the opening at a target, unless this Sovereign has made it already. */
	public static @Nullable Opening start(final Blaze blaze, final LivingEntity target) {
		if (Boolean.TRUE.equals(blaze.getAttached(OPENED)) || OPENINGS.containsKey(blaze.getUUID())) {
			return null;
		}
		blaze.setAttached(OPENED, true);
		Opening opening = new Opening(blaze, target);
		OPENINGS.put(blaze.getUUID(), opening);
		blaze.setTarget(target);
		StellaNether.LOGGER.info("[NETHER] sovereign opening target={} distance={}", target.getScoreboardName(), (int) blaze.distanceTo(target));
		return opening;
	}

	/** Runs the opening a tick, or watches for the player who starts it. Returns whether it holds the Sovereign. */
	static boolean tick(final ServerLevel raid, final Blaze blaze) {
		Opening opening = OPENINGS.get(blaze.getUUID());
		if (opening == null) {
			if (Boolean.TRUE.equals(blaze.getAttached(OPENED)) || SovereignSpecials.paused(blaze)) {
				return false;
			}
			LivingEntity trigger = trigger(raid, blaze);
			if (trigger == null) {
				return false;
			}
			opening = start(blaze, trigger);
			if (opening == null) {
				return false;
			}
		}
		if (opening.phase == Phase.DONE) {
			return false;
		}
		if (!opening.target.isAlive() || opening.target.level() != raid) {
			opening.phase = Phase.DONE;
			return false;
		}
		switch (opening.phase) {
			case RUSH -> rush(raid, opening);
			case CHARGE -> charge(raid, opening);
			case SALVO -> salvo(raid, opening);
			default -> {
			}
		}
		opening.age++;
		return true;
	}

	/** The player who draws it: its aggro, or else the nearest within range. */
	private static @Nullable LivingEntity trigger(final ServerLevel raid, final Blaze blaze) {
		if (blaze.getTarget() instanceof Player player && player.isAlive() && !player.isSpectator() && !player.isCreative()) {
			return player;
		}
		ServerPlayer nearest = null;
		double best = RANGE * RANGE;
		for (ServerPlayer player : raid.players()) {
			double distance = player.distanceToSqr(blaze);
			if (!player.isSpectator() && !player.isCreative() && player.isAlive() && distance <= best) {
				best = distance;
				nearest = player;
			}
		}
		return nearest;
	}

	private static void rush(final ServerLevel raid, final Opening opening) {
		Blaze blaze = opening.blaze;
		LivingEntity target = opening.target;
		if (blaze.hasLineOfSight(target) || opening.rushTicks >= RUSH_MAX_TICKS) {
			opening.phase = Phase.CHARGE;
			opening.chargeStartAge = opening.age;
			blaze.setDeltaMovement(Vec3.ZERO);
			raid.playSound(null, blaze.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.6F);
			return;
		}
		// Feet to feet, so it keeps its height over the ground and does not bore into the floor.
		Vec3 toward = target.position().subtract(blaze.position());
		double length = toward.length();
		if (length < 1.0E-4) {
			return;
		}
		Vec3 step = toward.scale(Math.min(RUSH_SPEED, length) / length);
		AABB next = blaze.getBoundingBox().move(step);
		// Whatever stands where it is going is broken, dropping nothing; only what cannot be broken stops it.
		BlockPos.betweenClosedStream(next).map(BlockPos::immutable).forEach(at -> {
			BlockState state = raid.getBlockState(at);
			if (!state.getCollisionShape(raid, at).isEmpty() && state.getDestroySpeed(raid, at) >= 0.0F && raid.destroyBlock(at, false, blaze, 512)) {
				opening.broken.add(at);
			}
		});
		blaze.setDeltaMovement(Vec3.ZERO);
		blaze.setPos(blaze.getX() + step.x, blaze.getY() + step.y, blaze.getZ() + step.z);
		blaze.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), blaze.getY() + blaze.getBbHeight() * 0.5, blaze.getZ(), 12, 0.6, 1.0, 0.6, 0.02);
		opening.rushTicks++;
	}

	/** Three seconds gathering blue fire: a ring drawn in tighter as it fills, and its sound rising. */
	private static void charge(final ServerLevel raid, final Opening opening) {
		Blaze blaze = opening.blaze;
		int charged = opening.age - opening.chargeStartAge;
		blaze.setDeltaMovement(Vec3.ZERO);
		blaze.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, opening.target.getEyePosition());
		double y = blaze.getY() + blaze.getBbHeight() * 0.5;
		double radius = 4.0 * (1.0 - (double) charged / CHARGE_TICKS) + 0.5;
		for (int i = 0; i < 8; i++) {
			double angle = 2.0 * Math.PI * i / 8 + charged * 0.2;
			raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX() + Math.cos(angle) * radius, y, blaze.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (charged % 20 == 0) {
			raid.playSound(null, blaze.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F + charged / (float) CHARGE_TICKS);
		}
		if (charged + 1 >= CHARGE_TICKS) {
			opening.phase = Phase.SALVO;
			opening.salvoStartAge = opening.age + 1;
		}
	}

	private static void salvo(final ServerLevel raid, final Opening opening) {
		Blaze blaze = opening.blaze;
		int into = opening.age - opening.salvoStartAge;
		// Fifteen strikes spread evenly over the five seconds.
		while (opening.strikes.size() < SALVO_STRIKES && opening.strikes.size() * SALVO_TICKS / SALVO_STRIKES <= into) {
			strike(raid, opening, blaze.getRandom());
		}
		if (into + 1 >= SALVO_TICKS) {
			opening.phase = Phase.DONE;
			StellaNether.LOGGER.info("[NETHER] sovereign opening done strikes={} hits={} broken={}", opening.strikes.size(), opening.hits.size(), opening.broken.size());
		}
	}

	private static void strike(final ServerLevel raid, final Opening opening, final RandomSource random) {
		Blaze blaze = opening.blaze;
		Vec3 around = opening.target.position();
		double angle = random.nextDouble() * 2.0 * Math.PI;
		double distance = Math.sqrt(random.nextDouble()) * SALVO_RADIUS;
		Vec3 at = new Vec3(around.x + Math.cos(angle) * distance, around.y, around.z + Math.sin(angle) * distance);
		opening.strikes.add(at);
		opening.strikeAges.add(opening.age);
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 3.0, at.z, 20, 0.2, 3.0, 0.2, 0.0);
		raid.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		raid.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.5F);
		AABB reach = new AABB(at, at).inflate(STRIKE_RADIUS, 3.0, STRIKE_RADIUS);
		for (LivingEntity victim : raid.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != blaze && entity.isAlive() && !(entity instanceof Blaze))) {
			double dx = victim.getX() - at.x;
			double dz = victim.getZ() - at.z;
			if (dx * dx + dz * dz > STRIKE_RADIUS * STRIKE_RADIUS) {
				continue;
			}
			victim.hurtServer(raid, raid.damageSources().mobAttack(blaze), STRIKE_DAMAGE);
			victim.igniteForSeconds(STRIKE_BURN_SECONDS);
			opening.hits.add(victim.getUUID());
		}
	}

	/** Forgets every opening in memory; a Sovereign's own mark keeps it from opening twice. Test hook and reset. */
	static void forget() {
		OPENINGS.clear();
	}
}
