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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Test-side bookkeeping: the arrows each shooter looses and the answers each caller drew, both by identity so a test
 * reads its own subject and no neighbour's, and every behaviour log line the mod writes, read back by content.
 */
public final class LearnTestInit implements ModInitializer {
	private static final Map<UUID, AtomicInteger> LOOSED = new ConcurrentHashMap<>();
	private static final Map<UUID, AtomicInteger> ANSWERED = new ConcurrentHashMap<>();
	private static final Queue<String> LINES = new ConcurrentLinkedQueue<>();

	@Override
	public void onInitialize() {
		LearnLog.tap(LINES::add);
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
}
