package com.stella.mobs;

import com.stella.mobs.behavior.SkeletonVolley;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** What each skeleton's volleys spawned, keyed by the shooter, so a test reads only its own arrows. */
final class VolleyRecord {
	private static final Map<UUID, List<Volley>> VOLLEYS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Cloud>> CLOUDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SHOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<MobEffectInstance>> SLOWS = new ConcurrentHashMap<>();

	private VolleyRecord() {
	}

	static void install() {
		SkeletonVolley.setRecorder(new SkeletonVolley.Recorder() {
			@Override
			public void volley(final Mob shooter, final List<UUID> arrows, final List<Vec3> velocities) {
				int burning = (int) arrows.stream().map(id -> ((net.minecraft.server.level.ServerLevel) shooter.level()).getEntity(id))
					.filter(entity -> entity != null && entity.isOnFire()).count();
				VOLLEYS.computeIfAbsent(shooter.getUUID(), key -> new CopyOnWriteArrayList<>())
					.add(new Volley(List.copyOf(arrows), List.copyOf(velocities), shooter.level().getServer().getTickCount(), burning));
			}

			@Override
			public void shot(final Mob shooter, final List<UUID> arrows) {
				SHOTS.merge(shooter.getUUID(), arrows.size(), Integer::sum);
			}

			@Override
			public void slowed(final Mob shooter, final net.minecraft.world.entity.LivingEntity victim, final MobEffectInstance slow) {
				// What the victim now carries, not what the shot meant to give it.
				MobEffectInstance carried = victim.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
				if (carried != null) {
					SLOWS.computeIfAbsent(shooter.getUUID(), key -> new CopyOnWriteArrayList<>()).add(carried);
				}
			}

			@Override
			public void cloud(final Mob shooter, final UUID arrow, final UUID cloud, final Vec3 at, final List<MobEffectInstance> effects) {
				CLOUDS.computeIfAbsent(shooter.getUUID(), key -> new CopyOnWriteArrayList<>())
					.add(new Cloud(arrow, cloud, at, List.copyOf(effects)));
			}
		});
	}

	static List<Volley> volleys(final Mob shooter) {
		return VOLLEYS.getOrDefault(shooter.getUUID(), List.of());
	}

	static List<Cloud> clouds(final Mob shooter) {
		return CLOUDS.getOrDefault(shooter.getUUID(), List.of());
	}

	static int shots(final Mob shooter) {
		return SHOTS.getOrDefault(shooter.getUUID(), 0);
	}

	static List<MobEffectInstance> slows(final Mob shooter) {
		return SLOWS.getOrDefault(shooter.getUUID(), List.of());
	}

	record Volley(List<UUID> arrows, List<Vec3> velocities, int serverTick, int burning) {
	}

	record Cloud(UUID arrow, UUID cloud, Vec3 at, List<MobEffectInstance> effects) {
	}
}
