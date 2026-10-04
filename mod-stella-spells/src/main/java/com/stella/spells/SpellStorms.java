package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Chainstorm's lightning: strikes that fall one after another onto the hostiles around a spot. */
public final class SpellStorms {
	public static final double RADIUS = SpellNumbers.CHAINSTORM.radius();
	public static final int MAX_STRIKES = SpellNumbers.CHAINSTORM.targets();
	public static final int STRIKE_INTERVAL_TICKS = 6;
	public static final float STRIKE_DAMAGE = SpellNumbers.CHAINSTORM.damage();
	/** Marks the storm's own bolts, which light the sky but leave copper and lightning rods alone. */
	public static final String STORM_BOLT_TAG = "mod-stella-spells.storm_bolt";

	public interface Recorder {
		void struck(LivingEntity caster, UUID target, int serverTick);

		/** One arc drawn from the previous target to this one; {@code particles} is how many it sent. */
		default void arced(LivingEntity caster, UUID from, UUID to, int particles, int serverTick) {
		}
	}

	/** {@code from} is the previous target, and {@code fromPos} where it stood when the strike was queued. */
	private record Strike(
		LivingEntity caster, ServerLevel level, LivingEntity target, float damage, int dueTick,
		@Nullable LivingEntity from, @Nullable Vec3 fromPos
	) {
	}

	private static final Recorder SILENT = (caster, target, serverTick) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static final List<Strike> PENDING = new ArrayList<>();
	private static final List<Recorder> EXTRA_RECORDERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private SpellStorms() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** A test seam: hears every strike beside the recorder that {@link #setRecorder} holds, and is never replaced by it. */
	static void addRecorder(final Recorder extra) {
		EXTRA_RECORDERS.add(extra);
	}

	/** Strike order: the first target is nearest the point, each next one is the unstruck target nearest the last. */
	static List<LivingEntity> chainOrder(final List<LivingEntity> targets, final Vec3 point) {
		List<LivingEntity> remaining = new ArrayList<>(targets);
		List<LivingEntity> ordered = new ArrayList<>(remaining.size());
		Vec3 last = point;
		while (!remaining.isEmpty()) {
			Vec3 from = last;
			LivingEntity next = remaining.stream().min(Comparator
				.comparingDouble((LivingEntity mob) -> mob.position().distanceToSqr(from))
				.thenComparingInt(LivingEntity::getId)).orElseThrow();
			remaining.remove(next);
			ordered.add(next);
			last = next.position();
		}
		return ordered;
	}

	public static int pendingCount() {
		synchronized (PENDING) {
			return PENDING.size();
		}
	}

	/** Drops every pending strike, so none outlives the server that queued it. */
	public static void onServerStopping(final MinecraftServer server) {
		synchronized (PENDING) {
			PENDING.clear();
		}
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpellStorms::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(SpellStorms::onServerStopping);
	}

	/** Strikes the nearest hostiles around the point now and in sequence; false when there is nothing to strike. */
	static boolean start(final ServerPlayer caster, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		ServerLevel level = caster.level();
		double radius = SpellNumbers.CHAINSTORM.radius(modifiers);
		List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(point) <= radius * radius
		));
		if (targets.isEmpty()) {
			return false;
		}

		return queue(caster, level, point, targets, RpgBridge.damage(caster, STRIKE_DAMAGE, modifiers.powerMultiplier()));
	}

	static boolean startForMob(final Mob caster, final Vec3 point) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return false;
		}
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
			new AABB(point, point).inflate(RADIUS), candidate -> candidate != caster && candidate.isAlive()
				&& candidate.position().distanceToSqr(point) <= RADIUS * RADIUS && MobSpells.isFoeOf(caster, candidate));
		return queue(caster, level, point, targets, STRIKE_DAMAGE);
	}

	private static boolean queue(final LivingEntity caster, final ServerLevel level, final Vec3 point,
		final List<LivingEntity> targets, final float damage) {
		if (targets.isEmpty()) {
			return false;
		}
		List<LivingEntity> ordered = chainOrder(targets, point);
		int now = level.getServer().getTickCount();
		strike(new Strike(caster, level, ordered.get(0), damage, now, null, null), now);
		synchronized (PENDING) {
			for (int i = 1; i < Math.min(MAX_STRIKES, ordered.size()); i++) {
				LivingEntity previous = ordered.get(i - 1);
				PENDING.add(new Strike(caster, level, ordered.get(i), damage, now + i * STRIKE_INTERVAL_TICKS,
					previous, chest(previous)));
			}
		}
		return true;
	}

	/** Where an arc meets a mob: its chest. */
	private static Vec3 chest(final LivingEntity mob) {
		return mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		List<Strike> due = new ArrayList<>();
		synchronized (PENDING) {
			for (Iterator<Strike> it = PENDING.iterator(); it.hasNext(); ) {
				Strike next = it.next();
				// A strike queued by another server, such as a world left in singleplayer, is dropped.
				if (next.level().getServer() != server) {
					it.remove();
				} else if (next.dueTick() <= now) {
					due.add(next);
					it.remove();
				}
			}
		}
		due.forEach(strike -> strike(strike, now));
	}

	// The first strike is a visual-only sky bolt, every later one an arc from the last target; both end in the storm's damage.
	private static void strike(final Strike strike, final int now) {
		LivingEntity target = strike.target();
		if (!target.isAlive() || target.level() != strike.level()) {
			return;
		}

		if (strike.fromPos() == null) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(strike.level(), EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setVisualOnly(true);
				bolt.addTag(STORM_BOLT_TAG);
				bolt.snapTo(target.getX(), target.getY(), target.getZ());
				strike.level().addFreshEntity(bolt);
			}
		} else {
			LivingEntity from = strike.from();
			Vec3 start = from != null && from.isAlive() ? chest(from) : strike.fromPos();
			int particles = SpellVisuals.arc(strike.level(), start, chest(target), strike.level().getRandom());
			UUID fromId = from.getUUID();
			recorder.arced(strike.caster(), fromId, target.getUUID(), particles, now);
			EXTRA_RECORDERS.forEach(extra -> extra.arced(strike.caster(), fromId, target.getUUID(), particles, now));
		}
		target.damageCooldownTime = 0;
		target.hurtServer(strike.level(), SpellCasting.spellSource(strike.caster()), strike.damage());
		recorder.struck(strike.caster(), target.getUUID(), now);
		EXTRA_RECORDERS.forEach(extra -> extra.struck(strike.caster(), target.getUUID(), now));
	}
}
