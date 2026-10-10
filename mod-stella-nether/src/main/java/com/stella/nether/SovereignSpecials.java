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
	/** Marks a Sovereign's brood blaze, so the raid gives it a level of its own when it loads. */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> BROOD_MARK =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.createPersistent(StellaNether.id("brood"), com.mojang.serialization.Codec.BOOL);
	private static final double BROOD_RING = 3.0;

	public static final double DIVE_RISE = 8.0;
	public static final int DIVE_RISE_TICKS = 20;
	public static final int DIVE_HOVER_TICKS = 10;
	public static final double DIVE_SPEED = 1.5;
	private static final int DIVE_MAX_TICKS = 80;
	/** Half the side of the dive's square blast: twelve by twelve. */
	public static final double DIVE_HALF_WIDTH = 6.0;
	public static final float DIVE_DAMAGE = 6.0F;
	public static final int DIVE_BURN_SECONDS = 8;
	public static final double DIVE_FIRE_CHANCE = 0.35;

	/**
	 * The vortex: for thirty seconds the Sovereign channels behind a shield, slowing every player near it, while
	 * flames fly in to it from a sphere all around. Broken in time, the shield leaves it staggered; held to the end,
	 * it bursts in a sphere that breaks the ground and throws everyone back.
	 */
	public static final int VORTEX_TICKS = 30 * 20;
	/** The sphere the flames set out from, the reach of its slowing, and the reach of its burst. */
	public static final double VORTEX_RADIUS = 16.0;
	/** Slowness III, on every player within the sphere while it channels. */
	public static final int VORTEX_SLOW_AMPLIFIER = 2;
	private static final int VORTEX_SLOW_TICKS = 40;
	public static final double VORTEX_BULLET_SPEED = 0.35;
	public static final int VORTEX_BULLETS_EVERY = 4;
	public static final int VORTEX_BULLETS_EACH = 3;
	/** A flame this near a player's body has hit them. */
	public static final double VORTEX_HIT_REACH = 0.3;
	public static final float VORTEX_BULLET_DAMAGE = 1.5F;
	public static final int VORTEX_BULLET_BURN_SECONDS = 4;
	/** The fixed share of the Sovereign's whole health the shield holds: a fifth. */
	public static final double VORTEX_SHIELD_SHARE = 0.20;
	/** How long a Sovereign whose shield is broken stands staggered, neither moving nor attacking. */
	public static final int VORTEX_STAGGER_TICKS = 8 * 20;
	public static final float VORTEX_BLAST_DAMAGE = 10.0F;
	public static final float VORTEX_BLAST_KNOCKBACK = 3.0F;
	public static final net.minecraft.resources.Identifier VORTEX_SHIELD_ID = StellaNether.id("vortex_shield");
	private static final int SHIELD_COLOUR = 0x5AA0FF;

	public static final double WAVE_RADIUS = 16.0;
	public static final double WAVE_SPEED = 1.0;
	public static final double WAVE_HEIGHT = 4.0;
	public static final float WAVE_DAMAGE = 1.5F;
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
	public static final float NOVA_DAMAGE = 2.5F;
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
		/** The vortex: its shield, where it holds the Sovereign, its centre, when it staggered, and its burst. */
		private boolean shield;
		private Vec3 shieldAt = Vec3.ZERO;
		private Vec3 vortexCentre = Vec3.ZERO;
		private int staggeredAt = -1;
		private boolean blasted;
		private int blastBroken;
		/** Every player the vortex slowed. */
		private final Set<UUID> slowed = new HashSet<>();
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

		public Vec3 shieldAt() {
			return this.shieldAt;
		}

		public Vec3 vortexCentre() {
			return this.vortexCentre;
		}

		public int staggeredAt() {
			return this.staggeredAt;
		}

		public boolean blasted() {
			return this.blasted;
		}

		public int blastBroken() {
			return this.blastBroken;
		}

		public Set<UUID> slowed() {
			return this.slowed;
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
		HIT,
		/** The channel ended with it still in flight. */
		FADED
	}

	/** One flame the vortex sends in from its edge: blue bursts where it hits, yellow burns. */
	public static final class Bullet {
		private Vec3 position;
		private final Vec3 from;
		private final boolean blue;
		private final double spawnRadius;
		private @Nullable BulletEnd end;
		private @Nullable UUID hit;

		Bullet(final Vec3 position, final boolean blue, final double spawnRadius) {
			this.position = position;
			this.from = position;
			this.blue = blue;
			this.spawnRadius = spawnRadius;
		}

		public Vec3 position() {
			return this.position;
		}

		public Vec3 from() {
			return this.from;
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

	/**
	 * Whether the vortex holds the Sovereign: channelling behind its shield, or staggered once it broke. It can
	 * neither move nor attack meanwhile.
	 */
	public static boolean shielded(final Blaze blaze) {
		Control control = CONTROLS.get(blaze.getUUID());
		Cast active = control == null ? null : control.active;
		return active != null && active.special == Special.VORTEX && !active.done && (active.shield || active.staggeredAt >= 0);
	}

	public static boolean airborne(final Blaze blaze) {
		Control control = CONTROLS.get(blaze.getUUID());
		return control != null && control.active != null && control.active.special == Special.DIVE && !control.active.done;
	}

	static void forget(final UUID sovereign) {
		CONTROLS.remove(sovereign);
	}

	/** Starts a special at once against a target. */
	public static Cast cast(final Blaze blaze, final Special special, final LivingEntity target) {
		Control control = control(blaze);
		Cast cast = new Cast(special, blaze, target);
		control.active = cast;
		control.last = cast;
		control.history.add(special);
		BehaviorLog.info("[NETHER] sovereign special={} target={}", special, target.getScoreboardName());
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
			// Ordinary blazes: the tag keeps elite and boss rolls off while they load.
			child.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
			child.setAttached(BROOD_MARK, true);
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
		if (cast.age == 0) {
			cast.vortexCentre = new Vec3(blaze.getX(), blaze.getY() + blaze.getBbHeight() * 0.5, blaze.getZ());
			raiseShield(raid, cast);
		}
		holdStill(cast);
		if (cast.staggeredAt >= 0) {
			raid.sendParticles(ParticleTypes.CRIT, blaze.getX(), blaze.getY() + blaze.getBbHeight() + 0.3, blaze.getZ(), 3, 0.6, 0.1, 0.6, 0.0);
			if (cast.age - cast.staggeredAt >= VORTEX_STAGGER_TICKS) {
				cast.lastActionAge = cast.age;
				cast.done = true;
				BehaviorLog.info("[NETHER] sovereign stagger over after={}", cast.age - cast.staggeredAt);
			}
			return;
		}
		if (blaze.getAbsorptionAmount() <= 0.0F) {
			// Broken while it channelled: the vortex fails and the Sovereign reels.
			dropShield(blaze);
			cast.shield = false;
			cast.staggeredAt = cast.age;
			fadeBullets(cast);
			raid.playSound(null, blaze.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
			raid.sendParticles(ParticleTypes.ELECTRIC_SPARK, blaze.getX(), blaze.getY() + blaze.getBbHeight() * 0.5, blaze.getZ(), 60, 1.0, 1.5, 1.0, 0.2);
			BehaviorLog.info("[NETHER] sovereign shield broken at={} staggered", cast.age);
			return;
		}
		if (cast.age >= VORTEX_TICKS) {
			// Held to the end: it bursts.
			dropShield(blaze);
			cast.shield = false;
			fadeBullets(cast);
			vortexBlast(raid, cast);
			cast.lastActionAge = cast.age;
			cast.done = true;
			return;
		}
		showShield(raid, cast);
		if (cast.age % 10 == 0) {
			for (ServerPlayer player : raid.players()) {
				if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.position().distanceTo(cast.vortexCentre) <= VORTEX_RADIUS) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, VORTEX_SLOW_TICKS, VORTEX_SLOW_AMPLIFIER), blaze);
					cast.slowed.add(player.getUUID());
				}
			}
		}
		if (cast.age % VORTEX_BULLETS_EVERY == 0) {
			RandomSource random = blaze.getRandom();
			for (int i = 0; i < VORTEX_BULLETS_EACH; i++) {
				// A direction evenly over the whole sphere: above, below and all around.
				Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
				vortexBullet(cast, direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : direction, random.nextBoolean());
			}
		}
		moveBullets(raid, cast);
	}

	/** Sends one flame in to the vortex's centre from its sphere, along a direction. Test hook, and the vortex's own. */
	static Bullet vortexBullet(final Cast cast, final Vec3 direction, final boolean blue) {
		Vec3 at = cast.vortexCentre.add(direction.normalize().scale(VORTEX_RADIUS));
		Bullet bullet = new Bullet(at, blue, at.distanceTo(cast.vortexCentre));
		cast.bullets.add(bullet);
		return bullet;
	}

	private static void fadeBullets(final Cast cast) {
		for (Bullet bullet : cast.bullets) {
			if (bullet.end == null) {
				bullet.end = BulletEnd.FADED;
			}
		}
	}

	private static void moveBullets(final ServerLevel raid, final Cast cast) {
		Vec3 centre = cast.vortexCentre;
		for (Bullet bullet : cast.bullets) {
			if (bullet.end != null) {
				continue;
			}
			Vec3 in = centre.subtract(bullet.position);
			double left = in.length();
			if (left <= VORTEX_BULLET_SPEED) {
				bullet.position = centre;
				bullet.end = BulletEnd.CENTRE;
				raid.sendParticles(bullet.blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, centre.x, centre.y, centre.z, 4, 0.2, 0.2, 0.2, 0.02);
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

	/** The channel begins: a shield of {@link #VORTEX_SHIELD_SHARE} of its whole health, as absorption. */
	private static void raiseShield(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		double points = shieldPoints(blaze);
		net.minecraft.world.entity.ai.attributes.AttributeInstance max = blaze.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
		if (max != null) {
			max.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(VORTEX_SHIELD_ID, points,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}
		blaze.setAbsorptionAmount((float) points);
		cast.shield = true;
		cast.shieldAt = blaze.position();
		raid.playSound(null, blaze.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
		BehaviorLog.info("[NETHER] sovereign vortex shield up points={} whole={}", points, VORTEX_SHIELD_SHARE * BlazeSovereign.effectiveMaxHealth(blaze));
	}

	/** The shield in absorption points: its whole-health size through the divisor, then the rpg multiplier on hits. */
	private static double shieldPoints(final Blaze blaze) {
		Double divisor = blaze.getAttached(BlazeSovereign.DIVISOR);
		return VORTEX_SHIELD_SHARE * BlazeSovereign.effectiveMaxHealth(blaze) / (divisor == null || divisor < 1.0 ? 1.0 : divisor) * BlazeSovereign.healthScale();
	}

	private static void dropShield(final Blaze blaze) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance max = blaze.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
		if (max != null) {
			max.removeModifier(VORTEX_SHIELD_ID);
		}
		blaze.setAbsorptionAmount(0.0F);
	}

	/** It cannot move while it channels or reels. */
	private static void holdStill(final Cast cast) {
		Blaze blaze = cast.blaze;
		blaze.setDeltaMovement(Vec3.ZERO);
		blaze.getNavigation().stop();
		blaze.setPos(cast.shieldAt.x, cast.shieldAt.y, cast.shieldAt.z);
	}

	/** The shield drawn as a shell of blue motes around it, fewer and smaller as it wears down. */
	private static void showShield(final ServerLevel raid, final Cast cast) {
		if (cast.age % 2 != 0) {
			return;
		}
		Blaze blaze = cast.blaze;
		double left = Math.max(0.0, Math.min(1.0, blaze.getAbsorptionAmount() / shieldPoints(blaze)));
		int motes = 2 + (int) Math.round(28 * left);
		net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(SHIELD_COLOUR, (float) (0.6 + 1.4 * left));
		double radius = blaze.getBbHeight() * 0.6;
		RandomSource random = blaze.getRandom();
		for (int i = 0; i < motes; i++) {
			Vec3 on = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
			if (on.lengthSqr() < 1.0E-6) {
				continue;
			}
			Vec3 at = cast.vortexCentre.add(on.normalize().scale(radius));
			raid.sendParticles(dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Held to the end: a sphere of {@link #VORTEX_RADIUS} breaks every breakable block and throws everyone back, hard. */
	private static void vortexBlast(final ServerLevel raid, final Cast cast) {
		Blaze blaze = cast.blaze;
		Vec3 centre = cast.vortexCentre;
		int reach = (int) Math.ceil(VORTEX_RADIUS);
		BlockPos base = BlockPos.containing(centre);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dy = -reach; dy <= reach; dy++) {
				for (int dz = -reach; dz <= reach; dz++) {
					at.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
					if (Vec3.atCenterOf(at).distanceToSqr(centre) > VORTEX_RADIUS * VORTEX_RADIUS) {
						continue;
					}
					net.minecraft.world.level.block.state.BlockState state = raid.getBlockState(at);
					// Liquids are left, and so is anything that cannot be broken.
					if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(raid, at) < 0.0F) {
						continue;
					}
					raid.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					cast.blastBroken++;
				}
			}
		}
		for (LivingEntity victim : raid.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(VORTEX_RADIUS),
			entity -> entity != blaze && entity.isAlive() && !(entity instanceof Blaze))) {
			Vec3 middle = victim.getBoundingBox().getCenter();
			if (middle.distanceTo(centre) > VORTEX_RADIUS) {
				continue;
			}
			Vec3 away = new Vec3(middle.x - centre.x, 0.0, middle.z - centre.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			double resist = 1.0 - victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
			Vec3 push = new Vec3(away.x * VORTEX_BLAST_KNOCKBACK * resist, 0.8 * resist, away.z * VORTEX_BLAST_KNOCKBACK * resist);
			cast.hits.add(victim.getUUID());
			cast.pushes.put(victim.getUUID(), push);
			victim.damageCooldownTime = 0;
			victim.hurtServer(raid, raid.damageSources().mobAttack(blaze), VORTEX_BLAST_DAMAGE);
			victim.setDeltaMovement(push);
			victim.needsSync = true;
		}
		cast.blasted = true;
		raid.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z, 6, VORTEX_RADIUS / 3.0, VORTEX_RADIUS / 3.0, VORTEX_RADIUS / 3.0, 0.0);
		raid.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x, centre.y, centre.z, 400, VORTEX_RADIUS / 2.0, VORTEX_RADIUS / 2.0, VORTEX_RADIUS / 2.0, 0.3);
		raid.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10.0F, 0.5F);
		BehaviorLog.info("[NETHER] sovereign vortex burst broken={} hit={}", cast.blastBroken, cast.hits.size());
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
