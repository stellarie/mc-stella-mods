package com.stella.ruins.site.spidernest;

import com.stella.ruins.world.RuinClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * The nest's heartbeat. While the clock runs, each standing egg sac beats. The beat comes every 40 ticks until the
 * last 20 seconds, then speeds up to every 8 ticks as the clock runs out.
 * <p>
 * The objective ticks once a second, so it only refreshes a beat. A server tick event plays the beats, so the pace
 * can change inside a second. A beat that is not refreshed for {@link #STALE_TICKS} drops by itself.
 */
public final class NestHeartbeat {
	/** The beat gap while the clock has more than {@link #FAST_TICKS} left. */
	public static final int SLOW_INTERVAL = 40;
	public static final int FAST_INTERVAL = 8;
	/** The last stretch of the clock, in ticks, where the beat speeds up. */
	public static final int FAST_TICKS = 400;
	public static final int STALE_TICKS = 100;

	private record Key(ResourceKey<Level> dimension, BlockPos core) {
	}

	private record Beat(ServerLevel level, List<BlockPos> sacs, long deadline, long seen) {
	}

	private static final Map<Key, Beat> BEATS = new ConcurrentHashMap<>();
	private static long counter;

	private NestHeartbeat() {
	}

	/** The server tick count the heartbeat keeps. */
	public static long tick() {
		return counter;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> beatAll(++counter));
	}

	/** The ticks between two beats, for a clock with {@code remaining} ticks left. */
	public static int interval(final long remaining) {
		if (remaining > FAST_TICKS) {
			return SLOW_INTERVAL;
		}
		long clamped = Math.max(0L, remaining);
		return FAST_INTERVAL + (int) (clamped * (SLOW_INTERVAL - FAST_INTERVAL) / FAST_TICKS);
	}

	/** Records the standing sacs and the deadline of a nest. The objective calls it every time it ticks. */
	public static void track(final ServerLevel level, final BlockPos core, final List<BlockPos> sacs, final long deadline) {
		BEATS.put(new Key(level.dimension(), core.immutable()), new Beat(level, List.copyOf(sacs), deadline, counter));
	}

	public static void clear(final ServerLevel level, final BlockPos core) {
		BEATS.remove(new Key(level.dimension(), core));
	}

	/** True while a nest beats. */
	public static boolean isTracking(final ServerLevel level, final BlockPos core) {
		return BEATS.containsKey(new Key(level.dimension(), core));
	}

	/** Plays the beats that fall due on {@code tick}. Returns how many sacs beat. Tests call it with their own count. */
	public static int beatAll(final long tick) {
		int plays = 0;
		for (Key key : new ArrayList<>(BEATS.keySet())) {
			plays += beat(key, tick);
		}
		return plays;
	}

	/** The beats of one nest that fall due on {@code tick}. */
	public static int beatCore(final ServerLevel level, final BlockPos core, final long tick) {
		return beat(new Key(level.dimension(), core), tick);
	}

	private static int beat(final Key key, final long tick) {
		Beat beat = BEATS.get(key);
		if (beat == null) {
			return 0;
		}
		long remaining = beat.deadline() - RuinClock.now(beat.level());
		if (remaining < 0 || tick - beat.seen() > STALE_TICKS && beat.seen() <= tick) {
			BEATS.remove(key);
			return 0;
		}
		if (tick % interval(remaining) != 0) {
			return 0;
		}
		float pitch = 1.0F + 0.5F * (1.0F - Math.min(1.0F, Math.max(0L, remaining) / (float) FAST_TICKS));
		int plays = 0;
		for (BlockPos pos : beat.sacs()) {
			beat.level().playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 0.9F, pitch);
			plays++;
		}
		return plays;
	}
}
