package com.stella.mobs.entity.spawn;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One custom spawn rule. {@link StellaSpawner} calls it once per cycle with the players it services.
 * A rule owns its own limits and timers, and reads time from {@code clock}, never from the level clock,
 * which barely advances under the game test runner.
 */
public interface SpawnRule {
	/** A short name for the log. */
	String name();

	/** Runs one cycle for one level. {@code clock} counts the spawner's level ticks. */
	void cycle(ServerLevel level, List<ServerPlayer> players, long clock);

	/** Drops every timer and record, when the server stops. */
	void reset();
}
