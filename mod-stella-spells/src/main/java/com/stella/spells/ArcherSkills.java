package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ten archer books: instant hit-scan rays with an arrow trail, never an arrow entity. A ray deals its
 * damage through {@link RpgBridge#skillDamage} with {@link School#ARCHER} exactly once, so no shot takes the
 * ranged dice a second time.
 */
public final class ArcherSkills {
	/** How many rays a fan looses, plain and awakened. */
	static final int MULTISHOT_RAYS = 5;
	static final int AWAKENED_MULTISHOT_RAYS = 9;
	static final double MULTISHOT_FAN_DEGREES = 30.0;
	/** How far an awakened fan ray bends toward the nearest foe. */
	static final double AWAKENED_HOMING_DEGREES = 8.0;
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
	/** The share of its own maximum health a victim of an awakened trap keeps moving with. */
	static final int TRAP_ROOT_AMPLIFIER = 6;
	/** Explosive Arrow's splash, and the three bomblets an awakened one scatters. */
	static final double EXPLOSIVE_SPLASH_RADIUS = 3.0;
	static final int EXPLOSIVE_BOMBLETS = 3;
	static final double BOMBLET_RADIUS = 2.0;
	static final int BOMBLET_FLIGHT_TICKS = 6;
	/** Disengage's own push and the reach of the slow it leaves behind. */
	static final double DISENGAGE_BACK = 1.2;
	static final double DISENGAGE_UP = 0.6;
	static final double DISENGAGE_RADIUS = 3.0;
	static final int DISENGAGE_SLOW_TICKS = 60;
	/** The reach a trap catches in, and how long a trap waits. */
	static final double TRAP_RADIUS = 1.5;
	static final int TRAP_TICKS = 600;
	static final int TRAP_TRIGGER_TICKS = 80;
	static final int TRAP_ARMED_TICKS = 10;
	static final int TRAPS_PER_CASTER = 1;
	static final int AWAKENED_TRAPS_PER_CASTER = 3;
	/** Eagle Eye: half an hour, and the share every archer skill hit gains while it holds. */
	static final int EAGLE_EYE_TICKS = 36_000;
	static final double EAGLE_EYE_PLAIN_FACTOR = 1.2;
	static final double EAGLE_EYE_AWAKENED_FACTOR = 1.4;
	static final double EAGLE_EYE_AWAKENED_AMPLIFIER = 1.0;
	/** Elemental Quiver: how many arrows one scroll imbues, plain and awakened. */
	static final int QUIVER_ARROWS = 10;
	static final int AWAKENED_QUIVER_ARROWS = 20;

	/** The pulses still owed, by caster, oldest first. */
	private static final Map<UUID, List<Job>> JOBS = new ConcurrentHashMap<>();
	/** The traps standing, by caster; an awakened trap is the one whose victim is staggered. */
	private static final Map<UUID, List<Trap>> TRAPS = new ConcurrentHashMap<>();
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
	}

	/** No job or trap outlives the server it was put on, and no job keeps a due tick from another server. */
	static void clear() {
		JOBS.clear();
		TRAPS.clear();
		PULSES_RUN.clear();
		clock = 0;
	}

	/**
	 * Everything this caster owes, dropped without touching the server's clock. A test that only wants its
	 * own state gone calls this: resetting the clock would leave every other job in the process un-due.
	 */
	static void clearFor(final ServerPlayer caster) {
		JOBS.remove(caster.getUUID());
		TRAPS.remove(caster.getUUID());
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

	/** How many traps this caster has standing; the test seam for the trap cap. */
	static int trapsStanding(final ServerPlayer caster) {
		List<Trap> traps = TRAPS.get(caster.getUUID());
		return traps == null ? 0 : traps.size();
	}

	// ---- the ten books ----

	/** Multishot: a fan of rays, each ray hitting the first foe on it. An awakened fan bends onto its foe. */
	static boolean applyMultishot(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		SpellNumbers numbers = SpellNumbers.MULTISHOT;
		int rays = modifiers.awakened() ? AWAKENED_MULTISHOT_RAYS : MULTISHOT_RAYS;
		double reach = numbers.radius(modifiers);
		boolean struck = false;
		for (int ray = 0; ray < rays; ray++) {
			Vec3 direction = fanDirection(caster, rays, ray);
			// An awakened ray bends onto the nearest foe inside its own bend, so a fan of it covers a rank.
			if (modifiers.awakened()) {
				direction = bent(caster, direction, reach);
			}
			Mob foe = SkillTargeting.rayFoe(caster, direction, reach);
			if (foe == null) {
				trail(caster, direction, reach, null);
				continue;
			}
			struck = true;
			trail(caster, direction, reach, foe);
			hit(caster, foe, numbers.damage(), modifiers);
		}
		return struck;
	}

	// The nearest foe within the bend of this ray, whose own direction turns onto it; the ray itself when none.
	private static Vec3 bent(final ServerPlayer caster, final Vec3 direction, final double reach) {
		Vec3 toNearest = null;
		double best = Double.MAX_VALUE;
		for (Mob foe : SkillTargeting.foesOnRay(caster, direction, reach)) {
			double distance = foe.position().distanceToSqr(caster.position());
			if (distance < best) {
				best = distance;
				toNearest = foe.getBoundingBox().getCenter().subtract(caster.getEyePosition());
			}
		}
		if (toNearest == null || toNearest.lengthSqr() < 1.0E-6) {
			return direction;
		}
		double degrees = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, direction.normalize().dot(toNearest.normalize())))));
		return degrees <= AWAKENED_HOMING_DEGREES ? toNearest.normalize() : direction;
	}

	// The fan sits around the caster's own look, evenly spread across its whole angle.
	private static Vec3 fanDirection(final ServerPlayer caster, final int rays, final int index) {
		double step = MULTISHOT_FAN_DEGREES / (rays - 1);
		return rotate(caster.getLookAngle(), -MULTISHOT_FAN_DEGREES / 2.0 + index * step);
	}

	/** Piercing Shot: every foe on a long line, in order. Each later foe takes more when it is awakened. */
	static boolean applyPiercingShot(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
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
		if (point == null) {
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
		if (target == null) {
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
		if (target == null) {
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

	/** Explosive Arrow: the target and its neighbours, with no block broken. Awakened: three bomblets. */
	static boolean applyExplosiveArrow(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (target == null) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.EXPLOSIVE_ARROW;
		blast(caster, target.position(), modifiers);
		hit(caster, target, numbers.damage(), modifiers);
		for (Mob foe : foesAroundPoint(caster, target.position(), EXPLOSIVE_SPLASH_RADIUS)) {
			if (foe != target) {
				hit(caster, foe, splash(numbers, modifiers), modifiers);
			}
		}
		if (modifiers.awakened()) {
			for (int bomblet = 0; bomblet < EXPLOSIVE_BOMBLETS; bomblet++) {
				Vec3 at = target.position().add(bombletOffset(bomblet));
				owe(caster, new Job(at, dueIn(BOMBLET_FLIGHT_TICKS * (bomblet + 1)),
					JobKind.BOMBLET, modifiers));
			}
		}
		return true;
	}

	// The three bomblets land on a triangle inside the blast, so each one reaches its own ground.
	static Vec3 bombletOffset(final int index) {
		double angle = index * (Math.PI * 2.0 / EXPLOSIVE_BOMBLETS);
		return new Vec3(Math.cos(angle) * (EXPLOSIVE_SPLASH_RADIUS - 1.0), 0.0, Math.sin(angle) * (EXPLOSIVE_SPLASH_RADIUS - 1.0));
	}

	/** Disengage: the caster springs back and up, and the ground it left slows what stands there. */
	static boolean applyDisengage(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		Vec3 look = caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() > 1.0E-6) {
			flat = flat.normalize();
		} else {
			flat = new Vec3(0.0, 0.0, 1.0);
		}
		Vec3 start = caster.position();
		caster.setDeltaMovement(-flat.x * DISENGAGE_BACK, DISENGAGE_UP, -flat.z * DISENGAGE_BACK);
		caster.resetFallDistance();
		for (Mob foe : foesAroundPoint(caster, start, SpellNumbers.DISENGAGE.radius(modifiers))) {
			if (modifiers.awakened()) {
				SpellStatusApi.stagger(foe, SpellNumbers.DISENGAGE.durationTicks(modifiers), caster);
			} else {
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DISENGAGE_SLOW_TICKS, SpellNumbers.DISENGAGE.amplifier()), caster);
			}
		}
		caster.level().playSound(null, start.x, start.y, start.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
		return true;
	}

	/** Snare Trap: one trap at the point, three when it is awakened. The first foe inside takes it. */
	static boolean applySnareTrap(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}
		int cap = modifiers.awakened() ? AWAKENED_TRAPS_PER_CASTER : TRAPS_PER_CASTER;
		List<Trap> traps = new ArrayList<>(TRAPS.getOrDefault(caster.getUUID(), List.of()));
		while (traps.size() >= cap) {
			traps.remove(0);
		}
		traps.add(new Trap(point, dueIn(TRAP_TICKS), dueIn(TRAP_ARMED_TICKS), modifiers));
		TRAPS.put(caster.getUUID(), traps);
		caster.level().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, point.x, point.y + 0.2, point.z, 12, 0.6, 0.2, 0.6, 0.0);
		return true;
	}

	/** Eagle Eye: half an hour of the archer's own eye, and the amplifier says whether it is awakened. */
	static boolean applyEagleEye(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
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

	/**
	 * Elemental Quiver: the first scroll in the pack imbues its spell into the arrows there. A cast with no
	 * scroll or no arrow does nothing at all, so the caller reports NO_TARGET and no rest follows.
	 */
	static boolean applyElementalQuiver(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		ItemStack scroll = firstScroll(caster);

		if (scroll.isEmpty()) {
			return false;
		}
		SpellDefinition spell = ((SpellScrollItem) scroll.getItem()).definition();
		if (SpellImbue.NOT_IMBUABLE.contains(spell.id()) || Spellbooks.BOOK_ONLY.contains(spell.id())) {
			return false;
		}
		ItemStack arrows = firstArrows(caster);
		if (arrows.isEmpty()) {
			return false;
		}
		ItemStack book = new ItemStack(BuiltInRegistries.ITEM.getValue(spell.id()));
		int count = Math.min(modifiers.awakened() ? AWAKENED_QUIVER_ARROWS : QUIVER_ARROWS, arrows.getCount());
		ItemStack whole = SpellImbue.imbue(arrows.copyWithCount(count), book);
		if (whole.isEmpty()) {
			return false;
		}
		// The cast is all or nothing: a pack with no room for the imbued arrows keeps the plain ones and the scroll.
		arrows.shrink(count);
		if (!caster.getInventory().add(whole)) {
			arrows.grow(count);
			return false;
		}
		if (!modifiers.awakened()) {
			scroll.shrink(1);
		}
		Spellbooks.LOGGER.info("[SPELL] event=ELEMENTAL_QUIVER caster={} spell={} arrows={} scrollKept={}",
			caster.getUUID(), spell.id(), whole.getCount(), modifiers.awakened());
		return true;
	}

	// The first scroll of the pack, in inventory order.
	private static ItemStack firstScroll(final ServerPlayer caster) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.getItem() instanceof SpellScrollItem) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// The first stack of arrows in the pack, in inventory order.
	private static ItemStack firstArrows(final ServerPlayer caster) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.is(net.minecraft.tags.ItemTags.ARROWS)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---- the shared ray helpers ----

	/** A hit: the school's own dice once, then the caster's Eagle Eye, and nothing else. */
	static void hit(final ServerPlayer caster, final Mob target, final float flat, final SpellModifiers.CastModifiers modifiers) {
		float amount = (float) (RpgBridge.skillDamage(caster, School.ARCHER, flat, modifiers.powerMultiplier()) * eagleEyeFactor(caster));
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
	 * Every foe within this reach of a point in the caster's own level. The reach carries {@link #SLACK}, so a
	 * body standing exactly on the edge is inside the area rather than a rounding error outside it.
	 */
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

	private static Vec3 rotate(final Vec3 direction, final double degrees) {
		double radians = Math.toRadians(degrees);
		double cosine = Math.cos(radians);
		double sine = Math.sin(radians);
		return new Vec3(direction.x * cosine - direction.z * sine, direction.y, direction.x * sine + direction.z * cosine);
	}

	// ---- the timed jobs ----

	private enum JobKind {
		RAIN,
		RAPID_FIRE,
		BOMBLET
	}

	private record Job(@Nullable Vec3 at, int dueTick, JobKind kind, SpellModifiers.CastModifiers modifiers) {
	}

	private record Trap(Vec3 at, int expiresAt, int armedAt, SpellModifiers.CastModifiers modifiers) {
	}

	private static void owe(final ServerPlayer caster, final Job job) {
		JOBS.compute(caster.getUUID(), (id, existing) -> {
			List<Job> jobs = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
			jobs.add(job);
			jobs.sort(Comparator.comparingInt(Job::dueTick));
			return jobs;
		});

	}

	// A job whose caster is gone is dropped, so no pulse outlives the player who owed it.
	private static void tick(final MinecraftServer server) {
		int now = ++clock;
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
		for (Map.Entry<UUID, List<Trap>> entry : List.copyOf(TRAPS.entrySet())) {
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			if (caster == null || !caster.isAlive()) {
				TRAPS.remove(entry.getKey());
				continue;
			}
			List<Trap> left = new ArrayList<>();
			for (Trap trap : entry.getValue()) {
				if (now >= trap.expiresAt() || !spring(caster, trap, now)) {
					if (now < trap.expiresAt()) {
						left.add(trap);
					}
					continue;
				}
				Spellbooks.LOGGER.info("[SPELL] event=SNARE_SPRING caster={} at={}", caster.getUUID(), trap.at());
			}
			if (left.isEmpty()) {
				TRAPS.remove(entry.getKey());
			} else {
				TRAPS.put(entry.getKey(), left);
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

	// A standing trap: the first foe inside its reach takes the hit and is rooted in place.
	private static boolean spring(final ServerPlayer caster, final Trap trap, final int now) {
		if (now < trap.armedAt()) {
			return false;
		}
		caster.level().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, trap.at().x, trap.at().y + 0.2, trap.at().z,
			2, 0.3, 0.1, 0.3, 0.0);
		Mob caught = null;
		for (Mob foe : foesAroundPoint(caster, trap.at(), TRAP_RADIUS)) {
			caught = foe;
			break;
		}
		if (caught == null) {
			return false;
		}
		hit(caster, caught, SpellNumbers.SNARE_TRAP.damage(), trap.modifiers());
		if (trap.modifiers().awakened()) {
			SpellStatusApi.stagger(caught, SpellNumbers.SNARE_TRAP.durationTicks(trap.modifiers()), caster);
		} else {
			caught.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TRAP_TRIGGER_TICKS, SpellNumbers.SNARE_TRAP.amplifier()), caster);
		}
		return true;
	}
}
