package com.stella.nether;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Blaze Sovereign's five specials, cast in turn while it has a player to fight: a spiral of fireballs,
 * a brood of blazes, a burning dive, a meteor storm and a charged wave. Each cast records what it spawned,
 * whom it hit and where it struck, so its effect can be judged from the record.
 */
public final class SovereignSpecials {
	public enum Special {
		FLAME_SPIRAL,
		BROOD,
		DIVE,
		METEOR_STORM,
		CHARGED_WAVE
	}

	public static final List<Special> ROTATION = List.of(Special.FLAME_SPIRAL, Special.BROOD, Special.DIVE, Special.METEOR_STORM, Special.CHARGED_WAVE);
	/** Ticks from first facing a player to the first special, and the rest after each special ends. */
	public static final int FIRST_SPECIAL_TICKS = 60;
	public static final int SPECIAL_EVERY_TICKS = 160;
	/** A special is cast only at a target this near. */
	public static final double SPECIAL_RANGE = 32.0;

	public static final int SPIRAL_TICKS = 100;
	public static final int SPIRAL_EVERY = 5;
	public static final int SPIRAL_PER_VOLLEY = 8;
	private static final double SPIRAL_TURN = Math.PI / 16.0;

	public static final int BROOD_SIZE = 5;
	private static final double BROOD_RING = 3.0;

	public static final double DIVE_RISE = 8.0;
	public static final int DIVE_RISE_TICKS = 20;
	public static final int DIVE_HOVER_TICKS = 10;
	public static final double DIVE_SPEED = 1.5;
	private static final int DIVE_MAX_TICKS = 80;
	/** Half the side of the dive's square blast: twelve by twelve. */
	public static final double DIVE_HALF_WIDTH = 6.0;
	public static final float DIVE_DAMAGE = 20.0F;
	public static final int DIVE_BURN_SECONDS = 8;
	public static final double DIVE_FIRE_CHANCE = 0.35;

	public static final int METEOR_COUNT = 20;
	public static final int METEOR_EVERY = 3;
	/** Half the side of the storm's square: twelve by twelve. */
	public static final double METEOR_HALF_WIDTH = 6.0;
	public static final double METEOR_RADIUS = 1.5;
	public static final float METEOR_DAMAGE = 5.0F;
	public static final double METEOR_STUN_CHANCE = 0.3;
	/** Every third meteor falls on someone standing in the storm. */
	private static final int METEOR_AIM_EVERY = 3;

	public static final double WAVE_RADIUS = 16.0;
	public static final double WAVE_SPEED = 1.0;
	public static final double WAVE_HEIGHT = 4.0;
	public static final float WAVE_DAMAGE = 4.0F;
	public static final float WAVE_KNOCKBACK = 2.5F;

	public static final int STUN_TICKS = 60;
	/** A stun is Slowness this strong, which all but roots a player, with fatigue and weakness. */
	public static final int STUN_SLOWNESS = 6;

	/** One special as cast: what it did, kept for as long as the Sovereign's control lasts. */
	public static final class Cast {
		private final Special special;
		private final Blaze blaze;
		private final LivingEntity target;
		private final Vec3 centre;
		private final Vec3 home;
		private final List<Entity> spawned = new ArrayList<>();
		private final List<Vec3> headings = new ArrayList<>();
		private final Set<UUID> hits = new HashSet<>();
		private final List<Vec3> impacts = new ArrayList<>();
		private int age;
		private int lastActionAge = -1;
		private int firesPlaced;
		private int meteors;
		private boolean done;
		/** Per step, how many a wave's query saw; kept for a failing test to report. */
		private final List<String> seen = new ArrayList<>();

		Cast(final Special special, final Blaze blaze, final LivingEntity target) {
			this.special = special;
			this.blaze = blaze;
			this.target = target;
			this.centre = target.position();
			this.home = blaze.position();
		}

		public Special special() {
			return this.special;
		}

		public boolean done() {
			return this.done;
		}

		public Vec3 centre() {
			return this.centre;
		}

		public List<Entity> spawned() {
			return this.spawned;
		}

		public List<Vec3> headings() {
			return this.headings;
		}

		public Set<UUID> hits() {
			return this.hits;
		}

		public List<Vec3> impacts() {
			return this.impacts;
		}

		public int firesPlaced() {
			return this.firesPlaced;
		}

		public int lastActionAge() {
			return this.lastActionAge;
		}

