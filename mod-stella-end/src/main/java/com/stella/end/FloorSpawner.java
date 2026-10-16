package com.stella.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Keeps each floor near a player stocked with hostile mobs. The cap counts a recorded identity set that load and unload
 * events keep, not a box query, so a neighbour's mob never counts and a despawned one stops counting at once.
 */
public final class FloorSpawner {
	/** Hostiles a floor holds. The floor witch counts outside this. */
	public static final int CAP = 24;
	/** Server ticks between two top-ups. */
	public static final int INTERVAL = 40;
	/** Server ticks between two enderman retargets. */
	public static final int RETARGET_INTERVAL = 100;
	/** A floor works only while a player stands within this many blocks of it. */
	public static final int ACTIVE_RANGE = 48;
	/** An enderman hunts the nearest player within this many blocks. */
	public static final double HUNT_RANGE = 16.0;
	/** A spawn keeps this far from every player. */
	public static final double SAFE_DISTANCE = 10.0;
	/** Mobs one floor gains in one top-up. */
	public static final int BATCH = 4;
	/** The highest floor with a spawner: floor 6 belongs to the boss, which makes its own mobs. */
	public static final int TOP_FLOOR = EndCastle.FLOORS - 1;
	private static final int ATTEMPTS = 16;

	private static final Map<Integer, Set<UUID>> HOSTILES = new HashMap<>();
	private static final Map<Integer, Set<UUID>> WITCHES = new HashMap<>();
	private static int ticks;

