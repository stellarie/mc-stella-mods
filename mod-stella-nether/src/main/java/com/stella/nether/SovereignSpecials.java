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
import net.minecraft.server.level.ServerPlayer;
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
 * The Blaze Sovereign's six specials, cast in turn while it has a player to fight: a spiral of fireballs,
 * a brood of blazes, a burning dive, the vortex and its shield, a charged wave, and the soul nova. Each cast records what it spawned,
 * whom it hit and where it struck, so its effect can be judged from the record.
 */
public final class SovereignSpecials {
	public enum Special {
		FLAME_SPIRAL,
		BROOD,
		DIVE,
		VORTEX,
		CHARGED_WAVE,
		SOUL_NOVA
	}

	public static final List<Special> ROTATION = List.of(Special.FLAME_SPIRAL, Special.BROOD, Special.DIVE, Special.VORTEX, Special.CHARGED_WAVE, Special.SOUL_NOVA);
	/** Ticks from first facing a player to the first special, and the rest after each special ends. */
	public static final int FIRST_SPECIAL_TICKS = 60;
	public static final int SPECIAL_EVERY_TICKS = 160;
	/** A special is cast only at a target this near. */
	public static final double SPECIAL_RANGE = 32.0;

	public static final int SPIRAL_TICKS = 100;
	public static final int SPIRAL_EVERY = 5;
	public static final int SPIRAL_PER_VOLLEY = 8;
	private static final double SPIRAL_TURN = Math.PI / 16.0;
	/** How far a spiral fireball flies before it is spent. */
	public static final double SPIRAL_REACH = 32.0;

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

	/** The vortex: twelve seconds pulling players in over a 32 by 32 square, while flames fly in from its edge. */
	public static final int VORTEX_TICKS = 12 * 20;
	public static final double VORTEX_HALF = 16.0;
	/** The pull added toward the centre each tick, and the most it builds to. */
	public static final double VORTEX_PULL = 0.04;
	public static final double VORTEX_PULL_MAX = 0.3;
	/** Flames fly inward faster than anyone is pulled. */
	public static final double VORTEX_BULLET_SPEED = 0.35;
	public static final int VORTEX_BULLETS_EVERY = 4;
	public static final int VORTEX_BULLETS_EACH = 3;
	/** A flame this near a player's body has hit them. */
	public static final double VORTEX_HIT_REACH = 0.3;
	public static final float VORTEX_BULLET_DAMAGE = 4.0F;
	public static final int VORTEX_BULLET_BURN_SECONDS = 4;
	/** After the pull, a shield of this much health, in the Sovereign's whole health. */
	public static final double VORTEX_SHIELD = 200.0;
	public static final int VORTEX_SUMMON_EVERY = 5 * 20;
	public static final int VORTEX_SUMMON_MIN = 2;
	public static final int VORTEX_SUMMON_MAX = 4;
	/** Living shield summons at most, so a shield nobody breaks cannot fill the fortress. */
	public static final int VORTEX_SUMMON_CAP = 12;
	public static final net.minecraft.resources.Identifier VORTEX_SHIELD_ID = StellaNether.id("vortex_shield");

	public static final double WAVE_RADIUS = 16.0;
	public static final double WAVE_SPEED = 1.0;
	public static final double WAVE_HEIGHT = 4.0;
	public static final float WAVE_DAMAGE = 4.0F;
	public static final float WAVE_KNOCKBACK = 2.5F;