		public Vec3 home() {
			return this.home;
		}

		public List<String> seen() {
			return this.seen;
		}
	}

	private static final class Control {
		@Nullable Cast active;
		@Nullable Cast last;
		int countdown = FIRST_SPECIAL_TICKS;
		int next;
		boolean paused;
		final List<Special> history = new ArrayList<>();
	}

	private static final Map<UUID, Control> CONTROLS = new ConcurrentHashMap<>();

	private SovereignSpecials() {
	}

	private static Control control(final Blaze blaze) {
		return CONTROLS.computeIfAbsent(blaze.getUUID(), id -> new Control());
	}

	/** Stops the Sovereign starting specials on its own; a test casts them by hand. */
	public static void pause(final Blaze blaze) {
		control(blaze).paused = true;
	}

	public static void resume(final Blaze blaze, final int countdown) {
		Control control = control(blaze);
		control.paused = false;
		control.countdown = countdown;
	}

	public static List<Special> history(final Blaze blaze) {
		return List.copyOf(control(blaze).history);
	}

	public static @Nullable Cast lastCast(final Blaze blaze) {
		return control(blaze).last;
	}

	/** The dive lifts the Sovereign off the ground, so its footing rule waits until the dive is down. */
	public static boolean airborne(final Blaze blaze) {
		Control control = CONTROLS.get(blaze.getUUID());
		return control != null && control.active != null && control.active.special == Special.DIVE && !control.active.done;
	}

	static void forget() {
		CONTROLS.clear();
	}

	/** Starts a special at once against a target. */
	public static Cast cast(final Blaze blaze, final Special special, final LivingEntity target) {
		Control control = control(blaze);
		Cast cast = new Cast(special, blaze, target);
		control.active = cast;
		control.last = cast;
		control.history.add(special);
		StellaNether.LOGGER.info("[NETHER] sovereign special={} target={}", special, target.getScoreboardName());
		return cast;
	}

	/** Runs the active special a tick, or counts down to the next while a target is near. */
	static void tick(final ServerLevel raid, final Blaze blaze) {
		Control control = control(blaze);
		Cast active = control.active;
		if (active != null) {
			step(raid, active);
			active.age++;
			if (active.done) {
				control.active = null;
				control.countdown = SPECIAL_EVERY_TICKS;
			}
			return;
		}
		LivingEntity target = blaze.getTarget();
		if (control.paused || target == null || !target.isAlive() || target.distanceTo(blaze) > SPECIAL_RANGE) {
			return;
		}
		if (--control.countdown <= 0) {
			cast(blaze, ROTATION.get(control.next++ % ROTATION.size()), target);
		}
	}

	/** Test hook: runs the active special one tick, as the server tick would. */
	static void tickOnce(final ServerLevel raid, final Blaze blaze) {
		tick(raid, blaze);
	}

	private static void step(final ServerLevel raid, final Cast cast) {
		switch (cast.special) {
			case FLAME_SPIRAL -> spiral(raid, cast);
			case BROOD -> brood(raid, cast);
			case DIVE -> dive(raid, cast);
			case METEOR_STORM -> meteors(raid, cast);
			case CHARGED_WAVE -> wave(raid, cast);
		}
	}

