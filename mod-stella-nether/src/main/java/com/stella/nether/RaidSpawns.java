package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The raid's own spawns, beside the nether's: in either mode a blaze rises out of lava near a player, and in
 * hard a wither skeleton turns up anywhere near a player with room to stand, not only in a fortress. Each
 * player is tried every {@link #SPAWN_EVERY_TICKS}, up to {@link #LOCAL_CAP} of a kind within {@link #CAP_RANGE}.
 */
public final class RaidSpawns {
	public static final int SPAWN_EVERY_TICKS = 40;
	/** A spawn lands this far from the player, never nearer. */
	public static final int MIN_DISTANCE = 16;
	public static final int MAX_DISTANCE = 40;
	/** Columns tried per attempt, and how far up and down each is searched from the player's height. */
	public static final int COLUMN_TRIES = 12;
	public static final int SEARCH_UP = 16;
	public static final int SEARCH_DOWN = 40;
	public static final int LOCAL_CAP = 8;
	public static final double CAP_RANGE = 64.0;

	private RaidSpawns() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RaidSpawns::tick);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SPAWN_EVERY_TICKS != 0) {
			return;
		}
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		if (raid == null) {
			return;
		}
		for (ServerPlayer player : List.copyOf(raid.players())) {
			// Each player's spawns follow the raid they stand in.
			RaidState state = RaidInstance.stateAt(raid, player.blockPosition());
			if (state.active() && !player.isSpectator()) {
				attempt(raid, player.blockPosition(), state.mode(), raid.getRandom());
			}
		}
	}

	/** One attempt around a spot: a blaze from lava, and in hard a wither skeleton. What it spawned, by identity. */
	public static List<Mob> attempt(final ServerLevel raid, final BlockPos around, final RaidMode mode, final RandomSource random) {
		List<Mob> spawned = new ArrayList<>();
		if (below(raid, around, EntityTypes.BLAZE)) {
			for (int i = 0; i < COLUMN_TRIES; i++) {
				Mob blaze = blazeAt(raid, column(around, random));
				if (blaze != null) {
					spawned.add(blaze);
					break;
				}
			}
		}
		if (spawnsWithers(mode) && below(raid, around, EntityTypes.WITHER_SKELETON)) {
			for (int i = 0; i < COLUMN_TRIES; i++) {
				Mob skeleton = witherAt(raid, column(around, random));
				if (skeleton != null) {
					spawned.add(skeleton);
					break;
				}
			}
		}
		return spawned;
	}

	public static boolean spawnsWithers(final RaidMode mode) {
		return mode == RaidMode.HARD;
	}

	/** A blaze out of the highest lava surface in this column, open air above it; nothing if the column holds none. */
	public static @Nullable Mob blazeAt(final ServerLevel raid, final BlockPos column) {
		return blazeAt(raid, column, SEARCH_UP, SEARCH_DOWN);
	}

	static @Nullable Mob blazeAt(final ServerLevel raid, final BlockPos column, final int up, final int down) {
		for (int dy = up; dy >= -down; dy--) {
			BlockPos lava = column.above(dy);
			if (raid.isLoaded(lava) && raid.getFluidState(lava).is(FluidTags.LAVA) && raid.getFluidState(lava).isSource()
				&& open(raid, lava.above(), 2)) {
				return EntityTypes.BLAZE.spawn(raid, lava.above(), EntitySpawnReason.NATURAL);
			}
		}
		return null;
	}

	/** A wither skeleton on the highest sturdy, dry floor in this column with three blocks of room. */
	public static @Nullable Mob witherAt(final ServerLevel raid, final BlockPos column) {
		return witherAt(raid, column, SEARCH_UP, SEARCH_DOWN);
	}

	static @Nullable Mob witherAt(final ServerLevel raid, final BlockPos column, final int up, final int down) {
		for (int dy = up; dy >= -down; dy--) {
			BlockPos floor = column.above(dy);
			if (raid.isLoaded(floor) && raid.getBlockState(floor).isFaceSturdy(raid, floor, Direction.UP) && open(raid, floor.above(), 3)) {
				return EntityTypes.WITHER_SKELETON.spawn(raid, floor.above(), EntitySpawnReason.NATURAL);
			}
		}
		return null;
	}

	private static boolean open(final ServerLevel raid, final BlockPos from, final int height) {
		for (int up = 0; up < height; up++) {
			BlockPos at = from.above(up);
			if (!raid.getBlockState(at).getCollisionShape(raid, at).isEmpty() || !raid.getFluidState(at).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static BlockPos column(final BlockPos around, final RandomSource random) {
		double angle = random.nextDouble() * 2.0 * Math.PI;
		double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
		return around.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
	}

	private static boolean below(final ServerLevel raid, final BlockPos around, final EntityType<?> type) {
		return raid.getEntities(type, new AABB(around).inflate(CAP_RANGE), entity -> entity.isAlive()).size() < LOCAL_CAP;
	}
}
