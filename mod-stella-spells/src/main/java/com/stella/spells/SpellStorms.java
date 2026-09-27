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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Chainstorm's lightning: strikes that fall one after another onto the hostiles around a spot. */
public final class SpellStorms {
	public static final double RADIUS = 5.0;
	public static final int MAX_STRIKES = 5;
	public static final int STRIKE_INTERVAL_TICKS = 6;
	public static final float STRIKE_DAMAGE = 6.0f;
	/** Marks the storm's own bolts, which light the sky but leave copper and lightning rods alone. */
	public static final String STORM_BOLT_TAG = "mod-stella-spells.storm_bolt";

	public interface Recorder {
		void struck(ServerPlayer caster, UUID target, int serverTick);
	}

	private record Strike(ServerPlayer caster, ServerLevel level, Mob target, float damage, int dueTick) {
	}

	private static final Recorder SILENT = (caster, target, serverTick) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static final List<Strike> PENDING = new ArrayList<>();

	private SpellStorms() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
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
		double radius = RADIUS * modifiers.radiusMultiplier();
		List<Mob> targets = new ArrayList<>(level.getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(point) <= radius * radius
		));
		if (targets.isEmpty()) {
			return false;
		}

		targets.sort(Comparator.comparingDouble((Mob mob) -> mob.position().distanceToSqr(point)).thenComparingInt(Mob::getId));
		float damage = STRIKE_DAMAGE * (float) modifiers.powerMultiplier();
		int now = level.getServer().getTickCount();
		strike(new Strike(caster, level, targets.get(0), damage, now), now);
		synchronized (PENDING) {
			for (int i = 1; i < Math.min(MAX_STRIKES, targets.size()); i++) {
				PENDING.add(new Strike(caster, level, targets.get(i), damage, now + i * STRIKE_INTERVAL_TICKS));
			}
		}
		return true;
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

	// A visual-only bolt so the sky lights up without fire, then the storm's own damage on its target.
	private static void strike(final Strike strike, final int now) {
		Mob target = strike.target();
		if (!target.isAlive() || target.level() != strike.level()) {
			return;
		}

		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(strike.level(), EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setVisualOnly(true);
			bolt.addTag(STORM_BOLT_TAG);
			bolt.snapTo(target.getX(), target.getY(), target.getZ());
			strike.level().addFreshEntity(bolt);
		}
		target.damageCooldownTime = 0;
		target.hurtServer(strike.level(), strike.level().damageSources().magic(), strike.damage());
		recorder.struck(strike.caster(), target.getUUID(), now);
	}
}