	private static void spiral(final ServerLevel raid, final Cast cast) {
		if (cast.age >= SPIRAL_TICKS) {
			cast.done = true;
			return;
		}
		if (cast.age % SPIRAL_EVERY != 0) {
			return;
		}
		Blaze blaze = cast.blaze;
		double y = blaze.getY() + blaze.getBbHeight() * 0.5;
		int volley = cast.age / SPIRAL_EVERY;
		for (int i = 0; i < SPIRAL_PER_VOLLEY; i++) {
			double angle = 2.0 * Math.PI * i / SPIRAL_PER_VOLLEY + volley * SPIRAL_TURN;
			Vec3 heading = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			SmallFireball fireball = new SmallFireball(raid, blaze, heading);
			fireball.setItem(new net.minecraft.world.item.ItemStack(NetherItems.SOUL_FIRE_CHARGE));
			fireball.setPos(blaze.getX() + heading.x * (blaze.getBbWidth() + 0.5), y, blaze.getZ() + heading.z * (blaze.getBbWidth() + 0.5));
			if (raid.addFreshEntity(fireball)) {
				cast.spawned.add(fireball);
				cast.headings.add(heading);
			}
		}
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), y, blaze.getZ(), 24, 1.0, 1.0, 1.0, 0.1);
		cast.lastActionAge = cast.age;
	}

	private static void brood(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		for (int i = 0; i < BROOD_SIZE; i++) {
			double angle = 2.0 * Math.PI * i / BROOD_SIZE;
			Blaze child = EntityTypes.BLAZE.create(raid, EntitySpawnReason.MOB_SUMMONED);
			if (child == null) {
				continue;
			}
			// A brood is ordinary blazes: the tag keeps the mobs mod's elite and boss rolls off them.
			child.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
			child.snapTo(blaze.getX() + Math.cos(angle) * BROOD_RING, blaze.getY() + 0.5, blaze.getZ() + Math.sin(angle) * BROOD_RING, 0.0F, 0.0F);
			child.setTarget(cast.target);
			if (raid.addFreshEntity(child)) {
				cast.spawned.add(child);
			}
		}
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), blaze.getY() + 1.0, blaze.getZ(), 40, BROOD_RING, 1.0, BROOD_RING, 0.05);
		cast.lastActionAge = cast.age;
		cast.done = true;
	}

	private static void dive(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		blaze.setDeltaMovement(Vec3.ZERO);
		if (cast.age < DIVE_RISE_TICKS) {
			blaze.setPos(blaze.getX(), cast.home.y + DIVE_RISE * (cast.age + 1) / DIVE_RISE_TICKS, blaze.getZ());
			return;
		}
		if (cast.age < DIVE_RISE_TICKS + DIVE_HOVER_TICKS) {
			raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), blaze.getY(), blaze.getZ(), 6, 0.8, 0.8, 0.8, 0.02);
			return;
		}
		Vec3 to = cast.centre.subtract(blaze.position());
		if (to.length() > DIVE_SPEED && cast.age < DIVE_MAX_TICKS) {
			Vec3 step = to.normalize().scale(DIVE_SPEED);
			blaze.setPos(blaze.getX() + step.x, blaze.getY() + step.y, blaze.getZ() + step.z);
			return;
		}
		blaze.setPos(cast.centre.x, cast.centre.y, cast.centre.z);
		AABB area = new AABB(cast.centre.x - DIVE_HALF_WIDTH, cast.centre.y - 4.0, cast.centre.z - DIVE_HALF_WIDTH,
			cast.centre.x + DIVE_HALF_WIDTH, cast.centre.y + 4.0, cast.centre.z + DIVE_HALF_WIDTH);
		for (LivingEntity victim : raid.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != blaze && entity.isAlive() && !(entity instanceof Blaze))) {
			victim.hurtServer(raid, raid.damageSources().mobAttack(blaze), DIVE_DAMAGE);
			victim.igniteForSeconds(DIVE_BURN_SECONDS);
			cast.hits.add(victim.getUUID());
		}
		RandomSource random = blaze.getRandom();
		BlockPos base = BlockPos.containing(cast.centre);
		for (int dx = (int) -DIVE_HALF_WIDTH; dx <= DIVE_HALF_WIDTH; dx++) {
			for (int dz = (int) -DIVE_HALF_WIDTH; dz <= DIVE_HALF_WIDTH; dz++) {
				if (random.nextDouble() >= DIVE_FIRE_CHANCE) {
					continue;
				}
				for (int dy = 2; dy >= -2; dy--) {
					BlockPos at = base.offset(dx, dy, dz);
					if (raid.getBlockState(at).isAir() && raid.getBlockState(at.below()).isFaceSturdy(raid, at.below(), Direction.UP)) {
						raid.setBlock(at, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
						cast.firesPlaced++;
						break;
					}
				}
			}
		}
		raid.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cast.centre.x, cast.centre.y + 1.0, cast.centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cast.centre.x, cast.centre.y + 0.5, cast.centre.z, 120, DIVE_HALF_WIDTH / 2, 0.5, DIVE_HALF_WIDTH / 2, 0.05);
		raid.playSound(null, BlockPos.containing(cast.centre), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
		cast.lastActionAge = cast.age;
		cast.done = true;
	}

	private static void meteors(final ServerLevel raid, final Cast cast) {
		if (cast.meteors >= METEOR_COUNT) {
			cast.done = true;
			return;
		}
		if (cast.age % METEOR_EVERY != 0) {
			return;
		}
		RandomSource random = cast.blaze.getRandom();
		AABB area = new AABB(cast.centre.x - METEOR_HALF_WIDTH, cast.centre.y - 4.0, cast.centre.z - METEOR_HALF_WIDTH,
			cast.centre.x + METEOR_HALF_WIDTH, cast.centre.y + 4.0, cast.centre.z + METEOR_HALF_WIDTH);
		Vec3 point = null;
		if (cast.meteors % METEOR_AIM_EVERY == 0) {
			List<LivingEntity> standing = raid.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && !(entity instanceof Blaze));
			if (!standing.isEmpty()) {
				Vec3 at = standing.get(random.nextInt(standing.size())).position();
				point = new Vec3(clamp(at.x + random.nextDouble() - 0.5, cast.centre.x), at.y, clamp(at.z + random.nextDouble() - 0.5, cast.centre.z));
			}
		}
		if (point == null) {
			point = new Vec3(cast.centre.x + (random.nextDouble() * 2 - 1) * METEOR_HALF_WIDTH, cast.centre.y,
				cast.centre.z + (random.nextDouble() * 2 - 1) * METEOR_HALF_WIDTH);
		}
		cast.meteors++;
		cast.impacts.add(point);
		final Vec3 impact = point;
		for (LivingEntity victim : raid.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(METEOR_RADIUS, 3.0, METEOR_RADIUS),
			entity -> entity.isAlive() && !(entity instanceof Blaze) && horizontal(entity.position(), impact) <= METEOR_RADIUS)) {
			victim.hurtServer(raid, raid.damageSources().mobAttack(cast.blaze), METEOR_DAMAGE);
			if (meteorStuns(random)) {
				stun(victim);
			}
			cast.hits.add(victim.getUUID());
		}
		raid.sendParticles(ParticleTypes.LAVA, impact.x, impact.y + 0.2, impact.z, 12, 0.6, 0.2, 0.6, 0.0);
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, impact.x, impact.y + 4.0, impact.z, 20, 0.2, 4.0, 0.2, 0.0);
		raid.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
		cast.lastActionAge = cast.age;
	}

	private static double clamp(final double value, final double centre) {
		return Math.max(centre - METEOR_HALF_WIDTH, Math.min(centre + METEOR_HALF_WIDTH, value));
	}

	public static boolean meteorStuns(final RandomSource random) {
		return random.nextDouble() < METEOR_STUN_CHANCE;
	}

	private static void wave(final ServerLevel raid, final Cast cast) {
		double radius = (cast.age + 1) * WAVE_SPEED;
		Blaze blaze = cast.blaze;
		Vec3 from = cast.home;
		AABB reach = new AABB(from, from).inflate(radius + 1.0, WAVE_HEIGHT, radius + 1.0);
		List<LivingEntity> near = raid.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != blaze && entity.isAlive() && !(entity instanceof Blaze));
		cast.seen.add((int) radius + ":" + near.size());
		for (LivingEntity victim : near) {
			double distance = horizontal(victim.position(), from);
			if (distance > radius || cast.hits.contains(victim.getUUID())) {
				continue;
			}
			cast.hits.add(victim.getUUID());
			victim.hurtServer(raid, raid.damageSources().mobAttack(blaze), WAVE_DAMAGE);
			Vec3 away = new Vec3(victim.getX() - from.x, 0.0, victim.getZ() - from.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			double resist = 1.0 - victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
			victim.setDeltaMovement(away.x * WAVE_KNOCKBACK * resist, 0.5 * resist, away.z * WAVE_KNOCKBACK * resist);
			// A player's own client moves it, so the push is sent to it.
			victim.needsSync = true;
			stun(victim);
		}
		for (int i = 0; i < 24; i++) {
			double angle = 2.0 * Math.PI * i / 24;
			raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x + Math.cos(angle) * radius, from.y + 0.5, from.z + Math.sin(angle) * radius, 1, 0.0, 0.1, 0.0, 0.0);
		}
		cast.lastActionAge = cast.age;
		if (radius >= WAVE_RADIUS) {
			cast.done = true;
		}
	}

	static double horizontal(final Vec3 a, final Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** All but rooted for three seconds: Slowness, Mining Fatigue and Weakness. */
	public static void stun(final LivingEntity victim) {
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, STUN_SLOWNESS));
		victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, STUN_TICKS, 2));
		victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 1));
	}
}