	private FloorSpawner() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> record(entity, level, true));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> record(entity, level, false));
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension().equals(Level.END)) {
				tick(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	/** The live hostiles the spawner recorded on a floor. */
	public static List<Mob> hostiles(final ServerLevel end, final int floor) {
		return live(end, HOSTILES.get(floor));
	}

	/** The floor's live witch, or null. */
	public static @Nullable Mob witch(final ServerLevel end, final int floor) {
		List<Mob> witches = live(end, WITCHES.get(floor));
		return witches.isEmpty() ? null : witches.get(0);
	}

	/** Forgets every record and restarts the clocks. A server stop does this; a test does it to start clean. */
	public static void reset() {
		HOSTILES.clear();
		WITCHES.clear();
		ticks = 0;
	}

	/** Whether a floor works now: a player lives within {@link #ACTIVE_RANGE} blocks of its volume. */
	static boolean active(final ServerLevel end, final int floor) {
		double minY = EndCastle.slabY(floor);
		double maxY = minY + EndCastle.FLOOR_HEIGHT;
		for (ServerPlayer player : end.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			double dx = gap(player.getX(), EndCastle.MIN_X, EndCastle.MIN_X + EndCastle.SIZE);
			double dy = gap(player.getY(), minY, maxY);
			double dz = gap(player.getZ(), EndCastle.MIN_Z, EndCastle.MIN_Z + EndCastle.SIZE);
			if (dx * dx + dy * dy + dz * dz <= (double) ACTIVE_RANGE * ACTIVE_RANGE) {
				return true;
			}
		}
		return false;
	}

	private static double gap(final double value, final double low, final double high) {
		return value < low ? low - value : value > high ? value - high : 0.0;
	}

	private static void record(final Entity entity, final ServerLevel level, final boolean loaded) {
		int floor = EndMobs.floorOf(entity);
		if (floor == 0 || !(entity instanceof Mob) || !level.dimension().equals(Level.END)) {
			return;
		}
		Map<Integer, Set<UUID>> book = EndMobs.isWitch(entity) ? WITCHES : HOSTILES;
		if (loaded) {
			book.computeIfAbsent(floor, key -> new LinkedHashSet<>()).add(entity.getUUID());
		} else if (book.containsKey(floor)) {
			book.get(floor).remove(entity.getUUID());
		}
	}

	private static List<Mob> live(final ServerLevel end, final @Nullable Set<UUID> ids) {
		List<Mob> mobs = new ArrayList<>();
		if (ids != null) {
			for (UUID id : ids) {
				if (end.getEntity(id) instanceof Mob mob && mob.isAlive()) {
					mobs.add(mob);
				}
			}
		}
		return mobs;
	}

	// -- The clock -------------------------------------------------------------------------------------------------

	/** Counts the level's own ticks: the game clock barely moves in a test. */
	private static void tick(final ServerLevel end) {
		if (end.players().isEmpty()) {
			return;
		}
		ticks++;
		if (ticks % INTERVAL == 0) {
			topUp(end);
		}
		if (ticks % RETARGET_INTERVAL == 0) {
			retarget(end);
		}
	}

	private static void topUp(final ServerLevel end) {
		// Mobs of a chunk that has not loaded yet are not on the record, and a top-up now would overshoot the cap.
		if (!EndCastles.state(end).built() || !castleLoaded(end)) {
			return;
		}
		CastleLayout layout = EndCastles.layout(end);
		for (int floor = 1; floor <= TOP_FLOOR; floor++) {
			if (!active(end, floor)) {
				continue;
			}
			CastleLayout.FloorPlan plan = layout.floor(floor);
			if (witch(end, floor) == null) {
				BlockPos spot = spot(end, floor, plan, 2);
				if (spot != null) {
					EndMobs.spawnWitch(end, floor, spot);
				}
			}
			int have = hostiles(end, floor).size();
			int missing = Math.min(BATCH, CAP - have);
			int spawned = 0;
			for (int i = 0; i < missing; i++) {
				EndMobs.Kind kind = EndMobs.pick(floor, end.getRandom().nextInt(EndMobs.ROLLS));
				BlockPos spot = spot(end, floor, plan, height(kind));
				if (spot != null && EndMobs.spawn(end, floor, kind, spot) != null) {
					spawned++;
				}
			}
			if (missing > 0) {
				BehaviorLog.info("[END] topUp floor={} had={} wanted={} spawned={} nowRecorded={}", floor, have, missing, spawned, hostiles(end, floor).size());
			}
		}
	}

	private static boolean castleLoaded(final ServerLevel end) {
		for (int cx = EndCastle.MIN_X >> 4; cx <= (EndCastle.MIN_X + EndCastle.SIZE - 1) >> 4; cx++) {
			for (int cz = EndCastle.MIN_Z >> 4; cz <= (EndCastle.MIN_Z + EndCastle.SIZE - 1) >> 4; cz++) {
				if (!end.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void retarget(final ServerLevel end) {
		for (int floor = 1; floor <= TOP_FLOOR; floor++) {
			for (Mob mob : hostiles(end, floor)) {
				if (mob instanceof Enderman enderman) {
					EndMobs.hunt(end, enderman);
				}
			}
		}
	}

	/** Blocks of clear air a kind needs above its feet. */
	private static int height(final EndMobs.Kind kind) {
		return switch (kind) {
			case WITHER_SKELETON -> 2;
			case ENDERMAN -> 3;
			case SHULKER -> 1;
		};
	}

	/** A random spawn cell of the plan with solid ground, clear air, and every player far enough away; null if none turns up. */
	private static @Nullable BlockPos spot(final ServerLevel end, final int floor, final CastleLayout.FloorPlan plan, final int clearance) {
		List<int[]> cells = plan.spawnCells();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			int[] cell = cells.get(end.getRandom().nextInt(cells.size()));
			BlockPos feet = new BlockPos(EndCastle.MIN_X + cell[0], EndCastle.standY(floor), EndCastle.MIN_Z + cell[1]);
			if (end.hasChunkAt(feet) && standable(end, feet, clearance) && !nearAPlayer(end, feet)) {
				return feet;
			}
		}
		return null;
	}

	private static boolean standable(final ServerLevel end, final BlockPos feet, final int clearance) {
		if (!end.getBlockState(feet.below()).isFaceSturdy(end, feet.below(), Direction.UP)) {
			return false;
		}
		for (int i = 0; i < clearance; i++) {
			if (!end.getBlockState(feet.above(i)).isAir()) {
				return false;
			}
		}
		return true;
	}

	private static boolean nearAPlayer(final ServerLevel end, final BlockPos feet) {
		for (ServerPlayer player : end.players()) {
			if (player.distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5) < SAFE_DISTANCE * SAFE_DISTANCE) {
				return true;
			}
		}
		return false;
	}
}
