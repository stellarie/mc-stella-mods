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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The archer books: instant hit-scan rays with an arrow trail, and one real shower of arrow entities. A ray
 * deals its damage through {@link RpgBridge#skillDamage} with {@link School#ARCHER} exactly once, so no shot
 * takes the ranged dice a second time. Two books instead arm the next real arrow, and
 * {@link SpellStatus#onHurt} hands that arrow its blast through {@link #projectileDamage}. Arrow Shower looses
 * its arrows as entities, so a block in their way stops them and every arrow bonus applies as it does to a shot.
 * Every book needs a bow or a crossbow, and Arrow Shower also spends eight arrows. A quiver in the off hand lends
 * its arrows to the skills that name them: each spent arrow adds its kind damage and effect to the hits it feeds. A book imbued on a
 * bow casts from an arrow hit, needs neither, and spends nothing.
 */
public final class ArcherSkills {
	/** How many arrows one Arrow Crafting cast makes, and how many an awakened one makes. */
	static final int ARROW_CRAFTING_ARROWS = 32;
	static final int AWAKENED_ARROW_CRAFTING_ARROWS = 48;
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
	/** Concentrate: half an hour of a steadier aim, paid through the RPG mod's own stat and critical seams. */
	static final int CONCENTRATE_TICKS = 36_000;
	/** Spectral Quiver: half an hour of arrows the bow does not need, at three quarters the damage. */
	static final int SPECTRAL_QUIVER_TICKS = 36_000;
	static final double QUIVER_DAMAGE_FACTOR = 0.75;
	/** What a +15 Elemental Quiver keeps: its Elemental Edge signature. */
	static final double EDGED_QUIVER_DAMAGE_FACTOR = 0.90;
	/** What a +15 Triangle Shot rolls: its Barrage signature. */
	static final double BARRAGE_CHANCE = 0.50;
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
	/** Every archer skill reaches this far, in blocks, and no further. */
	static final int SKILL_RANGE_BLOCKS = 24;
	/** Arrow Shower: how many arrows it looses, over how many ticks, and what the cast costs in arrows. */
	static final int ARROW_SHOWER_ARROWS = 32;
	static final int AWAKENED_ARROW_SHOWER_ARROWS = 64;
	static final int ARROW_SHOWER_TICKS = 80;
	static final int ARROW_SHOWER_COST = 8;
	/** Each shower arrow is worth this share of a full-draw arrow from the same weapon. */
	static final float ARROW_SHOWER_DAMAGE_SHARE = 0.25F;
	/** The disc around the aimed point that the arrows come down on, and the speed they are loosed at. */
	static final double ARROW_SHOWER_RADIUS = 6.0;
	static final double ARROW_SHOWER_SPEED = 2.0;
	/** The speed of an arrow from a fully drawn bow, and from a loaded crossbow: what a shower arrow is worth. */
	private static final double FULL_DRAW_SPEED = 3.0;
	private static final double CROSSBOW_ARROW_SPEED = 3.15;
	/** Marks an arrow the shower loosed, so the damage seam and the friendly-fire rule can find it. */
	static final String SHOWER_TAG = Spellbooks.MOD_ID + ".arrow_shower";
	/** How far above the eye an arrow leaves, so it clears the caster's own head. */
	private static final double SHOWER_LAUNCH_LIFT = 0.4;
	/** The flattest and the steepest lob the shower solves for, in degrees. */
	private static final double SHOWER_MIN_ELEVATION = 45.0;
	private static final double SHOWER_MAX_ELEVATION = 89.5;
	/** An arrow's own drag and gravity per tick, used to find where a lob comes down. */
	private static final double ARROW_DRAG = 0.99;
	private static final double ARROW_GRAVITY = 0.05;
	private static final int LOB_STEPS = 400;
	/** A shower arrow is taken out of the world once it is this old: the flight, then a few seconds in the ground. */
	private static final int SHOWER_ARROW_LIFE_TICKS = 160;
	/** The idle falcon: its reach, how long the owner must rest, its hits and the gap between attacks. */
	static final double PARROT_RANGE = 8.0;
	static final int PARROT_IDLE_TICKS = 40;
	static final int PARROT_INTERVAL_TICKS = 30;
	static final int PARROT_HITS = 3;
	static final int PARROT_GLOW_TICKS = 60;
	static final double PARROT_XP_SHARE = 0.35;
	/** Double Strafe's hits on its target, and the reach of its shot. */
	static final int DOUBLE_STRAFE_HITS = 2;
	static final double RAPID_FIRE_RANGE = SKILL_RANGE_BLOCKS;
	/** Vultures Eye: half an hour in which a far hit gains this much per block beyond the near range, up to a cap. */
	static final int VULTURES_EYE_TICKS = 36_000;
	static final double VULTURE_NEAR_BLOCKS = 8.0;
	static final double VULTURE_PER_BLOCK = 0.02;
	static final double VULTURE_CAP = 0.40;
	static final double VULTURE_AWAKENED_CAP = 0.60;
	/** Blitz Beat: half an hour of a chance to call the falcon, which strikes this often, this many ticks apart. */
	static final int BLITZ_BEAT_TICKS = 36_000;
	static final double BLITZ_BEAT_CHANCE = 0.25;
	static final double BLITZ_BEAT_BOW_CHANCE = 0.35;
	/** Each point of LUK adds this much to the falcon's chance: 5 LUK for 1 percent. */
	static final double BLITZ_BEAT_CHANCE_PER_LUK = 0.002;
	private static volatile UnbrokenVow.StatSource blitzStats = UnbrokenVow::rpgStat;
	static final int BLITZ_BEAT_STRIKES = 3;
	static final int BLITZ_BEAT_BOW_STRIKES = 6;
	static final int BLITZ_BEAT_INTERVAL_TICKS = 6;
	/** An awakened falcon strikes this many times as often. */
	static final int AWAKENED_BLITZ_BEAT_FACTOR = 2;
	/** The critical chance Sharp Shooting adds to its own hits, and True Sight adds while it lasts. */
	static final double SHARP_SHOOTING_CRIT_CHANCE = 0.30;
	static final double TRUE_SIGHT_CRIT_CHANCE = 0.15;
	static final double TRUE_SIGHT_AWAKENED_CRIT_CHANCE = 0.25;
	/** Ankle Snare: how near a foe has to come to spring a trap, and how many traps one archer keeps. */
	static final double ANKLE_SNARE_RADIUS = 1.5;
	static final int ANKLE_SNARE_LIFE_TICKS = 600;
	static final int ANKLE_SNARE_ROOT_TICKS = 60;
	static final int ANKLE_SNARE_MAX_TRAPS = 3;
	/** Arrow Repel: the push on its target, and the distance the shipped text promises. */
	static final double ARROW_REPEL_STRENGTH = 2.4;
	static final double ARROW_REPEL_LIFT = 0.3;
	static final double ARROW_REPEL_MIN_BLOCKS = 3.0;
	/** The books that need ammunition. Only Arrow Shower does; the rest need a bow, and a quiver adds its arrows. */
	static final java.util.Set<String> AMMO_BOOKS = java.util.Set.of("arrow_rain");
	/** Explosive Arrow's splash, and the three bomblets an awakened one scatters. */
	static final double EXPLOSIVE_SPLASH_RADIUS = 3.0;
	static final int EXPLOSIVE_BOMBLETS = 3;
	static final double BOMBLET_RADIUS = 2.0;
	static final int BOMBLET_FLIGHT_TICKS = 6;
	/** True Sight: half an hour, and the share every archer skill hit gains while it holds. */
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
	/** The ankle snares each caster has laid, oldest first. */
	private static final Map<UUID, List<Snare>> SNARES = new ConcurrentHashMap<>();
	/** The Arrow Showers still loosing arrows, by caster. */
	private static final Map<UUID, Shower> SHOWERS = new ConcurrentHashMap<>();
	/** The arrows each caster's last shower loosed, by identity. */
	private static final Map<UUID, List<UUID>> SHOWER_ARROWS = new ConcurrentHashMap<>();
	/** The server tick a player last struck a blow of their own, which holds the idle falcon back. */
	private static final Map<UUID, Integer> LAST_BLOW = new ConcurrentHashMap<>();
	/** The server tick the idle falcon may next attack a player's foe. */
	private static final Map<UUID, Integer> PARROT_READY = new ConcurrentHashMap<>();
	/** Set while the falcon's own strike lands, so it never counts as a blow of the player. */
	private static final ThreadLocal<Boolean> FALCON_STRIKING = new ThreadLocal<>();
	/** The idle falcon strike now landing, so the foe it kills pays the player its share of experience. */
	private static final ThreadLocal<ParrotKill> PARROT_KILL = new ThreadLocal<>();
	/** Set while Sharp Shooting rolls its hits, so the critical seam reads its raised chance. */
	private static final ThreadLocal<Boolean> SHARP_SHOOTING = new ThreadLocal<>();
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
		// An aimed bolt and an explosive arrow are paid on the arrow that lands, not on the cast that armed it.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(ArcherSkills::afterArrowDamage);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(ArcherSkills::afterOwnBlow);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArcherSkills::allowDamage);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register(ArcherSkills::allowDeath);
	}

	/** No shot, job or mark outlives the server it was put on, and none keeps a due tick from another server. */
	static void clear() {
		SHOTS.clear();
		SPENT.clear();
		JOBS.clear();
		SNARES.clear();
		SHOWERS.clear();
		SHOWER_ARROWS.clear();
		LAST_BLOW.clear();
		PARROT_READY.clear();
		PULSES_RUN.clear();
		ArcherVisuals.clear();
		FalconGuard.clear();
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
		SNARES.remove(caster.getUUID());
		SHOWERS.remove(caster.getUUID());
		SHOWER_ARROWS.remove(caster.getUUID());
		LAST_BLOW.remove(caster.getUUID());
		PARROT_READY.remove(caster.getUUID());
		PULSES_RUN.remove(caster.getUUID());
		ArcherVisuals.forget(caster);
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

	/** How many unsprung ankle snares this caster has laid; the test seam for the trap. */
	static int ankleSnares(final ServerPlayer caster) {
		List<Snare> snares = SNARES.get(caster.getUUID());
		return snares == null ? 0 : snares.size();
	}

	/** How many timed pulses this caster has actually run; the test seam for a sequence that really fired. */
	static int pulsesRun(final ServerPlayer caster) {
		return PULSES_RUN.getOrDefault(caster.getUUID(), 0);
	}

	/** The test seam for the falcon roll: null rolls on the caster own source. */
	private static volatile @Nullable RandomSource blitzRandom;

	static void setBlitzRandom(final @Nullable RandomSource random) {
		blitzRandom = random;
	}

	/** The arrows the caster's last Arrow Shower loosed, by identity; the test seam for the shower. */
	static List<UUID> showerArrows(final ServerPlayer caster) {
		List<UUID> loosed = SHOWER_ARROWS.get(caster.getUUID());
		return loosed == null ? List.of() : List.copyOf(loosed);
	}

	/** The test seam for the shower's landing spots: null rolls on the caster own source. */
	private static volatile @Nullable RandomSource showerRandom;

	static void setShowerRandom(final @Nullable RandomSource random) {
		showerRandom = random;
	}

	/** True while this caster's next arrow still owes a book; the test seam for the next-shot books. */
	static boolean shotOwed(final ServerPlayer caster) {
		return SHOTS.containsKey(caster.getUUID());
	}

	/** Arms the shot this caster owes as an awakened one: the +15 Cluster Charge. False when no shot is owed. */
	static boolean upgradeShot(final ServerPlayer caster) {
		return SHOTS.computeIfPresent(caster.getUUID(),
			(id, shot) -> new Shot(shot.kind(), shot.modifiers().withAwakened(true), shot.expiresAt())) != null;
	}

	/** True while the arrow this caster owes was armed awakened; the test seam for the next-shot books. */
	static boolean owedShotAwakened(final ServerPlayer caster) {
		Shot shot = SHOTS.get(caster.getUUID());
		return shot != null && shot.modifiers().awakened();
	}

	// ---- the fourteen books ----

	/** Arrow Crafting: one material becomes arrows of its kind. It needs no bow. */
	static boolean applyArrowCrafting(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		ItemStack material = ItemStack.EMPTY;
		ArrowItems.Craft craft = null;
		for (ArrowItems.Craft candidate : ArrowItems.CRAFTS) {
			if (caster.getOffhandItem().is(candidate.material())) {
				material = caster.getOffhandItem();
				craft = candidate;
				break;
			}
		}
		for (ArrowItems.Craft candidate : ArrowItems.CRAFTS) {
			if (craft != null) {
				break;
			}
			for (int slot = 0; slot < Inventory.INVENTORY_SIZE && craft == null; slot++) {
				ItemStack stack = caster.getInventory().getItem(slot);
				if (stack.is(candidate.material())) {
					material = stack;
					craft = candidate;
				}
			}
		}
		if (craft == null) {
			return false;
		}
		material.shrink(1);
		int count = modifiers.awakened() ? AWAKENED_ARROW_CRAFTING_ARROWS : ARROW_CRAFTING_ARROWS;
		ItemStack arrows = new ItemStack(craft.arrow(), count);
		caster.getInventory().add(arrows);
		if (!arrows.isEmpty()) {
			caster.spawnAtLocation((ServerLevel) caster.level(), arrows);
		}
		Spellbooks.LOGGER.info("[SPELL] event=ARROW_CRAFTING caster={} arrow={} count={}",
			caster.getUUID(), BuiltInRegistries.ITEM.getKey(craft.arrow()), count);
		return true;
	}

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
		ItemStack arrow = takeSkillArrow(caster);
		ArcherVisuals.note(caster, arrow);
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 from = caster.position();
		Vec3 eye = caster.getEyePosition();
		caster.teleportTo(landing.x, landing.y, landing.z);
		caster.resetFallDistance();
		ArcherVisuals.trail(level, caster, book("multishot"), eye, landing, ArcherVisuals.colorOf(arrow));
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
		if (!bowReady(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.CONCENTRATE, CONCENTRATE_TICKS, SpellNumbers.SNARE_TRAP.amplifier()), caster);
		caster.level().sendParticles(ParticleTypes.ITEM_COBWEB, caster.getX(), caster.getY(1.0), caster.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		Spellbooks.LOGGER.info("[SPELL] event=CONCENTRATE caster={} ticks={} dex={} half={} bow={}",
			caster.getUUID(), CONCENTRATE_TICKS, ConcentrateStats.CONCENTRATE_DEXTERITY,
			ConcentrateStats.CONCENTRATE_CRITICAL_HALF, hasBowOrCrossbow(caster));
		return true;
	}

	/** Spectral Quiver: half an hour in which the bow needs no arrow, at three quarters the damage. */
	static boolean applyElementalQuiver(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster)) {
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
		MobEffectInstance quiver = caster.getEffect(SpellStatus.SPECTRAL_QUIVER);
		return quiver == null ? 1.0 : quiver.getAmplifier() >= 1 ? EDGED_QUIVER_DAMAGE_FACTOR : QUIVER_DAMAGE_FACTOR;
	}

	/** Wind Walk: half an hour at a quarter more walking speed, the modifier itself on the effect. */
	static boolean applyDisengage(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster)) {
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
		LivingEntity struck = CastContext.imbuedVictim();
		if (struck != null) {
			detonate(caster, struck, modifiers, true);
			return true;
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
		MobEffectInstance triangle = caster == null ? null : caster.getEffect(SpellStatus.TRIANGLE_SHOT);
		if (triangle == null) {
			return 1;
		}
		return random.nextDouble() < (triangle.getAmplifier() >= 1 ? BARRAGE_CHANCE : TRIANGLE_SHOT_CHANCE) ? TRIANGLE_SHOT_ARROWS : 1;
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
		float refined = (float) BookRefine.statFactor(shot.modifiers().refineLevel());
		return switch (shot.kind()) {
			case AIMED_BOLT -> AIMED_BOLT_DAMAGE * refined;
			case EXPLOSIVE_ARROW -> EXPLOSIVE_ARROW_DAMAGE * refined;
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
				detonate(shooter, victim, shot.modifiers(), false);
				Spellbooks.LOGGER.info("[SPELL] event=EXPLOSIVE_ARROW_HIT shooter={} victim={} damage={}",
					shooter.getUUID(), victim.getUUID(), damage);
			}
		}
	}

	/**
	 * Sharp Shooting: every foe on a long line, in order, with a raised critical chance. Each later foe takes
	 * more when it is awakened. An imbued shot flies from the caster toward the foe the arrow struck.
	 */
	static boolean applyPiercingShot(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster)) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.PIERCING_SHOT;
		LivingEntity struck = CastContext.imbuedVictim();
		Vec3 direction = struck == null ? caster.getLookAngle()
			: struck.getBoundingBox().getCenter().subtract(caster.getEyePosition()).normalize();
		List<Mob> crossed = SkillTargeting.rayFoes(caster, direction, numbers.radius(modifiers));
		ItemStack arrow = crossed.isEmpty() ? ItemStack.EMPTY : takeSkillArrow(caster);
		ArcherVisuals.note(caster, arrow);
		trail(caster, "piercing_shot", direction, numbers.radius(modifiers),
			crossed.isEmpty() ? null : crossed.get(crossed.size() - 1), ArcherVisuals.colorOf(arrow));
		float factor = 1.0F;
		SHARP_SHOOTING.set(Boolean.TRUE);
		try {
			for (Mob foe : crossed) {
				hit(caster, foe, numbers.damage() * factor, modifiers, arrow);
				if (modifiers.awakened()) {
					factor += PIERCING_STEP;
				}
			}
		} finally {
			SHARP_SHOOTING.remove();
		}
		return !crossed.isEmpty();
	}

	/**
	 * Arrow Shower: the caster lobs real arrows into the sky, one after another, and they come down over a disc
	 * around the ground it looks at. The cast costs {@link #ARROW_SHOWER_COST} arrows, from the quiver first and
	 * then the pack, and each spent arrow lends its kind to its share of the shower. An imbued cast and a Spectral
	 * Quiver pay nothing. A caster who cannot pay casts nothing.
	 */
	static boolean applyArrowRain(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster) || point == null) {
			return false;
		}
		List<ItemStack> ammo = showerAmmo(caster, modifiers);
		if (ammo == null) {
			return false;
		}
		int count = modifiers.awakened() ? AWAKENED_ARROW_SHOWER_ARROWS : ARROW_SHOWER_ARROWS;
		RandomSource random = showerRandom != null ? showerRandom : caster.getRandom();
		Shower shower = new Shower(showerSpots(point, count, random), ammo, weaponOf(caster), clock);
		SHOWERS.put(caster.getUUID(), shower);
		SHOWER_ARROWS.put(caster.getUUID(), new ArrayList<>());
		ArcherVisuals.note(caster, ammo.get(0));
		loosenDue(caster, shower, clock);
		Spellbooks.LOGGER.info("[SPELL] event=ARROW_SHOWER caster={} arrows={} ticks={} cost={} at={} awakened={}",
			caster.getUUID(), count, ARROW_SHOWER_TICKS, ammo.size(), point, modifiers.awakened());
		return true;
	}

	/**
	 * The arrows a cast spends, one single-arrow stack each, or null when the caster has too few. Nothing is
	 * taken unless all of it can be: the check comes first.
	 */
	private static @Nullable List<ItemStack> showerAmmo(final ServerPlayer caster, final SpellModifiers.CastModifiers modifiers) {
		if (imbuedCast() || hasSpectralQuiver(caster)) {
			return List.of(new ItemStack(Items.ARROW));
		}
		int cost = BookRefine.arrowCost(ARROW_SHOWER_COST, modifiers.refineLevel());
		if (arrowsOwned(caster) < cost) {
			return null;
		}
		List<ItemStack> spent = new ArrayList<>();
		for (int arrow = 0; arrow < cost; arrow++) {
			ItemStack taken = Quiver.takeArrow(caster);
			spent.add(taken.isEmpty() ? takePackArrow(caster) : taken);
		}
		return spent;
	}

	// Every arrow a shower may draw on: the quiver's, then the pack's.
	private static int arrowsOwned(final ServerPlayer caster) {
		int owned = 0;
		for (ItemStack stack : Quiver.contents(Quiver.held(caster))) {
			owned += stack.is(ItemTags.ARROWS) ? stack.getCount() : 0;
		}
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			owned += ammo(stack) ? stack.getCount() : 0;
		}
		return owned;
	}

	private static ItemStack takePackArrow(final ServerPlayer caster) {
		ItemStack arrows = firstAmmo(caster);
		return arrows == null ? ItemStack.EMPTY : arrows.split(1);
	}

	/** The bow or crossbow a shower is loosed from: the weapon slot, else a hand; empty when there is none. */
	private static ItemStack weaponOf(final ServerPlayer caster) {
		ItemStack slot = RpgBridge.skillWeapon(caster);
		if (ranged(slot)) {
			return slot;
		}
		return ranged(caster.getMainHandItem()) ? caster.getMainHandItem()
			: ranged(caster.getOffhandItem()) ? caster.getOffhandItem() : ItemStack.EMPTY;
	}

	/**
	 * The ground spots a shower of this many arrows aims at, in loosing order. They fill the disc evenly, so a
	 * wide area is covered whatever the roll, and the roll turns and shuffles them.
	 */
	static List<Vec3> showerSpots(final Vec3 centre, final int count, final RandomSource random) {
		double turn = random.nextDouble() * Math.PI * 2.0;
		double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		List<Vec3> spots = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			double radius = ARROW_SHOWER_RADIUS * Math.sqrt((index + 0.5) / count);
			double angle = turn + index * golden;
			spots.add(new Vec3(centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius));
		}
		for (int index = count - 1; index > 0; index--) {
			java.util.Collections.swap(spots, index, random.nextInt(index + 1));
		}
		return spots;
	}

	/**
	 * The velocity that lobs an arrow from one point down onto another. The elevation is the steep one of the two
	 * that reach, found by walking the arrow's own flight, so the arrow rises into the sky and falls back onto the
	 * spot. A spot too far for the flattest lob gets that lob and falls short.
	 */
	static Vec3 showerVelocity(final Vec3 from, final Vec3 to) {
		double across = Math.hypot(to.x - from.x, to.z - from.z);
		double rise = to.y - from.y;
		double flat = Math.toRadians(SHOWER_MIN_ELEVATION);
		double steep = Math.toRadians(SHOWER_MAX_ELEVATION);
		double elevation;
		if (lobRange(flat, rise) <= across) {
			elevation = flat;
		} else if (lobRange(steep, rise) >= across) {
			elevation = steep;
		} else {
			double low = flat;
			double high = steep;
			for (int step = 0; step < 48; step++) {
				double middle = (low + high) / 2.0;
				if (lobRange(middle, rise) > across) {
					low = middle;
				} else {
					high = middle;
				}
			}
			elevation = (low + high) / 2.0;
		}
		double dx = across > 1.0E-6 ? (to.x - from.x) / across : 0.0;
		double dz = across > 1.0E-6 ? (to.z - from.z) / across : 1.0;
		double flatSpeed = ARROW_SHOWER_SPEED * Math.cos(elevation);
		return new Vec3(dx * flatSpeed, ARROW_SHOWER_SPEED * Math.sin(elevation), dz * flatSpeed);
	}

	// How far across an arrow loosed at this elevation has flown when it comes back down through the rise.
	private static double lobRange(final double elevation, final double rise) {
		double across = 0.0;
		double height = 0.0;
		double flat = ARROW_SHOWER_SPEED * Math.cos(elevation);
		double lift = ARROW_SHOWER_SPEED * Math.sin(elevation);
		for (int tick = 0; tick < LOB_STEPS; tick++) {
			double lastAcross = across;
			double lastHeight = height;
			across += flat;
			height += lift;
			flat *= ARROW_DRAG;
			lift = lift * ARROW_DRAG - ARROW_GRAVITY;
			if (lift < 0.0 && height <= rise) {
				double share = (lastHeight - rise) / (lastHeight - height);
				return lastAcross + (across - lastAcross) * share;
			}
		}
		return across;
	}

	/**
	 * Looses one shower arrow of this ammunition from a point at a velocity. It is a real arrow of the caster, fired
	 * from the caster's weapon so that bow enchantments and the arrow's own effects apply, and it cannot be picked up.
	 */
	static AbstractArrow spawnShowerArrow(
		final ServerPlayer caster, final ItemStack ammo, final ItemStack weapon, final Vec3 from, final Vec3 velocity
	) {
		ServerLevel level = (ServerLevel) caster.level();
		ArrowItem item = ammo.getItem() instanceof ArrowItem arrowItem ? arrowItem : (ArrowItem) Items.ARROW;
		AbstractArrow arrow = item.createArrow(level, ammo.copyWithCount(1), caster, weapon.isEmpty() ? null : weapon);
		arrow.setPos(from.x, from.y, from.z);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.addTag(SHOWER_TAG);
		Projectile.spawnProjectileUsingShoot(arrow, level, weapon, velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
		return arrow;
	}

	// Looses every arrow whose time has come: arrow k leaves at tick k times the shower's length over its size.
	private static void loosenDue(final ServerPlayer caster, final Shower shower, final int now) {
		int size = shower.spots.size();
		while (shower.loosed < size && now - shower.startedAt >= (long) shower.loosed * ARROW_SHOWER_TICKS / size) {
			Vec3 spot = shower.spots.get(shower.loosed);
			Vec3 from = caster.getEyePosition().add(0.0, SHOWER_LAUNCH_LIFT, 0.0);
			ItemStack ammo = shower.ammo.get(shower.loosed % shower.ammo.size());
			AbstractArrow arrow = spawnShowerArrow(caster, ammo, shower.weapon, from, showerVelocity(from, spot));
			SHOWER_ARROWS.computeIfAbsent(caster.getUUID(), id -> new ArrayList<>()).add(arrow.getUUID());
			((ServerLevel) caster.level()).playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F,
				0.8F + caster.getRandom().nextFloat() * 0.5F);
			shower.loosed++;
		}
	}

	// A shower ends when its last arrow is loosed or its caster is gone; its arrows are taken out of the world when old.
	private static void tickShowers(final MinecraftServer server, final int now) {
		for (Map.Entry<UUID, Shower> entry : List.copyOf(SHOWERS.entrySet())) {
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			Shower shower = entry.getValue();
			if (caster == null || !caster.isAlive() || shower.loosed >= shower.spots.size()) {
				SHOWERS.remove(entry.getKey());
			} else {
				loosenDue(caster, shower, now);
			}
		}
		if (now % 10 != 0) {
			return;
		}
		for (Map.Entry<UUID, List<UUID>> entry : SHOWER_ARROWS.entrySet()) {
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null) {
				continue;
			}
			for (UUID id : List.copyOf(entry.getValue())) {
				Entity arrow = ((ServerLevel) owner.level()).getEntity(id);
				if (arrow != null && arrow.tickCount >= SHOWER_ARROW_LIFE_TICKS) {
					arrow.discard();
				}
			}
		}
	}

	/**
	 * The speed an arrow's damage is made from. A shower arrow lands at the end of a lob, much slower than a shot
	 * from a drawn weapon, so its damage reads as a full draw of the weapon it was loosed from; the share is taken
	 * after that, which keeps every arrow bonus and effect as a full-draw arrow would have them.
	 */
	public static double damageSpeed(final AbstractArrow arrow, final double speed) {
		if (!arrow.entityTags().contains(SHOWER_TAG)) {
			return speed;
		}
		ItemStack weapon = arrow.getWeaponItem();
		return weapon != null && weapon.getItem() instanceof CrossbowItem ? CROSSBOW_ARROW_SPEED : FULL_DRAW_SPEED;
	}

	/** The share of damage a hit keeps for the arrow that dealt it: a quarter for a shower arrow, all otherwise. */
	static float showerFactor(final net.minecraft.world.damagesource.DamageSource source) {
		return source.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(SHOWER_TAG)
			? ARROW_SHOWER_DAMAGE_SHARE : 1.0F;
	}

	/** Double Strafe: two archer hits on the target at once, with no arrow spent. */
	static boolean applyDoubleStrafe(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster) || target == null) {
			return false;
		}
		ItemStack[] arrows = new ItemStack[DOUBLE_STRAFE_HITS];
		for (int shot = 0; shot < DOUBLE_STRAFE_HITS; shot++) {
			arrows[shot] = takeSkillArrow(caster);
		}
		ArcherVisuals.note(caster, arrows[0]);
		trail(caster, "rapid_fire", towards(caster, target), RAPID_FIRE_RANGE, target, ArcherVisuals.colorOf(arrows[0]));
		for (int shot = 0; shot < DOUBLE_STRAFE_HITS; shot++) {
			hit(caster, target, SpellNumbers.RAPID_FIRE.damage(), modifiers, arrows[shot]);
		}
		return true;
	}

	/** Vultures Eye: half an hour in which a far hit hits harder. It needs a bow. */
	static boolean applyVulturesEye(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster)) {
			return false;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.VULTURES_EYE, VULTURES_EYE_TICKS, modifiers.awakened() ? 1 : 0), caster);
		caster.level().sendParticles(ParticleTypes.END_ROD, caster.getX(), caster.getEyeY(), caster.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
		Spellbooks.LOGGER.info("[SPELL] event=VULTURES_EYE caster={} ticks={} near={} perBlock={} cap={}",
			caster.getUUID(), VULTURES_EYE_TICKS, VULTURE_NEAR_BLOCKS, VULTURE_PER_BLOCK,
			modifiers.awakened() ? VULTURE_AWAKENED_CAP : VULTURE_CAP);
		return true;
	}

	/** The extra share a far hit of this shooter gains: 0 without the buff or inside the near range. */
	static double vultureBonus(final ServerPlayer shooter, final Entity victim) {
		MobEffectInstance eye = shooter.getEffect(SpellStatus.VULTURES_EYE);
		if (eye == null) {
			return 0.0;
		}
		double beyond = shooter.position().distanceTo(victim.position()) - VULTURE_NEAR_BLOCKS;
		if (beyond <= 0.0) {
			return 0.0;
		}
		return Math.min(eye.getAmplifier() >= 1 ? VULTURE_AWAKENED_CAP : VULTURE_CAP, VULTURE_PER_BLOCK * beyond);
	}

	/** The share a hit of this shooter keeps for Vultures Eye: only an own arrow or a skill hit gains by distance. */
	static float vultureFactor(final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer shooter)) {
			return 1.0F;
		}
		boolean arrow = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow shot && shot.getOwner() == shooter;
		if (!arrow && !RpgBridge.isSkillSource(source)) {
			return 1.0F;
		}
		return 1.0F + (float) vultureBonus(shooter, victim);
	}

	/** Blitz Beat: half an hour in which a hit may call a spectral falcon. It needs no bow and no target. */
	static boolean applyBlitzBeat(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		caster.addEffect(new MobEffectInstance(SpellStatus.BLITZ_BEAT, BLITZ_BEAT_TICKS, modifiers.awakened() ? 1 : 0), caster);
		LAST_BLOW.put(caster.getUUID(), clock);
		PARROT_READY.remove(caster.getUUID());
		Falcons.ensure(caster);
		caster.level().sendParticles(ParticleTypes.DUST_PLUME, caster.getX(), caster.getY(1.0), caster.getZ(), 14, 0.4, 0.4, 0.4, 0.02);
		Spellbooks.LOGGER.info("[SPELL] event=BLITZ_BEAT caster={} ticks={} chance={} bowChance={} awakened={}",
			caster.getUUID(), BLITZ_BEAT_TICKS, BLITZ_BEAT_CHANCE, BLITZ_BEAT_BOW_CHANCE, modifiers.awakened());
		return true;
	}

	/** The falcon's chance on a hit: the base for the hand, plus 1 percent per 5 LUK, at most 1. */
	static double blitzChance(final LivingEntity caster, final boolean bow) {
		double luk = Math.max(0.0, blitzStats.stat(caster, "luk"));
		return Math.min(1.0, (bow ? BLITZ_BEAT_BOW_CHANCE : BLITZ_BEAT_CHANCE) + luk * BLITZ_BEAT_CHANCE_PER_LUK);
	}

	/** Pins the LUK reader for a harness. A null source returns to the RPG mod's own stats. */
	static void setBlitzStats(final UnbrokenVow.StatSource source) {
		blitzStats = source == null ? UnbrokenVow::rpgStat : source;
	}

	/** How many falcon strikes this caster hit calls: 0 for none. The roll is the caller, so a seeded source drives it. */
	static int blitzStrikes(final ServerPlayer caster, final RandomSource random) {
		MobEffectInstance buff = caster.getEffect(SpellStatus.BLITZ_BEAT);
		if (buff == null) {
			return 0;
		}
		boolean bow = bowInHand(caster);
		if (random.nextDouble() >= blitzChance(caster, bow)) {
			return 0;
		}
		int strikes = bow ? BLITZ_BEAT_BOW_STRIKES : BLITZ_BEAT_STRIKES;
		return buff.getAmplifier() >= 1 ? strikes * AWAKENED_BLITZ_BEAT_FACTOR : strikes;
	}

	// Only a hand counts here: a bow in the RPG weapon slot or the pack does not make the caster an archer at the blow.
	private static boolean bowInHand(final ServerPlayer caster) {
		return ranged(caster.getMainHandItem()) || ranged(caster.getOffhandItem());
	}

	// A blow of the caster own, melee or arrow: the hit that may call the falcon. Skill damage and an imbued cast never do.
	// A mob that holds the buff for good takes the same road through MobBlitz.
	private static void afterOwnBlow(
		final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source,
		final float baseDamage, final float damage, final boolean blocked
	) {
		if (blocked || damage <= 0.0F || CastContext.imbuedVictim() != null || RpgBridge.isSkillSource(source)) {
			return;
		}
		if (source.getEntity() instanceof Mob attacker) {
			MobBlitz.afterBlow(attacker, victim, source);
			return;
		}
		if (!(victim instanceof Mob foe) || !foe.isAlive()) {
			return;
		}
		ServerPlayer owner = null;
		if (source.getDirectEntity() instanceof ServerPlayer player && source.getEntity() == player && SpellStatus.isMelee(source)) {
			owner = player;
		} else if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
			&& arrow.getOwner() instanceof ServerPlayer shooter) {
			owner = shooter;
		}
		if (owner == null) {
			return;
		}
		RandomSource override = blitzRandom;
		int strikes = blitzStrikes(owner, override != null ? override : owner.getRandom());
		if (strikes <= 0) {
			return;
		}
		boolean ranged = bowInHand(owner);
		float amount = strikeDamage(owner, ranged);
		SpellModifiers.CastModifiers plain = SpellModifiers.resolve(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		for (int strike = 0; strike < strikes; strike++) {
			owe(owner, new Job(null, foe.getUUID(), dueIn(1 + strike * BLITZ_BEAT_INTERVAL_TICKS), JobKind.BLITZ, plain, amount));
		}
		((ServerLevel) owner.level()).playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.6F);
		Spellbooks.LOGGER.info("[SPELL] event=BLITZ_BEAT_CALL caster={} foe={} strikes={} ranged={}",
			owner.getUUID(), foe.getUUID(), strikes, ranged);
	}

	/**
	 * Ankle Snare: a trap at the target point. The first foe to come near springs it and is rooted. An
	 * imbued cast lays it under the foe the arrow struck, so it springs on that foe at once.
	 */
	static boolean applyAnkleSnare(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster) || point == null) {
			return false;
		}
		int life = SpellNumbers.ANKLE_SNARE.extraTicks(modifiers);
		Snare snare = new Snare(point, caster.level().dimension(), dueIn(life), modifiers);
		SNARES.compute(caster.getUUID(), (id, existing) -> {
			List<Snare> snares = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
			snares.add(snare);
			while (snares.size() > ANKLE_SNARE_MAX_TRAPS) {
				snares.remove(0);
			}
			return snares;
		});
		caster.level().sendParticles(ParticleTypes.ITEM_COBWEB, point.x, point.y + 0.1, point.z, 10, 0.4, 0.05, 0.4, 0.01);
		if (springSnare(caster.level(), caster.getUUID(), caster, snare)) {
			dropSnare(caster.getUUID(), snare);
		}
		Spellbooks.LOGGER.info("[SPELL] event=ANKLE_SNARE caster={} at={} life={}", caster.getUUID(), point, life);
		return true;
	}

	/** Arrow Repel: one heavy shot that drives the target several blocks back, away from the caster. */
	static boolean applyArrowRepel(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster) || target == null) {
			return false;
		}
		Vec3 away = target.position().subtract(caster.position());
		ItemStack arrow = takeSkillArrow(caster);
		ArcherVisuals.note(caster, arrow);
		trail(caster, "arrow_repel", towards(caster, target), RAPID_FIRE_RANGE, target, ArcherVisuals.colorOf(arrow));
		hit(caster, target, SpellNumbers.ARROW_REPEL.damage(), modifiers, arrow);
		shove(target, away, ARROW_REPEL_STRENGTH, ARROW_REPEL_LIFT);
		Spellbooks.LOGGER.info("[SPELL] event=ARROW_REPEL caster={} target={} strength={}",
			caster.getUUID(), target.getUUID(), ARROW_REPEL_STRENGTH);
		return true;
	}

	// The direction from the caster's eyes to the middle of a foe, so a shot aims at what it strikes.
	private static Vec3 towards(final ServerPlayer caster, final Mob foe) {
		return foe.getBoundingBox().getCenter().subtract(caster.getEyePosition()).normalize();
	}

	// A flat push away from a point; a foe with full knockback resistance stays where it stands.
	private static void shove(final Mob foe, final Vec3 away, final double strength, final double lift) {
		double give = 1.0 - foe.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		if (give <= 0.0 || flat.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 push = flat.normalize().scale(strength * give);
		foe.push(push.x, lift, push.z);
		foe.needsSync = true;
	}

	/** What one falcon strike deals: the caster floor ATK, else the book own number when the RPG mod gives none. */
	static float strikeDamage(final ServerPlayer caster, final boolean ranged) {
		double floor = RpgBridge.floorAtk(caster, ranged);
		float base = Double.isFinite(floor) && floor >= 0.0 ? RpgBridge.vanilla((float) floor) : SpellNumbers.HUNTERS_MARK.damage();
		return base * (float) BookRefine.statFactor(BookRefine.castLevel(caster.getUUID(), "hunters_mark"));
	}

	// One falcon strike: a coloured burst and layered sounds on the foe, then the amount fixed when it was called.
	// A fixed number never rolls a crit. The foe glows for a few seconds. A foe an idle strike kills drops no orbs
	// and pays the owner its share of the experience at once.
	private static void strike(final ServerPlayer caster, final Mob foe, final float amount, final JobKind kind) {
		ServerLevel level = (ServerLevel) caster.level();
		Identifier spell = book("hunters_mark");
		Vec3 at = foe.getBoundingBox().getCenter();
		SpellVisuals.particle(level, caster, spell, ParticleTypes.CRIT, at, 8, 0.3, 0.1);
		SpellVisuals.particle(level, caster, spell, ParticleTypes.ENCHANTED_HIT, at, 6, 0.3, 0.05);
		ArcherVisuals.ring(level, caster, spell, at, 0.6, ArcherVisuals.FALCON_COLOR, 10);
		SpellVisuals.sound(level, caster, spell, SoundEvents.ARROW_HIT, at, 0.6F, 1.6F);
		SpellVisuals.sound(level, caster, spell, SoundEvents.PHANTOM_BITE, at, 0.4F, 1.8F);
		if (foe.damageCooldownTime > 0) {
			foe.damageCooldownTime = 0;
		}
		foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, PARROT_GLOW_TICKS), caster);
		if (kind == JobKind.GUARD) {
			Falcons.swoopGuard(caster, foe);
		} else {
			Falcons.swoop(caster, foe);
		}
		double xpShare = switch (kind) {
			case PARROT -> PARROT_XP_SHARE;
			case GUARD -> FalconGuard.XP_SHARE;
			default -> 0.0;
		};
		ParrotKill kill = xpShare > 0.0 ? new ParrotKill(foe, caster) : null;
		FALCON_STRIKING.set(Boolean.TRUE);
		PARROT_KILL.set(kill);
		try {
			foe.hurtServer(level, RpgBridge.skillSource(caster), amount);
		} finally {
			FALCON_STRIKING.remove();
			PARROT_KILL.remove();
		}
		if (kill != null && kill.reward >= 0) {
			int share = (int) Math.round(kill.reward * xpShare);
			caster.giveExperiencePoints(share);
			Spellbooks.LOGGER.info("[SPELL] event={} caster={} foe={} reward={} share={}",
				kind == JobKind.GUARD ? "GUARD_KILL" : "PARROT_KILL", caster.getUUID(), foe.getUUID(), kill.reward, share);
		}
	}

	// A blow of a player stops the idle falcon for a while. The falcon's own strikes never count as one.
	private static boolean allowDamage(
		final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source, final float amount
	) {
		if (source.getEntity() instanceof ServerPlayer player && victim != player && FALCON_STRIKING.get() == null) {
			LAST_BLOW.put(player.getUUID(), clock);
		}
		// A shower arrow bounces off its own caster and off anyone the skills help.
		if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(SHOWER_TAG)
			&& arrow.getOwner() instanceof LivingEntity owner) {
			return victim != owner && !SkillTargeting.isAlly(owner, victim);
		}
		return true;
	}

	// The foe an idle falcon strike is about to kill: take its experience now, while it still has its gear, and
	// spare the world its orbs.
	private static boolean allowDeath(
		final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source, final float amount
	) {
		ParrotKill kill = PARROT_KILL.get();
		if (kill != null && victim == kill.foe && victim.level() instanceof ServerLevel level) {
			kill.reward = kill.foe.getExperienceReward(level, kill.owner);
			kill.foe.skipDropExperience();
		}
		return true;
	}

	// The Falcon Guard: every two seconds an archer with the innate has a chance to send the black falcon at the nearest
	// foe within reach for three hits. No rest is needed, and Blitz Beat does not share its timers.
	private static void tickGuards(final MinecraftServer server, final int now) {
		if (now % FalconGuard.CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			guardCheck(player);
		}
	}

	/** What one Falcon Guard hit deals: the average of the lowest and highest ranged roll, else the book number. */
	static float guardDamage(final ServerPlayer caster) {
		double average = RpgBridge.averageAtk(caster, true);
		return Double.isFinite(average) && average >= 0.0 ? RpgBridge.vanilla((float) average) : SpellNumbers.HUNTERS_MARK.damage();
	}

	/** One two-second check of the Falcon Guard: true when it rolled a strike and queued the hits. */
	static boolean guardCheck(final ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !FalconGuard.holds(player) || !FalconGuard.roll(player)) {
			return false;
		}
		Mob foe = nearestFoe(player, player, FalconGuard.RANGE);
		if (foe == null) {
			return false;
		}
		float amount = guardDamage(player);
		SpellModifiers.CastModifiers plain = SpellModifiers.resolve(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		for (int hit = 0; hit < FalconGuard.HITS; hit++) {
			owe(player, new Job(null, foe.getUUID(), dueIn(1 + hit * BLITZ_BEAT_INTERVAL_TICKS), JobKind.GUARD, plain, amount));
		}
		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.2F);
		Spellbooks.LOGGER.info("[SPELL] event=FALCON_GUARD_ATTACK caster={} foe={} hits={} amount={}", player.getUUID(), foe.getUUID(), FalconGuard.HITS, amount);
		return true;
	}

	// The idle falcon: a player with Blitz Beat who has not struck a blow for a while sends the falcon at the nearest
	// foe within reach, three hits, and then it rests before it picks a foe again.
	private static void tickParrots(final MinecraftServer server, final int now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !player.hasEffect(SpellStatus.BLITZ_BEAT)) {
				continue;
			}
			UUID id = player.getUUID();
			int rested = now - LAST_BLOW.computeIfAbsent(id, key -> now);
			if (rested < PARROT_IDLE_TICKS || now < PARROT_READY.getOrDefault(id, 0)) {
				continue;
			}
			Mob foe = nearestFoe(player, player, PARROT_RANGE);
			if (foe == null) {
				continue;
			}
			PARROT_READY.put(id, now + PARROT_INTERVAL_TICKS);
			float amount = strikeDamage(player, bowInHand(player));
			SpellModifiers.CastModifiers plain = SpellModifiers.resolve(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
			for (int hit = 0; hit < PARROT_HITS; hit++) {
				owe(player, new Job(null, foe.getUUID(), now + 1 + hit * BLITZ_BEAT_INTERVAL_TICKS, JobKind.PARROT, plain, amount));
			}
			((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.6F);
			Spellbooks.LOGGER.info("[SPELL] event=PARROT_ATTACK caster={} foe={} hits={} rested={}", id, foe.getUUID(), PARROT_HITS, rested);
		}
	}

	// A snare springs on the nearest foe within its reach; true when it did.
	private static boolean springSnare(
		final ServerLevel level, final UUID owner, final @Nullable ServerPlayer caster, final Snare snare
	) {
		Mob nearest = null;
		double best = Double.MAX_VALUE;
		for (Mob foe : level.getEntitiesOfClass(
			Mob.class, new AABB(snare.at(), snare.at()).inflate(ANKLE_SNARE_RADIUS),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(snare.at()) <= ANKLE_SNARE_RADIUS * ANKLE_SNARE_RADIUS
		)) {
			double distance = foe.position().distanceToSqr(snare.at());
			if (distance < best) {
				best = distance;
				nearest = foe;
			}
		}
		if (nearest == null) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.ANKLE_SNARE;
		int base = numbers.durationTicks(snare.modifiers()) * (snare.modifiers().awakened() ? 2 : 1);
		int root = numbers.controlTicks(nearest, base);
		nearest.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, root, numbers.amplifier()), caster);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, nearest.getX(), nearest.getY(0.2), nearest.getZ(), 16, 0.3, 0.1, 0.3, 0.02);
		level.playSound(null, snare.at().x, snare.at().y, snare.at().z, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.8F, 0.8F);
		if (caster != null) {
			ArcherVisuals.impact(level, caster, book("ankle_snare"), snare.at().add(0.0, 0.2, 0.0), ArcherVisuals.PLAIN_COLOR);
		}
		Spellbooks.LOGGER.info("[SPELL] event=ANKLE_SNARE_SPRUNG owner={} foe={} root={}", owner, nearest.getUUID(), root);
		return true;
	}

	private static void dropSnare(final UUID owner, final Snare snare) {
		SNARES.computeIfPresent(owner, (id, existing) -> {
			List<Snare> snares = new ArrayList<>(existing);
			snares.remove(snare);
			return snares.isEmpty() ? null : snares;
		});
	}

	/**
	 * The critical chance the archer books add on top of the caster's own: Sharp Shooting while its hits roll,
	 * and True Sight while it lasts with a bow in hand. The RPG mod's critical seam reads this through
	 * {@link ConcentrateStats}; without that mod the number is still readable here.
	 */
	public static double criticalChanceBonus(final LivingEntity caster) {
		double bonus = SHARP_SHOOTING.get() != null ? SHARP_SHOOTING_CRIT_CHANCE : 0.0;
		if (caster instanceof ServerPlayer player && hasBowOrCrossbow(player)) {
			MobEffectInstance sight = player.getEffect(SpellStatus.EAGLE_EYE);
			if (sight != null) {
				bonus += sight.getAmplifier() >= (int) EAGLE_EYE_AWAKENED_AMPLIFIER
					? TRUE_SIGHT_AWAKENED_CRIT_CHANCE : TRUE_SIGHT_CRIT_CHANCE;
			}
		}
		return bonus;
	}

	// The blast of an Explosive Arrow on the foe it struck. An imbued cast also hits that foe itself.
	private static void detonate(
		final ServerPlayer shooter, final LivingEntity victim, final SpellModifiers.CastModifiers modifiers, final boolean hitVictim
	) {
		blast(shooter, victim.position(), modifiers);
		if (hitVictim && victim instanceof Mob mob) {
			hit(shooter, mob, SpellNumbers.EXPLOSIVE_ARROW.damage(), modifiers);
		}
		for (Mob near : foesAroundPoint(shooter, victim.position(), EXPLOSIVE_SPLASH_RADIUS)) {
			if (near != victim) {
				hit(shooter, near, splash(SpellNumbers.EXPLOSIVE_ARROW, modifiers), modifiers);
			}
		}
		if (modifiers.awakened()) {
			for (int bomblet = 0; bomblet < EXPLOSIVE_BOMBLETS; bomblet++) {
				Vec3 at = victim.position().add(bombletOffset(bomblet));
				owe(shooter, new Job(at, null, dueIn(BOMBLET_FLIGHT_TICKS * (bomblet + 1)), JobKind.BOMBLET, modifiers));
			}
		}
	}

	// The three bomblets land on a triangle inside the blast, so each one reaches its own ground.
	static Vec3 bombletOffset(final int index) {
		double angle = index * (Math.PI * 2.0 / EXPLOSIVE_BOMBLETS);
		return new Vec3(Math.cos(angle) * (EXPLOSIVE_SPLASH_RADIUS - 1.0), 0.0, Math.sin(angle) * (EXPLOSIVE_SPLASH_RADIUS - 1.0));
	}

	/** True Sight: half an hour of the archer's own eye, and the amplifier says whether it is awakened. */
	static boolean applyEagleEye(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (!bowReady(caster)) {
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

	// ---- the school's own gate: every archer book needs a bow or a crossbow; four also need an arrow or a bolt ----

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

	/** True while an arrow hit casts an imbued bow's book; that cast needs neither a bow in hand nor ammunition. */
	static boolean imbuedCast() {
		return CastContext.imbuedVictim() != null;
	}

	/** The school's gate for every book: a bow or a crossbow in hand, or the cast never starts. */
	static boolean bowReady(final ServerPlayer caster) {
		return imbuedCast() || hasBowOrCrossbow(caster);
	}

	/** The gate of the books that once needed ammunition; a bow or a crossbow is all they need now. */
	static boolean armed(final ServerPlayer caster) {
		return bowReady(caster);
	}

	/** True when this weapon fires with an empty quiver under the caster's Spectral Quiver. */
	public static boolean ammoFree(final Player caster, final ItemStack weapon) {
		return ranged(weapon) && hasSpectralQuiver(caster);
	}

	private static boolean hasSpectralQuiver(final Player caster) {
		return caster.hasEffect(SpellStatus.SPECTRAL_QUIVER);
	}

	private static boolean ranged(final ItemStack stack) {
		return stack.getItem() instanceof ProjectileWeaponItem;
	}

	// The arrow a skill spends from the off-hand quiver; an imbued cast spends none, and no quiver gives none.
	private static ItemStack takeSkillArrow(final ServerPlayer caster) {
		return imbuedCast() ? ItemStack.EMPTY : Quiver.takeArrow(caster);
	}

	// ---- the shared ray helpers ----

	/** A hit: the school's own dice once, then the caster's own buffs, and nothing else. */
	static void hit(final ServerPlayer caster, final Mob target, final float flat, final SpellModifiers.CastModifiers modifiers) {
		hit(caster, target, flat, modifiers, ItemStack.EMPTY);
	}

	/** The same hit, carrying the damage and the effect of the arrow it spent from the quiver. */
	static void hit(
		final ServerPlayer caster, final Mob target, final float flat, final SpellModifiers.CastModifiers modifiers, final ItemStack arrow
	) {
		float amount = (float) (RpgBridge.skillDamage(caster, School.ARCHER, flat, modifiers.powerMultiplier())
			* eagleEyeFactor(caster) * quiverDamageFactor(caster) * ArrowHits.skillFactor(arrow));
		if (target.damageCooldownTime > 0) {
			target.damageCooldownTime = 0;
		}
		target.hurtServer((ServerLevel) caster.level(), RpgBridge.skillSource(caster), amount);
		ArrowHits.skillEffect(caster, target, arrow);
	}

	/** The trail of a skill shot: a coloured line with glints, and a burst and layered sounds where it stops. */
	static void trail(
		final ServerPlayer caster, final String book, final Vec3 direction, final double range, final @Nullable Mob hitFoe, final int color
	) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 start = caster.getEyePosition();
		double reach = hitFoe == null
			? SkillTargeting.blockedReach(level, caster, start, direction, range)
			: Math.min(range, start.distanceTo(hitFoe.getBoundingBox().getCenter()));
		Vec3 end = start.add(direction.scale(reach));
		ArcherVisuals.trail(level, caster, book(book), start, end, color);
		ArcherVisuals.impact(level, caster, book(book), end, color);
	}

	private static Identifier book(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
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
		BLITZ,
		PARROT,
		GUARD,
		BOMBLET
	}

	/** The book the caster's next arrow owes. */
	private enum ShotKind {
		AIMED_BOLT,
		EXPLOSIVE_ARROW
	}

	private record Shot(ShotKind kind, SpellModifiers.CastModifiers modifiers, int expiresAt) {
	}

	/** One running Arrow Shower: where its arrows aim, what they are made of, and how many have left. */
	private static final class Shower {
		final List<Vec3> spots;
		final List<ItemStack> ammo;
		final ItemStack weapon;
		final int startedAt;
		int loosed;

		Shower(final List<Vec3> spots, final List<ItemStack> ammo, final ItemStack weapon, final int startedAt) {
			this.spots = spots;
			this.ammo = ammo;
			this.weapon = weapon;
			this.startedAt = startedAt;
		}
	}

	/** The foe an idle falcon strike is hitting, and the experience it carried when it died under that strike. */
	private static final class ParrotKill {
		final Mob foe;
		final ServerPlayer owner;
		int reward = -1;

		ParrotKill(final Mob foe, final ServerPlayer owner) {
			this.foe = foe;
			this.owner = owner;
		}
	}

	private record Job(
		@Nullable Vec3 at, @Nullable UUID foe, int dueTick, JobKind kind, SpellModifiers.CastModifiers modifiers, float amount
	) {
		Job(final @Nullable Vec3 at, final @Nullable UUID foe, final int dueTick, final JobKind kind, final SpellModifiers.CastModifiers modifiers) {
			this(at, foe, dueTick, kind, modifiers, 0.0F);
		}
	}

	private record Snare(Vec3 at, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int expiresAt,
		SpellModifiers.CastModifiers modifiers) {
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
		tickSnares(server, now);
		tickShowers(server, now);
		tickParrots(server, now);
		tickGuards(server, now);
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

	// A snare ends when a foe springs it or its life runs out; the owner need not be online.
	private static void tickSnares(final MinecraftServer server, final int now) {
		for (Map.Entry<UUID, List<Snare>> entry : List.copyOf(SNARES.entrySet())) {
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			for (Snare snare : List.copyOf(entry.getValue())) {
				ServerLevel level = server.getLevel(snare.dimension());
				if (now >= snare.expiresAt() || level == null || springSnare(level, entry.getKey(), owner, snare)) {
					dropSnare(entry.getKey(), snare);
				}
			}
		}
	}

	private static void run(final ServerPlayer caster, final Job job) {
		PULSES_RUN.merge(caster.getUUID(), 1, Integer::sum);

		switch (job.kind()) {
			case BLITZ, PARROT, GUARD -> {
				if (job.foe() != null && ((ServerLevel) caster.level()).getEntity(job.foe()) instanceof Mob foe && foe.isAlive()) {
					strike(caster, foe, job.amount(), job.kind());
				}
			}
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
		Identifier spell = book("explosive_arrow");
		ArcherVisuals.ring(level, caster, spell, at.add(0.0, 0.2, 0.0), radius, ArcherVisuals.BLAST_COLOR, 24);
		SpellVisuals.sound(level, caster, spell, SoundEvents.GENERIC_EXPLODE.value(), at, 0.7F, 1.3F);
		SpellVisuals.sound(level, caster, spell, SoundEvents.FIREWORK_ROCKET_BLAST, at, 0.6F, 1.0F);
	}
}
