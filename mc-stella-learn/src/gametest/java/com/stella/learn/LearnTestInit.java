package com.stella.learn;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Test-side bookkeeping: the arrows each shooter looses and the answers each caller drew, both by identity so a test
 * reads its own subject and no neighbour's, and every behaviour log line the mod writes, read back by content.
 */
public final class LearnTestInit implements ModInitializer {
	private static final Map<UUID, AtomicInteger> LOOSED = new ConcurrentHashMap<>();
	private static final Map<UUID, AtomicInteger> ANSWERED = new ConcurrentHashMap<>();
	private static final Queue<String> LINES = new ConcurrentLinkedQueue<>();
	private static final Queue<FxEvent> FX = new ConcurrentLinkedQueue<>();

	/** One thing the callout animation drew or played. `receiver` is null for a burst or a sound. */
	record FxEvent(String kind, UUID caller, UUID receiver, Identifier type, Vec3 from, Vec3 to, int count, boolean seen) {
	}

	@Override
	public void onInitialize() {
		LearnLog.tap(LINES::add);
		CalloutFx.setRecorder(new CalloutFx.Recorder() {
			@Override
			public void burst(final UUID caller, final Identifier type, final Vec3 at, final int count, final boolean seen) {
				FX.add(new FxEvent("burst", caller, null, type, at, at, count, seen));
			}

			@Override
			public void beam(final UUID caller, final UUID receiver, final Identifier type, final Vec3 from, final Vec3 to, final int steps, final boolean seen) {
				FX.add(new FxEvent("beam", caller, receiver, type, from, to, steps, seen));
			}

			@Override
			public void sound(final UUID caller, final Identifier type, final Vec3 at) {
				FX.add(new FxEvent("sound", caller, null, type, at, at, 1, true));
			}
		});
		CalloutBoard.observe(event -> ANSWERED.computeIfAbsent(event.caller(), id -> new AtomicInteger()).incrementAndGet());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() != null) {
				LOOSED.computeIfAbsent(arrow.getOwner().getUUID(), id -> new AtomicInteger()).incrementAndGet();
			}
		});
	}

	static int loosedBy(final UUID shooter) {
		AtomicInteger count = LOOSED.get(shooter);
		return count == null ? 0 : count.get();
	}

	/** How many answers this caller's calls have drawn, so a test reads its own subject's calls and no neighbour's. */
	static int answersTo(final UUID caller) {
		AtomicInteger count = ANSWERED.get(caller);
		return count == null ? 0 : count.get();
	}

	/** Every log line written so far that holds all of `needles`, in the order written. */
	static List<String> linesWith(final String... needles) {
		return LINES.stream().filter(line -> Arrays.stream(needles).allMatch(line::contains)).toList();
	}

	/** Every animation event this caller's calls drew, in the order drawn. */
	static List<FxEvent> fxOf(final UUID caller) {
		return FX.stream().filter(event -> event.caller().equals(caller)).toList();
	}
}