	/** The soul nova: slow blue flames in every direction, which burst where they are after five seconds. */
	public static final int NOVA_RING = 12;
	public static final int NOVA_UPPER = 6;
	public static final double NOVA_UPPER_PITCH_DEGREES = 25.0;
	public static final double NOVA_SPEED = 0.15;
	/** A flame bursts at whichever comes first: a wall, this far from where it set out, or this long in flight. */
	public static final double NOVA_REACH = 16.0;
	public static final int NOVA_LIFE_TICKS = 6 * 20;
	public static final double NOVA_BURST_RADIUS = 3.0;
	public static final float NOVA_DAMAGE = 6.0F;
	public static final float NOVA_KNOCKBACK = 1.5F;
	/** Soul flames each burst sets on the blocks around it. */
	public static final int NOVA_FLAMES_EACH = 3;
	private static final int NOVA_FLAME_SPREAD = 2;

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
		/** A nova's flames: where each is, and which have stopped against a block. */
		private final List<Vec3> orbs = new ArrayList<>();
		/** The age each of a nova's flames burst at, by its place; a flame not yet burst is absent. */
		private final Map<Integer, Integer> burstAt = new java.util.HashMap<>();
		/** How far each ranged fireball had flown when it was spent at its reach, by its identity. */
		private final Map<UUID, Double> spent = new ConcurrentHashMap<>();
		/** Who a burst threw back, and the push it gave. */
		private final java.util.Map<UUID, Vec3> pushes = new java.util.HashMap<>();
		/** The blocks it broke moving itself through them. */
		private final List<BlockPos> broken = new ArrayList<>();
		/** The soul flames a nova set. */
		private final List<BlockPos> flames = new ArrayList<>();
		/** Where a nova's flames set out from. */
		private Vec3 novaFrom = Vec3.ZERO;
		/** The vortex's shield is up, where it stands, when it rose, and each wave it has called. */
		private boolean shield;
		private Vec3 shieldAt = Vec3.ZERO;
		private int shieldAge = -1;
		private final List<List<Blaze>> waves = new ArrayList<>();
		private final List<Vec3> summonedAt = new ArrayList<>();
		/** Every flame the vortex has sent in. */
		private final List<Bullet> bullets = new ArrayList<>();
		private int age;
		private int lastActionAge = -1;
		private int firesPlaced;
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

		public boolean shield() {
			return this.shield;
		}

		public int shieldAge() {
			return this.shieldAge;
		}

		public List<List<Blaze>> waves() {
			return this.waves;
		}

		public List<Vec3> summonedAt() {
			return this.summonedAt;
		}

		public List<Bullet> bullets() {
			return this.bullets;
		}

		public Map<Integer, Integer> burstAt() {
			return this.burstAt;
		}

		public Map<UUID, Double> spent() {
			return this.spent;
		}

		public List<Vec3> orbs() {
			return this.orbs;
		}

		public java.util.Map<UUID, Vec3> pushes() {
			return this.pushes;
		}

		public List<BlockPos> broken() {
			return this.broken;
		}

		public List<BlockPos> flames() {
			return this.flames;
		}

