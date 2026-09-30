package com.stella.mobs.entity.spawn;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The custom spawner: one level tick hook that runs every registered {@link SpawnRule}. It places only
 * mobs whose vanilla rules cannot say what the rule needs, always with a non-natural spawn reason and a
 * category outside the mob cap. The white and black slimes do not come through here: they use the
 * vanilla natural spawner and its biome list.
 *
 * <p>The spawner keeps its own tick count per level. The game test level clock barely advances, so a
 * timer keyed on it would never fire.
 */
public final class StellaSpawner {
	/** A cycle runs once every this many level ticks. */
	public static final int CYCLE_INTERVAL_TICKS = 20;
	/**
	 * The test runner sets this to false, so its other tests never gain a stray spawn. Play leaves it on.
	 * A test drives {@link #runCycle} itself and reads the same rule code the tick hook reaches.
	 */
	public static final String AUTO_PROPERTY = "stella.mobs.spawnerAuto";

	private static final List<SpawnRule> RULES = new ArrayList<>();
	private static final Map<ResourceKey<Level>, long[]> CLOCKS = new HashMap<>();
	private static volatile Predicate<ServerPlayer> playerFilter = player -> !"false".equals(System.getProperty(AUTO_PROPERTY));

	private StellaSpawner() {
	}

	public static void register() {
		BlueSlimeSpawnRule.registerEvents();
		addRule(BlueSlimeSpawnRule.INSTANCE);
		addRule(SkeletonWorkerSpawnRule.INSTANCE);
		addRule(GhostSpawnRule.INSTANCE);
		ServerTickEvents.END_LEVEL_TICK.register(StellaSpawner::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLOCKS.clear();
			for (SpawnRule rule : RULES) {
				rule.reset();
			}
		});
	}

	/** The hook for a later rule. Adding the same rule twice is ignored. */
	public static void addRule(final SpawnRule rule) {
		if (!RULES.contains(rule)) {
			RULES.add(rule);
		}
	}

	public static List<SpawnRule> rules() {
		return List.copyOf(RULES);
	}

	/** The players a tick cycle services. A test narrows it to its own player and restores the old filter. */
	public static Predicate<ServerPlayer> swapPlayerFilter(final Predicate<ServerPlayer> filter) {
		Predicate<ServerPlayer> previous = playerFilter;
		playerFilter = filter;
		return previous;
	}

	/** This level's spawner tick count. */
	public static long clock(final ServerLevel level) {
		long[] clock = CLOCKS.get(level.dimension());
		return clock == null ? 0L : clock[0];
	}

	private static void tick(final ServerLevel level) {
		long[] clock = CLOCKS.computeIfAbsent(level.dimension(), key -> new long[1]);
		clock[0]++;
		if (clock[0] % CYCLE_INTERVAL_TICKS != 0L) {
			return;
		}

		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && playerFilter.test(player)) {
				players.add(player);
			}
		}
		if (!players.isEmpty()) {
			runCycle(level, players, clock[0]);
		}
	}

	/** Runs every rule once. The tick hook and a test both call this. */
	public static void runCycle(final ServerLevel level, final List<ServerPlayer> players, final long clock) {
		for (SpawnRule rule : RULES) {
			rule.cycle(level, players, clock);
		}
	}
}
