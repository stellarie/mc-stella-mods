package com.stella.learn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** Counts the arrows each shooter looses, by the shooter's identity, so a test reads its own subject and no neighbour's. */
public final class LearnTestInit implements ModInitializer {
	private static final Map<UUID, AtomicInteger> LOOSED = new ConcurrentHashMap<>();

	@Override
	public void onInitialize() {
		// One line per answer, so a run log names who accepted a callout and who declined it and why.
		CalloutBoard.observe(event -> StellaLearn.LOGGER.info(
			"[BEHAVIOR] event=callout_answer caller={} receiver={} accepted={} reason={}",
			event.caller(), event.answer().receiver(), event.answer().accepted(), event.answer().reason()
		));
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
}
