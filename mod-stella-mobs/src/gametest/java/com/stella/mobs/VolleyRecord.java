package com.stella.mobs;

import com.stella.mobs.behavior.SkeletonVolley;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** What each skeleton's bow released, keyed by the shooter, so a test reads only its own arrows. */
final class VolleyRecord {
	/** Burst arrows sit 10 ticks apart and bursts 200, so any gap past this starts a burst. */
	private static final int NEW_BURST_GAP_TICKS = 100;
	private static final Map<UUID, List<Release>> BURST_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Release>> SHOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<MobEffectInstance>> SLOWS = new ConcurrentHashMap<>();
	private static boolean installed;

	private VolleyRecord() {
	}

	static synchronized void install() {
		if (installed) {
			return;
		}

		installed = true;
		SkeletonVolley.setRecorder(new SkeletonVolley.Recorder() {
			@Override
			public void burst(final Mob shooter, final UUID arrow, final Vec3 velocity) {
				BURST_ARROWS.computeIfAbsent(shooter.getUUID(), key -> new CopyOnWriteArrayList<>()).add(release(shooter, arrow, velocity));
			}

			@Override
			public void shot(final Mob shooter, final UUID arrow, final Vec3 velocity) {
				SHOTS.computeIfAbsent(shooter.getUUID(), key -> new CopyOnWriteArrayList<>()).add(release(shooter, arrow, velocity));
			}

			@Override
			public void slowed(final Mob shooter, final LivingEntity victim, final MobEffectInstance slow) {
				// What the victim now carries, not what the shot meant to give it.
				MobEffectInstance carried = victim.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
				if (carried != null) {
					SLOWS.computeIfAbsent(shooter.getUUID(), key -> new CopyOnWriteArrayList<>()).add(carried);
				}
			}
		});
	}

	/** Burst arrows loosed, in release order. */
	static List<Release> burstArrows(final Mob shooter) {
		return BURST_ARROWS.getOrDefault(shooter.getUUID(), List.of());
	}

	/** Single shots loosed, in release order. */
	static List<Release> shots(final Mob shooter) {
		return SHOTS.getOrDefault(shooter.getUUID(), List.of());
	}

	/**
	 * The burst arrows grouped into bursts.
	 * A gap past `NEW_BURST_GAP_TICKS` starts the next burst.
	 */
	static List<List<Release>> bursts(final Mob shooter) {
		List<List<Release>> bursts = new ArrayList<>();
		Release previous = null;
		for (Release arrow : burstArrows(shooter)) {
			if (previous == null || arrow.serverTick() - previous.serverTick() > NEW_BURST_GAP_TICKS) {
				bursts.add(new ArrayList<>());
			}

			bursts.get(bursts.size() - 1).add(arrow);
			previous = arrow;
		}

		return bursts;
	}

	static List<MobEffectInstance> slows(final Mob shooter) {
		return SLOWS.getOrDefault(shooter.getUUID(), List.of());
	}

	private static Release release(final Mob shooter, final UUID arrow, final Vec3 velocity) {
		LivingEntity target = shooter.getTarget();
		double distance = Double.NaN;
		double lineYaw = Double.NaN;
		if (target != null) {
			distance = shooter.distanceTo(target);
			lineYaw = Math.toDegrees(Math.atan2(target.getZ() - shooter.getZ(), target.getX() - shooter.getX()));
		}

		return new Release(arrow, velocity, shooter.level().getServer().getTickCount(), distance, lineYaw);
	}

	/**
	 * One arrow as it left the bow. `lineYaw` is the horizontal bearing from the shooter to its target in degrees,
	 * so a test reads the spread without knowing the aim's height.
	 */
	record Release(UUID arrow, Vec3 velocity, int serverTick, double targetDistance, double lineYaw) {
	}
}