		public int age() {
			return this.age;
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

	/** How a vortex flame ended: it reached the centre, or it hit a player. */
	public enum BulletEnd {
		CENTRE,
		HIT
	}

	/** One flame the vortex sends in from its edge: blue bursts where it hits, yellow burns. */
	public static final class Bullet {
		private Vec3 position;
		private final boolean blue;
		private final double spawnRadius;
		private @Nullable BulletEnd end;
		private @Nullable UUID hit;

		Bullet(final Vec3 position, final boolean blue, final double spawnRadius) {
			this.position = position;
			this.blue = blue;
			this.spawnRadius = spawnRadius;
		}

		public Vec3 position() {
			return this.position;
		}

		public boolean blue() {
			return this.blue;
		}

		public double spawnRadius() {
			return this.spawnRadius;
		}

		public @Nullable BulletEnd end() {
			return this.end;
		}

		public @Nullable UUID hit() {
			return this.hit;
		}
	}

	/** A fireball that is spent once it has flown its reach from where it set out. */
	private record Ranged(Entity fireball, Vec3 origin, double reach, Cast cast) {
	}

	private static final Map<UUID, Ranged> RANGED = new ConcurrentHashMap<>();

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> spendRanged());
	}

	/** Spends every ranged fireball past its reach; one gone on its own, by a hit, is only forgotten. */
	static void spendRanged() {
		for (Ranged ranged : List.copyOf(RANGED.values())) {
			Entity fireball = ranged.fireball();
			if (fireball.isRemoved()) {
				RANGED.remove(fireball.getUUID());
				continue;
			}
			double flown = fireball.position().distanceTo(ranged.origin());
			if (flown >= ranged.reach()) {
				ranged.cast().spent.put(fireball.getUUID(), flown);
				fireball.discard();
				RANGED.remove(fireball.getUUID());
			}
		}
	}

	private SovereignSpecials() {
	}

	private static Control control(final Blaze blaze) {
		return CONTROLS.computeIfAbsent(blaze.getUUID(), id -> new Control());
	}

	/** Stops the Sovereign starting specials on its own; a test casts them by hand. */
	/** Whether a test has held this Sovereign's specials; its charges wait on the same hold. */
	public static boolean paused(final Blaze blaze) {
		Control control = CONTROLS.get(blaze.getUUID());
		return control != null && control.paused;
	}

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
	/** Whether a special is running for this blaze; its normal attack holds meanwhile. */
	public static boolean casting(final Blaze blaze) {
		Control control = CONTROLS.get(blaze.getUUID());
		return control != null && control.active != null && !control.active.done;
	}

	/** Whether the Sovereign stands behind the vortex's shield: it can neither move nor attack meanwhile. */
	public static boolean shielded(final Blaze blaze) {
		Control control = CONTROLS.get(blaze.getUUID());
		return control != null && control.active != null && control.active.shield && !control.active.done;
	}

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
			case VORTEX -> vortex(raid, cast);
			case CHARGED_WAVE -> wave(raid, cast);
			case SOUL_NOVA -> nova(raid, cast);
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
				RANGED.put(fireball.getUUID(), new Ranged(fireball, fireball.position(), SPIRAL_REACH, cast));
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
			cast.broken.addAll(SovereignBody.moveThrough(raid, blaze, blaze.getX(), cast.home.y + DIVE_RISE * (cast.age + 1) / DIVE_RISE_TICKS, blaze.getZ()));
			return;
		}
		if (cast.age < DIVE_RISE_TICKS + DIVE_HOVER_TICKS) {
			raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), blaze.getY(), blaze.getZ(), 6, 0.8, 0.8, 0.8, 0.02);
			return;
		}
		Vec3 to = cast.centre.subtract(blaze.position());
		if (to.length() > DIVE_SPEED && cast.age < DIVE_MAX_TICKS) {
			Vec3 step = to.normalize().scale(DIVE_SPEED);
			cast.broken.addAll(SovereignBody.moveThrough(raid, blaze, blaze.getX() + step.x, blaze.getY() + step.y, blaze.getZ() + step.z));
			return;
		}
		cast.broken.addAll(SovereignBody.moveThrough(raid, blaze, cast.centre.x, cast.centre.y, cast.centre.z));
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

	private static void vortex(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		Vec3 centre = cast.home;
		if (cast.age < VORTEX_TICKS) {
			for (ServerPlayer player : raid.players()) {
				if (!player.isAlive() || player.isSpectator() || player.isCreative() || !inSquare(player.position(), centre)) {
					continue;
				}
				Vec3 in = new Vec3(centre.x - player.getX(), 0.0, centre.z - player.getZ());
				if (in.lengthSqr() < 1.0E-4) {
					continue;
				}
				Vec3 motion = player.getDeltaMovement().add(in.normalize().scale(VORTEX_PULL));
				Vec3 flat = new Vec3(motion.x, 0.0, motion.z);
				if (flat.length() > VORTEX_PULL_MAX) {
					flat = flat.normalize().scale(VORTEX_PULL_MAX);
				}
				player.setDeltaMovement(flat.x, motion.y, flat.z);
				// A player's own client moves it, so the pull is sent to it.
				player.needsSync = true;
				cast.pushes.put(player.getUUID(), in.normalize().scale(VORTEX_PULL));
			}
			if (cast.age % VORTEX_BULLETS_EVERY == 0) {
				RandomSource random = blaze.getRandom();
				for (int i = 0; i < VORTEX_BULLETS_EACH; i++) {
					vortexBullet(cast, random.nextDouble() * 2.0 * Math.PI, random.nextBoolean());
				}
			}
			for (int i = 0; i < 16; i++) {
				double angle = 2.0 * Math.PI * i / 16 + cast.age * 0.05;
				raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x + Math.cos(angle) * VORTEX_HALF, centre.y + 1.0, centre.z + Math.sin(angle) * VORTEX_HALF,
					1, 0.0, 0.2, 0.0, 0.0);
			}
		} else if (!cast.shield) {
			raiseShield(raid, cast);
		}
		moveBullets(raid, cast);
		if (cast.shield) {
			holdShield(raid, cast);
		}
	}

	static boolean inSquare(final Vec3 at, final Vec3 centre) {
		return Math.abs(at.x - centre.x) <= VORTEX_HALF && Math.abs(at.z - centre.z) <= VORTEX_HALF && Math.abs(at.y - centre.y) <= VORTEX_HALF;
	}

	/** Sends one flame in from the vortex's edge at an angle. Test hook, and the vortex's own. */
	static Bullet vortexBullet(final Cast cast, final double angle, final boolean blue) {
		Vec3 centre = cast.home;
		Vec3 at = new Vec3(centre.x + Math.cos(angle) * VORTEX_HALF, centre.y + 1.0, centre.z + Math.sin(angle) * VORTEX_HALF);
		Bullet bullet = new Bullet(at, blue, horizontal(at, centre));
		cast.bullets.add(bullet);
		return bullet;
	}

	private static void moveBullets(final ServerLevel raid, final Cast cast) {
		Vec3 centre = cast.home;
		for (Bullet bullet : cast.bullets) {
			if (bullet.end != null) {
				continue;
			}
			Vec3 in = new Vec3(centre.x - bullet.position.x, 0.0, centre.z - bullet.position.z);
			double left = in.length();
			if (left <= VORTEX_BULLET_SPEED) {
				bullet.position = new Vec3(centre.x, bullet.position.y, centre.z);
				bullet.end = BulletEnd.CENTRE;
				raid.sendParticles(bullet.blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, centre.x, bullet.position.y, centre.z, 4, 0.2, 0.2, 0.2, 0.02);
				continue;
			}
			bullet.position = bullet.position.add(in.scale(VORTEX_BULLET_SPEED / left));
			ServerPlayer struck = null;
			for (ServerPlayer player : raid.players()) {
				if (player.isAlive() && !player.isSpectator() && player.getBoundingBox().inflate(VORTEX_HIT_REACH).contains(bullet.position)) {
					struck = player;
					break;
				}
			}
			if (struck != null) {
				bullet.end = BulletEnd.HIT;
				bullet.hit = struck.getUUID();
				if (bullet.blue) {
					novaBurst(raid, cast, bullet.position, false);
				} else {
					struck.hurtServer(raid, raid.damageSources().mobAttack(cast.blaze), VORTEX_BULLET_DAMAGE);
					struck.igniteForSeconds(VORTEX_BULLET_BURN_SECONDS);
					raid.sendParticles(ParticleTypes.FLAME, bullet.position.x, bullet.position.y, bullet.position.z, 12, 0.3, 0.3, 0.3, 0.05);
				}
				continue;
			}
			raid.sendParticles(bullet.blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, bullet.position.x, bullet.position.y, bullet.position.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** The pull ends: the Sovereign takes a shield of {@link #VORTEX_SHIELD}, in its whole health, and stands still behind it. */
	private static void raiseShield(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		Double divisor = blaze.getAttached(BlazeSovereign.DIVISOR);
		double points = VORTEX_SHIELD / (divisor == null || divisor < 1.0 ? 1.0 : divisor);
		net.minecraft.world.entity.ai.attributes.AttributeInstance max = blaze.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
		if (max != null) {
			max.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(VORTEX_SHIELD_ID, points,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}
		blaze.setAbsorptionAmount((float) points);
		cast.shield = true;
		cast.shieldAt = blaze.position();
		cast.shieldAge = cast.age;
		raid.playSound(null, blaze.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
		StellaNether.LOGGER.info("[NETHER] sovereign shield up points={} whole={}", points, VORTEX_SHIELD);
	}

	private static void holdShield(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		if (blaze.getAbsorptionAmount() <= 0.0F) {
			net.minecraft.world.entity.ai.attributes.AttributeInstance max = blaze.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
			if (max != null) {
				max.removeModifier(VORTEX_SHIELD_ID);
			}
			cast.shield = false;
			cast.lastActionAge = cast.age;
			cast.done = true;
			raid.playSound(null, blaze.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
			StellaNether.LOGGER.info("[NETHER] sovereign shield broken waves={}", cast.waves.size());
			return;
		}
		// It cannot move while the shield holds.
		blaze.setDeltaMovement(Vec3.ZERO);
		blaze.getNavigation().stop();
		blaze.setPos(cast.shieldAt.x, cast.shieldAt.y, cast.shieldAt.z);
		double y = blaze.getY() + blaze.getBbHeight() * 0.5;
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, blaze.getX(), y, blaze.getZ(), 4, blaze.getBbWidth(), blaze.getBbHeight() * 0.5, blaze.getBbWidth(), 0.0);
		if ((cast.age - cast.shieldAge) % VORTEX_SUMMON_EVERY == 0) {
			summonWave(raid, cast);
		}
	}

	private static void summonWave(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		long alive = cast.waves.stream().flatMap(List::stream).filter(Blaze::isAlive).count();
		RandomSource random = blaze.getRandom();
		int count = VORTEX_SUMMON_MIN + random.nextInt(VORTEX_SUMMON_MAX - VORTEX_SUMMON_MIN + 1);
		List<Blaze> wave = new ArrayList<>();
		for (int i = 0; i < count && alive + wave.size() < VORTEX_SUMMON_CAP; i++) {
			BlockPos spot = groundIn(raid, cast.home, random);
			if (spot == null) {
				continue;
			}
			Blaze child = EntityTypes.BLAZE.create(raid, EntitySpawnReason.MOB_SUMMONED);
			if (child == null) {
				continue;
			}
			// Ordinary blazes: the tag keeps the mobs mod's elite and boss rolls off them.
			child.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
			child.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			child.setTarget(cast.target);
			if (raid.addFreshEntity(child)) {
				wave.add(child);
				cast.summonedAt.add(child.position());
				raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, child.getX(), child.getY() + 1.0, child.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
			}
		}
		cast.waves.add(wave);
		StellaNether.LOGGER.info("[NETHER] sovereign shield wave={} called={}", cast.waves.size(), wave.size());
	}

	/** A spot in the vortex's square with a floor and room for a blaze, if one is found in a few tries. */
	private static @Nullable BlockPos groundIn(final ServerLevel raid, final Vec3 centre, final RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			BlockPos column = BlockPos.containing(centre.x + (random.nextDouble() * 2.0 - 1.0) * VORTEX_HALF, centre.y, centre.z + (random.nextDouble() * 2.0 - 1.0) * VORTEX_HALF);
			for (int dy = 4; dy >= -8; dy--) {
				BlockPos feet = column.above(dy);
				BlockPos floor = feet.below();
				if (raid.getBlockState(floor).isFaceSturdy(raid, floor, Direction.UP) && raid.getBlockState(feet).getCollisionShape(raid, feet).isEmpty()
					&& raid.getBlockState(feet.above()).getCollisionShape(raid, feet.above()).isEmpty() && raid.getFluidState(feet).isEmpty()) {
					return feet;
				}
			}
		}
		return null;
	}

	private static void nova(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		if (cast.age == 0) {
			Vec3 from = new Vec3(blaze.getX(), blaze.getY() + blaze.getBbHeight() * 0.5, blaze.getZ());
			cast.novaFrom = from;
			double pitch = Math.toRadians(NOVA_UPPER_PITCH_DEGREES);
			for (int i = 0; i < NOVA_RING; i++) {
				double angle = 2.0 * Math.PI * i / NOVA_RING;
				cast.headings.add(new Vec3(Math.cos(angle), 0.0, Math.sin(angle)));
				cast.orbs.add(from);
			}
			for (int i = 0; i < NOVA_UPPER; i++) {
				double angle = 2.0 * Math.PI * (i + 0.5) / NOVA_UPPER;
				cast.headings.add(new Vec3(Math.cos(angle) * Math.cos(pitch), Math.sin(pitch), Math.sin(angle) * Math.cos(pitch)));
				cast.orbs.add(from);
			}
			raid.playSound(null, blaze.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
			cast.lastActionAge = cast.age;
		}
		for (int i = 0; i < cast.orbs.size(); i++) {
			if (cast.burstAt.containsKey(i)) {
				continue;
			}
			Vec3 orb = cast.orbs.get(i);
			Vec3 next = orb.add(cast.headings.get(i).scale(NOVA_SPEED));
			BlockPos into = BlockPos.containing(next);
			boolean wall = !raid.getBlockState(into).getCollisionShape(raid, into).isEmpty();
			if (!wall) {
				cast.orbs.set(i, next);
				orb = next;
			}
			// A flame bursts at a wall, at its reach, or at the end of its life, whichever comes first.
			if (wall || orb.distanceTo(cast.novaFrom) >= NOVA_REACH || cast.age + 1 >= NOVA_LIFE_TICKS) {
				cast.burstAt.put(i, cast.age);
				novaBurst(raid, cast, orb);
				cast.lastActionAge = cast.age;
			} else {
				raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, orb.x, orb.y, orb.z, 3, 0.15, 0.15, 0.15, 0.01);
			}
		}
		if (cast.burstAt.size() == cast.orbs.size()) {
			cast.done = true;
		}
	}

	/** One flame bursts: it hurts and throws back what stands near, and sets soul flames about, breaking nothing. */
	private static void novaBurst(final ServerLevel raid, final Cast cast, final Vec3 at) {
		novaBurst(raid, cast, at, true);
	}

	private static void novaBurst(final ServerLevel raid, final Cast cast, final Vec3 at, final boolean hurtOnce) {
		Blaze blaze = cast.blaze;
		cast.impacts.add(at);
		raid.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 30, 0.8, 0.8, 0.8, 0.08);
		raid.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.3F);
		AABB reach = new AABB(at, at).inflate(NOVA_BURST_RADIUS);
		List<LivingEntity> near = raid.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != blaze && entity.isAlive() && !(entity instanceof Blaze));
		for (LivingEntity victim : near) {
			Vec3 middle = victim.getBoundingBox().getCenter();
			if (middle.distanceTo(at) > NOVA_BURST_RADIUS) {
				continue;
			}
			if (cast.hits.add(victim.getUUID()) || !hurtOnce) {
				victim.damageCooldownTime = 0;
				victim.hurtServer(raid, raid.damageSources().mobAttack(blaze), NOVA_DAMAGE);
			}
			Vec3 away = new Vec3(middle.x - at.x, 0.0, middle.z - at.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			double resist = 1.0 - victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
			Vec3 push = new Vec3(away.x * NOVA_KNOCKBACK * resist, 0.4 * resist, away.z * NOVA_KNOCKBACK * resist);
			victim.setDeltaMovement(push);
			victim.needsSync = true;
			cast.pushes.put(victim.getUUID(), push);
		}
		RandomSource random = blaze.getRandom();
		for (int i = 0; i < NOVA_FLAMES_EACH; i++) {
			BlockPos column = BlockPos.containing(at).offset(random.nextInt(2 * NOVA_FLAME_SPREAD + 1) - NOVA_FLAME_SPREAD, 0,
				random.nextInt(2 * NOVA_FLAME_SPREAD + 1) - NOVA_FLAME_SPREAD);
			for (int dy = 2; dy >= -12; dy--) {
				BlockPos spot = column.above(dy);
				if (raid.getBlockState(spot).isAir() && NetherBlocks.SOUL_FLAME.defaultBlockState().canSurvive(raid, spot)) {
					raid.setBlock(spot, NetherBlocks.SOUL_FLAME.defaultBlockState(), Block.UPDATE_ALL);
					cast.flames.add(spot);
					break;
				}
			}
		}
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
