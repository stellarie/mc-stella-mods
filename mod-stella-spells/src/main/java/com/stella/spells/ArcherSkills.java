package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The archer books: instant hit-scan rays with an arrow trail, never an arrow entity. A ray deals its
 * damage through {@link RpgBridge#skillDamage} with {@link School#ARCHER} exactly once, so no shot takes the
 * ranged dice a second time. Three books instead arm the next real arrow, and {@link SpellStatus#onHurt}
 * hands that arrow its blast through {@link #projectileDamage}.
 */
public final class ArcherSkills {
	/** How many arrows a triangle shot looses, and how often it looses them. */
	static final int TRIANGLE_SHOT_ARROWS = 3;
	static final double TRIANGLE_SHOT_CHANCE = 0.30;
	static final double TRIANGLE_SHOT_TOLERANCE = 0.04;
	/** How many rolls a case samples before it reads the rate; enough for a three-sigma reading. */
	static final int TRIANGLE_SHOT_SAMPLES = 4_000;
	/** How much harder an aimed bolt hits, and how much harder an explosive arrow does. */
	static final float AIMED_BOLT_DAMAGE = 4.0F;
	static final float EXPLOSIVE_ARROW_DAMAGE = 1.5F;
	/** How near a measured ratio has to sit to the multiplier it proves. */
	static final double SHOT_RATIO_TOLERANCE = 0.05;
	/** What an aimed bolt or an explosive arrow is worth on the next arrow, and how long the offer stands. */
	static final int SHOT_OFFER_TICKS = 600;
	/** How long a triangle shot stands ready: half an hour, the school's own buff length. */
	static final int TRIANGLE_SHOT_OFFER_TICKS = 36_000;
	/** Concentrate: half an hour of a quarter more Dexterity, and half again the crit chance and damage. */
	static final int CONCENTRATE_TICKS = 36_000;
	static final double CONCENTRATE_DEX_BONUS = 0.10;
	static final double CONCENTRATE_CRIT_FACTOR = 1.75;
	/** Spectral Quiver: half an hour of arrows the bow does not need, at three quarters the damage. */
	static final int SPECTRAL_QUIVER_TICKS = 36_000;
	static final double QUIVER_DAMAGE_FACTOR = 0.75;
	/** Wind Walk: half an hour at a quarter more walking speed. */
	static final int WIND_WALK_TICKS = 36_000;
	static final double WIND_WALK_SPEED = SpellStatus.WIND_WALK_SPEED;
	/** The step an arrow's arc is walked in, and how much speed it loses per block of that arc. */
	static final double GRAPPLE_ARC_STEP = 0.25;
	static final double GRAPPLE_GRAVITY_PER_BLOCK = 0.02;
	/** How far below one point of the arc its own ground may sit. */
	static final double GRAPPLE_DROP_RANGE = 8.0;
	/** How far a grapple has to carry the caster, and how near the arrow its arrival counts as on it. */
	static final double GRAPPLE_MIN_DISTANCE = 1.0;
	static final double GRAPPLE_ARRIVAL_SLACK = 0.75;
	/** The flat damage one arrow deals to a case's foe, so a ratio between two arrows proves itself. */
	static final float SHOT_PROBE_DAMAGE = 3.0F;
	/** How much more each later foe on an awakened Piercing Shot line takes. */
	static final float PIERCING_STEP = 0.25F;
	/** Arrow Rain's own reach around its point, and how many ticks apart its pulses fall. */
	static final double ARROW_RAIN_RADIUS = 4.0;
	static final int ARROW_RAIN_PULSES = 5;
	static final int AWAKENED_ARROW_RAIN_PULSES = 10;
	static final int RAIN_INTERVAL_TICKS = 10;
	/** Rapid Fire's window and how many ticks apart its shots leave. */
	static final int RAPID_FIRE_SHOTS = 12;
	static final int RAPID_FIRE_INTERVAL_TICKS = 5;
	static final double RAPID_FIRE_RANGE = 24.0;
	/** How long a mark lasts, and how far an awakened one jumps when its holder falls. */
	static final int MARK_TICKS = 600;
	static final double MARK_JUMP_RANGE = 16.0;
	/** The entity tag an awakened Hunter's Mark leaves on its foe, carrying the caster who may jump it. */
	static final String MARK_JUMP_TAG = Spellbooks.MOD_ID + ".mark_jumps.";
	/** Explosive Arrow's splash, and the three bomblets an awakened one scatters. */
	static final double EXPLOSIVE_SPLASH_RADIUS = 3.0;
	static final int EXPLOSIVE_BOMBLETS = 3;
	static final double BOMBLET_RADIUS = 2.0;
	static final int BOMBLET_FLIGHT_TICKS = 6;
	/** Eagle Eye: half an hour, and the share every archer skill hit gains while it holds. */
	static final int EAGLE_EYE_TICKS = 36_000;
	static final double EAGLE_EYE_PLAIN_FACTOR = 1.2;
	static final double EAGLE_EYE_AWAKENED_FACTOR = 1.4;
	static final double EAGLE_EYE_AWAKENED_AMPLIFIER = 1.0;

	/** The shot an arrow has just spent, held until the hit it landed finishes. */
	private static final Map<UUID, Shot> SPENT = new ConcurrentHashMap<>();
	/** The shot the caster's next arrow still owes, by caster. */
	private static final Map<UUID, Shot> SHOTS = new ConcurrentHashMap<>();
	/** The pulses still owed, by caster, oldest first. */
	private static final Map<UUID, List<Job>> JOBS = new ConcurrentHashMap<>();
	/** How many timed pulses each caster has run, so a case can count a sequence rather than sample it. */
	private static final Map<UUID, Integer> PULSES_RUN = new ConcurrentHashMap<>();
	/**
	 * The clock the jobs run on: one tick per server tick. The level's own clock barely advances in a game
	 * test level, so a job keyed on it would never come due; this one counts the ticks that really happen.
	 */
	private static volatile int clock;

	private ArcherSkills() {
	}

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ArcherSkills::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob fallen && markOwner(fallen) != null) {
				onMarkedDeath(fallen);
			}
		});
		// An aimed bolt and an explosive arrow are paid on the arrow that lands, not on the cast that armed it.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(ArcherSkills::afterArrowDamage);
	}

	/** No shot, job or mark outlives the server it was put on, and none keeps a due tick from another server. */
	static void clear() {
		SHOTS.clear();
		SPENT.clear();
		JOBS.clear();
		PULSES_RUN.clear();
		clock = 0;
	}

	/**
	 * Everything this caster owes, dropped without touching the server's clock. A test that only wants its
	 * own state gone calls this: resetting the clock would leave every other job in the process un-due.
	 */
	static void clearFor(final ServerPlayer caster) {
		SHOTS.remove(caster.getUUID());
		SPENT.remove(caster.getUUID());
		JOBS.remove(caster.getUUID());
		PULSES_RUN.remove(caster.getUUID());
	}

	/** The tick a job scheduled now would be due on, plus this many ticks. */
	static int dueIn(final int ticks) {
		return clock + ticks;
	}

	/** How many pulses this caster still owes; the test seam for a timed book. */
	static int pulsesOwed(final ServerPlayer caster) {
		List<Job> jobs = JOBS.get(caster.getUUID());
		return jobs == null ? 0 : jobs.size();
	}

	/** How many timed pulses this caster has actually run; the test seam for a sequence that really fired. */
	static int pulsesRun(final ServerPlayer caster) {
		return PULSES_RUN.getOrDefault(caster.getUUID(), 0);
	}

	/** True while this caster's next arrow still owes a book; the test seam for the next-shot books. */
	static boolean shotOwed(final ServerPlayer caster) {
		return SHOTS.containsKey(caster.getUUID());
	}

	// ---- the twelve books ----

	/**
	 * Grappling Hook: the arrow flies its arc to the first ground under the caster's look, and the caster
	 * arrives there at once. It is a step, not a dash: a wall stops the arc rather than the traveller.
	 */
	static boolean applyGrapple(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		Vec3 landing = arrowLanding(caster);
		if (landing == null) {
			return false;
		}
		spendOneArrow(caster);
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 from = caster.position();
		caster.teleportTo(landing.x, landing.y, landing.z);
		caster.resetFallDistance();
		line(level, caster.getEyePosition(), landing);
		level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.7F, 0.8F);
		Spellbooks.LOGGER.info("[SPELL] event=GRAPPLE caster={} from={} landing={} reach={}",
			caster.getUUID(), from, landing, from.distanceTo(landing));
		return true;
	}

	/**
	 * Where one arrow loosed along the caster's look comes down: the first ground the arc reaches that the
	 * caster can stand on. The arc drops by the square of the distance it has flown, as a slow arrow's own
	 * drop does, and a wall stops the arrow, and the hook with it, before any ground is reached.
	 */
	static @Nullable Vec3 arrowLanding(final ServerPlayer caster) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 eye = caster.getEyePosition();
		Vec3 direction = caster.getLookAngle();
		double lowest = caster.getY() - GRAPPLE_DROP_RANGE;
		for (double flown = GRAPPLE_ARC_STEP; flown <= RAPID_FIRE_RANGE; flown += GRAPPLE_ARC_STEP) {
			if (SkillTargeting.blockedReach(level, caster, eye, direction, flown) < flown) {
				break;
			}
			Vec3 spot = eye.add(direction.scale(flown));
			double arc = spot.y - GRAPPLE_GRAVITY_PER_BLOCK * flown * flown;
			if (arc < lowest) {
				break;
			}
			Vec3 landing = found(caster, spot.x, arc, spot.z);
			if (landing != null) {
				return landing;
			}
		}
		return null;
	}

	// The ground under one point of the arc: the first solid block top at or below it that the caster fits on.
	private static @Nullable Vec3 found(final ServerPlayer caster, final double x, final double from, final double z) {
		for (double y = from; y >= from - GRAPPLE_DROP_RANGE; y -= GRAPPLE_ARC_STEP) {
			Vec3 spot = new Vec3(x, y, z);
			if (standable(caster, spot)) {
				return spot;
			}
		}
		return null;
	}

	// True when this spot has solid ground under it and nothing solid in the space the caster would occupy.
	static boolean standable(final ServerPlayer caster, final Vec3 spot) {
		ServerLevel level = (ServerLevel) caster.level();
		BlockPos feet = BlockPos.containing(spot.x, spot.y - 0.05, spot.z);
		if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
			return false;
		}
		return SpellCasting.fits(caster, spot);
	}

	/** Concentrate: half an hour of a steadier aim, paid only while a bow or a crossbow is used. */
	static boolean applySnareTrap(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.CONCENTRATE, CONCENTRATE_TICKS, SpellNumbers.SNARE_TRAP.amplifier()), caster);
		caster.level().sendParticles(ParticleTypes.ITEM_COBWEB, caster.getX(), caster.getY(1.0), caster.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		Spellbooks.LOGGER.info("[SPELL] event=CONCENTRATE caster={} ticks={} dex={} crit={} bow={}",
			caster.getUUID(), CONCENTRATE_TICKS, CONCENTRATE_DEX_BONUS, CONCENTRATE_CRIT_FACTOR, hasBowOrCrossbow(caster));
		return true;
	}

	/** The share an archer skill hit gains from this caster's Concentrate; 1.0 without it or without a bow. */
	static double concentrateFactor(final ServerPlayer caster) {
		if (!caster.hasEffect(SpellStatus.CONCENTRATE) || !hasBowOrCrossbow(caster)) {
			return 1.0;
		}
		return (1.0 + CONCENTRATE_DEX_BONUS) * CONCENTRATE_CRIT_FACTOR;
	}

	/** Spectral Quiver: half an hour in which the bow needs no arrow, at three quarters the damage. */
	static boolean applyElementalQuiver(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.SPECTRAL_QUIVER, SPECTRAL_QUIVER_TICKS, SpellNumbers.ELEMENTAL_QUIVER.amplifier()), caster);
		caster.level().sendParticles(ParticleTypes.OMINOUS_SPAWNING, caster.getX(), caster.getY(1.0), caster.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		Spellbooks.LOGGER.info("[SPELL] event=SPECTRAL_QUIVER caster={} ticks={} factor={}",
			caster.getUUID(), SPECTRAL_QUIVER_TICKS, QUIVER_DAMAGE_FACTOR);
		return true;
	}

	/** The share an archer skill hit keeps while the caster's quiver is spectral; 1.0 without it. */
	static double quiverDamageFactor(final ServerPlayer caster) {
		return caster.hasEffect(SpellStatus.SPECTRAL_QUIVER) ? QUIVER_DAMAGE_FACTOR : 1.0;
	}

	/** Wind Walk: half an hour at a quarter more walking speed, the modifier itself on the effect. */
	static boolean applyDisengage(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.WIND_WALK, WIND_WALK_TICKS, modifiers.awakened() ? 1 : 0), caster);
		caster.level().sendParticles(ParticleTypes.CRIMSON_SPORE, caster.getX(), caster.getY(0.2), caster.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
		Spellbooks.LOGGER.info("[SPELL] event=WIND_WALK caster={} ticks={} speed={}",
			caster.getUUID(), WIND_WALK_TICKS, WIND_WALK_SPEED);
		return true;
	}

	/**
	 * Aimed Bolt: the next arrow draws half again as slowly, hits four times as hard, and stops its target
	 * where it stands for two seconds. It waits half a minute for that arrow, then spends itself.
	 */
	static boolean applyAimedBolt(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		queue(caster, ShotKind.AIMED_BOLT, modifiers);
		Spellbooks.LOGGER.info("[SPELL] event=AIMED_BOLT caster={} offer={} factor={}",
			caster.getUUID(), SHOT_OFFER_TICKS, AIMED_BOLT_DAMAGE);
		return true;
	}

	/** Triangle Shot: half an hour in which a shot may loose three arrows instead of one. */
	static boolean applyTriangleShot(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.TRIANGLE_SHOT, TRIANGLE_SHOT_OFFER_TICKS, SpellNumbers.TRIANGLE_SHOT.amplifier()), caster);
		caster.level().sendParticles(ParticleTypes.INFESTED, caster.getX(), caster.getY(1.0), caster.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		Spellbooks.LOGGER.info("[SPELL] event=TRIANGLE_SHOT caster={} ticks={} chance={} arrows={}",
			caster.getUUID(), TRIANGLE_SHOT_OFFER_TICKS, TRIANGLE_SHOT_CHANCE, TRIANGLE_SHOT_ARROWS);
		return true;
	}

	/** Explosive Arrow: the next arrow bursts on what it strikes, with no block broken. */
	static boolean applyExplosiveArrow(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		queue(caster, ShotKind.EXPLOSIVE_ARROW, modifiers);
		Spellbooks.LOGGER.info("[SPELL] event=EXPLOSIVE_ARROW caster={} offer={} splash={}",
			caster.getUUID(), SHOT_OFFER_TICKS, EXPLOSIVE_SPLASH_RADIUS);
		return true;
	}

	// The next arrow owes this book, which replaces whatever the last armed book offered.
	private static void queue(final ServerPlayer caster, final ShotKind kind, final SpellModifiers.CastModifiers modifiers) {
		SHOTS.put(caster.getUUID(), new Shot(kind, modifiers, dueIn(SHOT_OFFER_TICKS)));
	}

	/**
	 * The number of arrows one shot looses under the caster's triangle shot: a barrage on its own roll, and
	 * one arrow otherwise. The roll is the caller's, so a seeded source drives it from a case.
	 */
	public static int barrage(final @Nullable ServerPlayer caster, final RandomSource random) {
		if (caster == null || !caster.hasEffect(SpellStatus.TRIANGLE_SHOT)) {
			return 1;
		}
		return random.nextDouble() < TRIANGLE_SHOT_CHANCE ? TRIANGLE_SHOT_ARROWS : 1;
	}

	/** How much longer an aimed bolt stretches the draw of one bow or crossbow shot. */
	static final double AIMED_BOLT_DRAW = 1.5;

	/** The use duration one aimed bolt shot takes, or the game's own when no aimed bolt is owed. */
	public static int aimedBoltDraw(final ServerPlayer caster, final ItemStack weapon, final int duration) {
		Shot shot = SHOTS.get(caster.getUUID());
		if (shot == null || shot.kind() != ShotKind.AIMED_BOLT || !ranged(weapon)) {
			return duration;
		}
		return (int) Math.round(duration * AIMED_BOLT_DRAW);
	}

	/**
	 * The share an arrow of this hit keeps. An aimed bolt or an explosive arrow waits for exactly one arrow:
	 * the first one that lands spends it, whatever the shot hit.
	 */
	static float projectileDamage(final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source) {
		if (!(source.getDirectEntity() instanceof Projectile projectile)
			|| !(projectile.getOwner() instanceof ServerPlayer shooter)) {
			return 1.0F;
		}
		Shot shot = SHOTS.remove(shooter.getUUID());
		if (shot == null) {
			return 1.0F;
		}
		// The hit that this arrow deals runs after the damage is scaled, so the spent shot waits for it.
		SPENT.put(shooter.getUUID(), shot);
		return switch (shot.kind()) {
			case AIMED_BOLT -> AIMED_BOLT_DAMAGE;
			case EXPLOSIVE_ARROW -> EXPLOSIVE_ARROW_DAMAGE;
		};
	}

	// The hit of a queued explosive arrow: the blast of the old cast, on the foe the arrow reached.
	private static void afterArrowDamage(
		final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source,
		final float baseDamage, final float damage, final boolean blocked
	) {
		if (damage <= 0.0F || !(source.getDirectEntity() instanceof Projectile projectile)
			|| !(projectile.getOwner() instanceof ServerPlayer shooter) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Shot shot = SPENT.remove(shooter.getUUID());
		if (shot == null) {
			return;
		}
		switch (shot.kind()) {
			case AIMED_BOLT -> {
				victim.addEffect(new MobEffectInstance(
					MobEffects.SLOWNESS, SpellNumbers.AIMED_BOLT.extraTicks(shot.modifiers()),
					SpellNumbers.AIMED_BOLT.extraAmplifier()
				), shooter);
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.getX(), victim.getY(1.0), victim.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
				Spellbooks.LOGGER.info("[SPELL] event=AIMED_BOLT_HIT shooter={} victim={} damage={}",
					shooter.getUUID(), victim.getUUID(), damage);
			}
			case EXPLOSIVE_ARROW -> {
				blast(shooter, victim.position(), shot.modifiers());
				for (Mob near : foesAroundPoint(shooter, victim.position(), EXPLOSIVE_SPLASH_RADIUS)) {
					if (near != victim) {
						hit(shooter, near, splash(SpellNumbers.EXPLOSIVE_ARROW, shot.modifiers()), shot.modifiers());
					}
				}
				if (shot.modifiers().awakened()) {
					for (int bomblet = 0; bomblet < EXPLOSIVE_BOMBLETS; bomblet++) {
						Vec3 at = victim.position().add(bombletOffset(bomblet));
						owe(shooter, new Job(at, dueIn(BOMBLET_FLIGHT_TICKS * (bomblet + 1)), JobKind.BOMBLET, shot.modifiers()));
					}
				}
				Spellbooks.LOGGER.info("[SPELL] event=EXPLOSIVE_ARROW_HIT shooter={} victim={} damage={}",
					shooter.getUUID(), victim.getUUID(), damage);
			}
		}
	}

	/** Piercing Shot: every foe on a long line, in order. Each later foe takes more when it is awakened. */
	static boolean applyPiercingShot(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.PIERCING_SHOT;
		Vec3 direction = caster.getLookAngle();
		List<Mob> struck = SkillTargeting.rayFoes(caster, direction, numbers.radius(modifiers));
		trail(caster, direction, numbers.radius(modifiers), struck.isEmpty() ? null : struck.get(struck.size() - 1));
		float factor = 1.0F;
		for (Mob foe : struck) {
			hit(caster, foe, numbers.damage() * factor, modifiers);
			if (modifiers.awakened()) {
				factor += PIERCING_STEP;
			}
		}
		return !struck.isEmpty();
	}

	/** Arrow Rain: pulses over the point the caster looks at. An awakened rain falls twice as long. */
	static boolean applyArrowRain(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster) || point == null) {
			return false;
		}
		int pulses = modifiers.awakened() ? AWAKENED_ARROW_RAIN_PULSES : ARROW_RAIN_PULSES;

		rain(caster, point, modifiers);
		for (int pulse = 1; pulse < pulses; pulse++) {
			owe(caster, new Job(point, dueIn(pulse * RAIN_INTERVAL_TICKS), JobKind.RAIN, modifiers));
		}
		return true;
	}

	/** Rapid Fire: a shot every five ticks at the foe the caster looks at, retargeted each shot. */
	static boolean applyRapidFire(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster) || target == null) {
			return false;
		}
		shoot(caster, modifiers);
		for (int shot = 1; shot < RAPID_FIRE_SHOTS; shot++) {
			owe(caster, new Job(null, dueIn(shot * RAPID_FIRE_INTERVAL_TICKS), JobKind.RAPID_FIRE, modifiers));
		}
		return true;
	}

	/** Hunter's Mark: Marked for half a minute. An awakened mark jumps when its holder falls. */
	static boolean applyHuntersMark(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster) || target == null) {
			return false;
		}
		clearMarkJump(target);
		SpellStatusApi.mark(target, SpellNumbers.HUNTERS_MARK.durationTicks(modifiers), caster);
		if (modifiers.awakened()) {
			target.addTag(markJumpTag(caster));
		}
		return true;
	}

	/** The tag an awakened mark wears, so a death knows which caster may jump it. */
	static String markJumpTag(final ServerPlayer caster) {
		return MARK_JUMP_TAG + caster.getUUID();
	}

	/** The caster an awakened mark belongs to, or null when this foe wears no awakened mark. */
	static @Nullable UUID markOwner(final Mob marked) {
		for (String tag : marked.entityTags()) {
			if (tag.startsWith(MARK_JUMP_TAG)) {
				try {
					return UUID.fromString(tag.substring(MARK_JUMP_TAG.length()));
				} catch (IllegalArgumentException malformed) {
					return null;
				}
			}
		}
		return null;
	}

	private static void clearMarkJump(final Mob marked) {
		for (String tag : List.copyOf(marked.entityTags())) {
			if (tag.startsWith(MARK_JUMP_TAG)) {
				marked.removeTag(tag);
			}
		}
	}

	/** The death of a marked foe: an awakened mark moves to the nearest foe that is still standing. */
	static boolean onMarkedDeath(final Mob fallen) {
		UUID owner = markOwner(fallen);
		if (owner == null || !(fallen.level() instanceof ServerLevel level)) {
			return false;
		}
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(owner);
		clearMarkJump(fallen);
		if (caster == null) {
			return false;
		}
		Mob next = nearestFoe(fallen, caster, MARK_JUMP_RANGE, fallen);
		if (next == null) {
			return false;
		}
		SpellStatusApi.mark(next, MARK_TICKS, caster);
		next.addTag(markJumpTag(caster));
		return true;
	}

	// The three bomblets land on a triangle inside the blast, so each one reaches its own ground.
	static Vec3 bombletOffset(final int index) {
		double angle = index * (Math.PI * 2.0 / EXPLOSIVE_BOMBLETS);
		return new Vec3(Math.cos(angle) * (EXPLOSIVE_SPLASH_RADIUS - 1.0), 0.0, Math.sin(angle) * (EXPLOSIVE_SPLASH_RADIUS - 1.0));
	}

	/** Eagle Eye: half an hour of the archer's own eye, and the amplifier says whether it is awakened. */
	static boolean applyEagleEye(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!armed(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(
			SpellStatus.EAGLE_EYE, SpellNumbers.EAGLE_EYE.durationTicks(modifiers),
			modifiers.awakened() ? (int) EAGLE_EYE_AWAKENED_AMPLIFIER : SpellNumbers.EAGLE_EYE.amplifier()
		), caster);
		return true;
	}

	/** The share an archer skill hit gains from this caster's Eagle Eye; 1.0 without it. */
	static double eagleEyeFactor(final ServerPlayer caster) {
		MobEffectInstance eye = caster.getEffect(SpellStatus.EAGLE_EYE);
		if (eye == null) {
			return 1.0;
		}
		return eye.getAmplifier() >= (int) EAGLE_EYE_AWAKENED_AMPLIFIER ? EAGLE_EYE_AWAKENED_FACTOR : EAGLE_EYE_PLAIN_FACTOR;
	}

	// ---- the school's own gate: every archer book needs a bow or a crossbow, and an arrow or a bolt ----

	/** True when the caster carries a bow or a crossbow, wherever the mod reads a weapon from. */
	static boolean hasBowOrCrossbow(final ServerPlayer caster) {
		return ranged(RpgBridge.skillWeapon(caster)) || ranged(caster.getMainHandItem()) || ranged(caster.getOffhandItem());
	}

	// The first stack of arrows or bolts in the pack, in inventory order, or null when the pack has none.
	// A stack is read slot by slot because a mock body's own `hasAnyMatching` reports nothing at all.
	private static @Nullable ItemStack firstAmmo(final ServerPlayer caster) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (ammo(stack)) {
				return stack;
			}
		}
		return null;
	}

	// An arrow, a spectral arrow or a tipped arrow; a firework counts only for a crossbow, so it is not here.
	private static boolean ammo(final ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.ARROWS) || stack.is(Items.ARROW)
			|| stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW));
	}

	/** True when the caster has an arrow or a bolt to fire, or a quiver that needs neither. */
	static boolean hasAmmo(final ServerPlayer caster) {
		return firstAmmo(caster) != null || hasSpectralQuiver(caster);
	}

	/** The school's gate: a ranged weapon and something to loose, or the cast never starts. */
	static boolean armed(final ServerPlayer caster) {
		return hasBowOrCrossbow(caster) && hasAmmo(caster);
	}

	/** True when this weapon fires with an empty quiver under the caster's Spectral Quiver. */
	public static boolean ammoFree(final ServerPlayer caster, final ItemStack weapon) {
		return ranged(weapon) && hasSpectralQuiver(caster);
	}

	private static boolean hasSpectralQuiver(final ServerPlayer caster) {
		return caster.hasEffect(SpellStatus.SPECTRAL_QUIVER);
	}

	private static boolean ranged(final ItemStack stack) {
		return stack.getItem() instanceof ProjectileWeaponItem;
	}

	// One arrow of the first stack in the pack pays for a grappling hook.
	private static void spendOneArrow(final ServerPlayer caster) {
		ItemStack arrows = firstAmmo(caster);
		if (arrows != null) {
			arrows.shrink(1);
		}
	}

	// A line of arrow motes from the launch to the landing, so the hook's flight can be seen.
	private static void line(final ServerLevel level, final Vec3 from, final Vec3 to) {
		double reach = from.distanceTo(to);
		if (reach < 1.0E-6) {
			return;
		}
		Vec3 step = to.subtract(from).scale(1.0 / reach);
		for (double flown = 0.5; flown <= reach; flown += 0.5) {
			Vec3 at = from.add(step.scale(flown));
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ---- the shared ray helpers ----

	/** A hit: the school's own dice once, then the caster's own buffs, and nothing else. */
	static void hit(final ServerPlayer caster, final Mob target, final float flat, final SpellModifiers.CastModifiers modifiers) {
		float amount = (float) (RpgBridge.skillDamage(caster, School.ARCHER, flat, modifiers.powerMultiplier())
			* eagleEyeFactor(caster) * concentrateFactor(caster) * quiverDamageFactor(caster));
		if (target.damageCooldownTime > 0) {
			target.damageCooldownTime = 0;
		}
		target.hurtServer((ServerLevel) caster.level(), RpgBridge.skillSource(caster), amount);
	}

	/** The arrow trail of a skill shot: motes along the path, and the arrow's own sound where it stops. */
	static void trail(final ServerPlayer caster, final Vec3 direction, final double range, final @Nullable Mob hitFoe) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 start = caster.getEyePosition();
		double reach = hitFoe == null
			? SkillTargeting.blockedReach(level, caster, start, direction, range)
			: Math.min(range, start.distanceTo(hitFoe.getBoundingBox().getCenter()));
		Vec3 end = start.add(direction.scale(reach));
		for (double step = 0.5; step <= reach; step += 0.5) {
			Vec3 at = start.add(direction.scale(step));
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, end.x, end.y, end.z,
			hitFoe == null ? SoundEvents.ARROW_SHOOT : SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	/** The nearest foe to a point, within this reach, or null. The nearest-to-the-point body wins. */
	static @Nullable Mob nearestFoe(final Entity from, final ServerPlayer caster, final double range) {
		return nearestFoe(from, caster, range, from);
	}

	/**
	 * The nearest foe to a point, within this reach, leaving out one entity. A mark that jumped onto its own
	 * fallen holder would never move at all, so the caller names the body to leave out.
	 */
	static @Nullable Mob nearestFoe(final Entity at, final ServerPlayer caster, final double range, final Entity leavingOut) {
		Mob nearest = null;
		double best = Double.MAX_VALUE;
		for (Mob foe : foesAroundPoint(caster, at.position(), range)) {
			if (foe == leavingOut) {
				continue;
			}
			double distance = foe.position().distanceToSqr(at.position());
			if (distance < best) {
				best = distance;
				nearest = foe;
			}
		}
		return nearest;
	}

	/**
	 * Every foe within this reach of a point in the caster's own level. The squared check on the body's own
	 * position is the rule, and the box is fitted to it, so a body exactly on the reach counts as inside.
	 */
	static List<Mob> foesAroundPoint(final ServerPlayer caster, final Vec3 at, final double range) {
		List<Mob> foes = new ArrayList<>(caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(at, at).inflate(range),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(at) <= range * range
		));
		foes.sort(Comparator.comparingDouble(foe -> foe.position().distanceToSqr(at)));
		return foes;
	}

	// ---- the timed jobs ----

	private enum JobKind {
		RAIN,
		RAPID_FIRE,
		BOMBLET
	}

	/** The book the caster's next arrow owes. */
	private enum ShotKind {
		AIMED_BOLT,
		EXPLOSIVE_ARROW
	}

	private record Shot(ShotKind kind, SpellModifiers.CastModifiers modifiers, int expiresAt) {
	}

	private record Job(@Nullable Vec3 at, int dueTick, JobKind kind, SpellModifiers.CastModifiers modifiers) {
	}

	private static void owe(final ServerPlayer caster, final Job job) {
		JOBS.compute(caster.getUUID(), (id, existing) -> {
			List<Job> jobs = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
			jobs.add(job);
			jobs.sort(Comparator.comparingInt(Job::dueTick));
			return jobs;
		});

	}

	// A job or a queued shot whose caster is gone is dropped, so nothing outlives the player who owed it.
	private static void tick(final MinecraftServer server) {
		int now = ++clock;
		SHOTS.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
		for (Map.Entry<UUID, List<Job>> entry : List.copyOf(JOBS.entrySet())) {
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			if (caster == null || !caster.isAlive()) {
				JOBS.remove(entry.getKey());
				continue;
			}
			List<Job> left = new ArrayList<>();
			for (Job job : entry.getValue()) {
				if (now < job.dueTick()) {
					left.add(job);
					continue;
				}
				run(caster, job);
			}
			if (left.isEmpty()) {
				JOBS.remove(entry.getKey());
			} else {
				JOBS.put(entry.getKey(), left);
			}
		}
	}

	private static void run(final ServerPlayer caster, final Job job) {
		PULSES_RUN.merge(caster.getUUID(), 1, Integer::sum);

		switch (job.kind()) {
			case RAIN -> rain(caster, job.at(), job.modifiers());
			case RAPID_FIRE -> shoot(caster, job.modifiers());
			case BOMBLET -> {
				if (job.at() != null) {
					blast(caster, job.at(), job.modifiers());
					for (Mob foe : foesAroundPoint(caster, job.at(), BOMBLET_RADIUS)) {
						hit(caster, foe, splash(SpellNumbers.EXPLOSIVE_ARROW, job.modifiers()), job.modifiers());
					}
				}
			}
		}
	}

	// One Arrow Rain pulse: a handful of falling arrows over the point, and a hit on everything there.
	private static void rain(final ServerPlayer caster, final @Nullable Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (point == null) {
			return;
		}
		ServerLevel level = (ServerLevel) caster.level();
		double radius = SpellNumbers.ARROW_RAIN.radius(modifiers);
		for (int arrow = 0; arrow < 8; arrow++) {
			double angle = arrow * (Math.PI * 2.0 / 8.0);
			double x = point.x + Math.cos(angle) * radius * 0.7;
			double z = point.z + Math.sin(angle) * radius * 0.7;
			level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, x, point.y + 4.0, z, 2, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, point.x, point.y, point.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6F, 0.9F);
		for (Mob foe : foesAroundPoint(caster, point, radius)) {
			hit(caster, foe, SpellNumbers.ARROW_RAIN.damage(), modifiers);
		}
	}

	// One Rapid Fire shot: the foe the caster looks at now, or nothing when the look has moved off.
	private static void shoot(final ServerPlayer caster, final SpellModifiers.CastModifiers modifiers) {
		Vec3 direction = caster.getLookAngle();
		Mob foe = SkillTargeting.rayFoe(caster, direction, RAPID_FIRE_RANGE);
		if (foe == null) {
			return;
		}
		trail(caster, direction, RAPID_FIRE_RANGE, foe);
		hit(caster, foe, SpellNumbers.RAPID_FIRE.damage(), modifiers);
		if (modifiers.awakened()) {
			blast(caster, foe.position(), modifiers);
			for (Mob near : foesAroundPoint(caster, foe.position(), EXPLOSIVE_SPLASH_RADIUS)) {
				if (near != foe) {
					hit(caster, near, splash(SpellNumbers.EXPLOSIVE_ARROW, modifiers), modifiers);
				}
			}
		}
	}

	/**
	 * What an Explosive Arrow deals to a foe inside its blast. The numbers record carries it in its extra
	 * timing, the one slot left after damage and radius, so the tooltip and the blast read one declaration.
	 */
	static float splash(final SpellNumbers numbers, final SpellModifiers.CastModifiers modifiers) {
		return numbers.extraTicks(modifiers);
	}

	// A blast that breaks nothing: the mod never calls level.explode, so no block is ever removed.
	private static void blast(final ServerPlayer caster, final Vec3 at, final SpellModifiers.CastModifiers modifiers) {
		ServerLevel level = (ServerLevel) caster.level();
		double radius = SpellNumbers.EXPLOSIVE_ARROW.radius(modifiers);
		for (double step = 0.0; step <= radius; step += 0.5) {
			for (int point = 0; point < 8; point++) {
				double angle = point * (Math.PI * 2.0 / 8.0);
				level.sendParticles(ParticleTypes.EXPLOSION,
					at.x + Math.cos(angle) * step, at.y + 0.4, at.z + Math.sin(angle) * step, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.3F);
	}
}
