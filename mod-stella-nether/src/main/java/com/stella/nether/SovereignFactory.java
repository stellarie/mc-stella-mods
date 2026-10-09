package com.stella.nether;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import org.jspecify.annotations.Nullable;

/**
 * Publishes {@code mod-stella-nether:sovereign-factory}, a {@code BiFunction<ServerLevel, CompoundTag, Mob>}
 * that builds a Sovereign outside any raid. The tag's {@code level} int is the Sovereign's final mob level;
 * other keys are ignored. The caller places the mob and adds it. The factory returns null on a bad tag and never
 * throws. A Sovereign built here touches no raid state and keeps its own bar, charge and special state; this class
 * ticks every one that stands loaded.
 */
public final class SovereignFactory {
	public static final String SHARE_KEY = "mod-stella-nether:sovereign-factory";
	static final String LEVEL_KEY = "level";

	/** A loaded free Sovereign and its bar. */
	private record Free(Blaze blaze, ServerBossEvent bar) {
	}

	/** Where a refusal line goes; a test swaps it to count lines. */
	static volatile java.util.function.Consumer<String> refusalSink = line -> StellaNether.LOGGER.warn(line);

	private static final Map<UUID, Free> LIVE = new ConcurrentHashMap<>();

	private SovereignFactory() {
	}

	public static void register() {
		BiFunction<ServerLevel, CompoundTag, Mob> factory = SovereignFactory::create;
		FabricLoader.getInstance().getObjectShare().put(SHARE_KEY, factory);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (isFree(entity)) {
				track((Blaze) entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (isFree(entity)) {
				untrack((Blaze) entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Free free : java.util.List.copyOf(LIVE.values())) {
				Blaze blaze = free.blaze();
				if (!blaze.isAlive()) {
					untrack(blaze);
				} else if (blaze.level() instanceof ServerLevel level) {
					BlazeSovereign.tickSovereign(level, blaze, free.bar(), server.getTickCount());
				}
			}
		});
	}

	/** The bar a loaded free Sovereign keeps for itself; null for any other blaze. */
	public static @Nullable ServerBossEvent barOf(final Blaze blaze) {
		Free free = LIVE.get(blaze.getUUID());
		return free != null && free.blaze() == blaze ? free.bar() : null;
	}

	static boolean isFree(final Entity entity) {
		return entity instanceof Blaze blaze && Integer.valueOf(BlazeSovereign.FREE_RUN).equals(blaze.getAttached(BlazeSovereign.SOVEREIGN));
	}

	private static void track(final Blaze blaze) {
		Free old = LIVE.put(blaze.getUUID(), new Free(blaze, new ServerBossEvent(blaze.getUUID(), blaze.getDisplayName(),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10)));
		if (old != null) {
			old.bar().removeAllPlayers();
		}
	}

	private static void untrack(final Blaze blaze) {
		Free free = LIVE.get(blaze.getUUID());
		if (free == null || free.blaze() != blaze) {
			return;
		}
		LIVE.remove(blaze.getUUID());
		free.bar().removeAllPlayers();
		// A Sovereign that only unloaded keeps its timers for when it loads again.
		if (!blaze.isAlive() || blaze.getRemovalReason() != null && blaze.getRemovalReason().shouldDestroy()) {
			SovereignSpecials.forget(blaze.getUUID());
			SovereignCharge.forget(blaze.getUUID());
		}
	}

	static @Nullable Mob create(final @Nullable ServerLevel level, final @Nullable CompoundTag tag) {
		try {
			if (level == null || tag == null) {
				return refuse("no level or tag");
			}
			int threat = tag.getInt(LEVEL_KEY).orElse(0);
			if (threat < 1) {
				return refuse("level is missing or below 1 in " + tag);
			}
			Blaze blaze = BlazeSovereign.create(level, BlazeSovereign.FREE_RUN, threat);
			BehaviorLog.info("[NETHER] sovereign built free level={} health={}", threat, BlazeSovereign.effectiveMaxHealth(blaze));
			return blaze;
		} catch (RuntimeException failure) {
			return refuse("failed: " + failure);
		}
	}

	private static @Nullable Mob refuse(final String reason) {
		refusalSink.accept("[NETHER] sovereign factory refused: " + reason);
		return null;
	}
}
