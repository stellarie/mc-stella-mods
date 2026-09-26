package com.stella.mobs;

import com.stella.mobs.behavior.EvokerStorm;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** What each storm did, keyed by its caster, so a test reads its own storm and never a neighbour's. */
final class StormRecord {
	private static final Map<UUID, List<Begin>> BEGINS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Ball>> BALLS = new ConcurrentHashMap<>();

	private StormRecord() {
	}

	static void install() {
		EvokerStorm.setRecorder(new EvokerStorm.Recorder() {
			@Override
			public void begun(final Mob caster, final String reason, final Vec3 centre) {
				BEGINS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>())
					.add(new Begin(reason, centre, caster.level().getServer().getTickCount()));
			}

			@Override
			public void ball(final Mob caster, final UUID ball, final Vec3 at, final int age) {
				BALLS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>())
					.add(new Ball(ball, at, age, caster.level().getServer().getTickCount()));
			}
		});
	}

	static List<Begin> begins(final Mob caster) {
		return BEGINS.getOrDefault(caster.getUUID(), List.of());
	}

	static List<Ball> balls(final Mob caster) {
		return BALLS.getOrDefault(caster.getUUID(), List.of());
	}

	record Begin(String reason, Vec3 centre, int serverTick) {
	}

	record Ball(UUID id, Vec3 at, int age, int serverTick) {
	}
}
