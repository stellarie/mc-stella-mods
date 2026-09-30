package com.stella.tower;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared fixtures: a player that can change dimension, a frame, the tower level, and the summary line. */
final class TowerTestSupport {
	static final Logger LOG = LoggerFactory.getLogger("mod-stella-tower-test");

	private TowerTestSupport() {
	}

	/** A player joined through the player list, so a teleport to another dimension carries them. */
	static ServerPlayer join(final GameTestHelper helper, final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "climber"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	/**
	 * Lets a test player be hurt. A player with no client is still counted as changing dimension and as not
	 * yet loaded, and either makes them invulnerable; a real client clears both within moments.
	 */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	static ServerLevel tower(final GameTestHelper helper) {
		ServerLevel tower = helper.getLevel().getServer().getLevel(TowerTravel.TOWER);
		helper.assertTrue(tower != null, "the tower dimension is not loaded");
		return tower;
	}

	/**
	 * A frame two wide and three high inside, standing along x, at relative (1..4, 1..5, z). Returns the
	 * inside's bottom-left spot.
	 */
	static BlockPos frame(final GameTestHelper helper, final int z, final net.minecraft.world.level.block.Block block) {
		for (int x = 1; x <= 4; x++) {
			for (int y = 1; y <= 5; y++) {
				boolean edge = x == 1 || x == 4 || y == 1 || y == 5;
				helper.setBlock(x, y, z, edge ? block : Blocks.AIR);
			}
		}
		return new BlockPos(2, 2, z);
	}

	/**
	 * Floors no earlier run has built: the test world keeps its floors, so a reused number proves nothing.
	 * A run takes about nine thousand floors and lasts about three minutes, so the start must move on faster
	 * than fifty floors a second, or a run started right after another begins inside the floors it built.
	 * It moves on eighty a second. The clock wraps every 300 thousand seconds, which keeps every floor inside
	 * the world border.
	 */
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_FLOOR =
		new java.util.concurrent.atomic.AtomicInteger(100_000 + (int) ((System.currentTimeMillis() / 1000L) % 300_000L) * 80);
	/** Overworld areas no earlier run has rolled, counted apart from the floors. */
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_AREA =
		new java.util.concurrent.atomic.AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 1_000_000L) * 8);

	/** An area number unique to this run. */
	static int freshArea() {
		return NEXT_AREA.getAndIncrement();
	}

	/** A floor number unique to this run, with the floor above it free as well. */
	static int freshFloor() {
		return NEXT_FLOOR.getAndAdd(4);
	}

	/** The first of a run of floors no earlier run has built, taken whole: size floors from the number returned. */
	static int freshBlock(final int size) {
		return NEXT_FLOOR.getAndAdd(size);
	}

	/** A fresh floor number that passes the test, such as an elite or a boss floor. */
	static int freshFloorWhere(final java.util.function.IntPredicate wanted) {
		return freshFloorWhere(wanted, 40);
	}

	/**
	 * A fresh floor number that passes the test, from a block of floors taken whole, so any residue of the
	 * block's size is found, with the two floors above it free too. A trap theme needs a block of a hundred
	 * and more.
	 */
	static int freshFloorWhere(final java.util.function.IntPredicate wanted, final int block) {
		int start = NEXT_FLOOR.getAndAdd(block);
		for (int floor = start; floor < start + block - 3; floor++) {
			if (wanted.test(floor)) {
				return floor;
			}
		}
		throw new IllegalStateException("no floor in " + start + ".." + (start + block - 4) + " fits");
	}

	/** Puts a joined player on a floor's arrival spot. */
	static ServerPlayer onFloor(final GameTestHelper helper, final ServerLevel tower, final int floor) {
		ServerPlayer player = join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	/**
	 * The nearest open spot of a floor's room at least this far from its arrival, for a test that stands
	 * something away from the player. The arrival moves from floor to floor, so no fixed offset from it is
	 * safe: one can land in a wall or outside the room.
	 */
	static Vec3 awayFromArrival(final ServerLevel tower, final int floor, final double distance) {
		Vec3 spot = openSpotAway(tower, floor, distance);
		if (spot == null) {
			throw new IllegalStateException("floor " + floor + " has no open spot " + distance + " from its arrival");
		}
		return spot;
	}

	/**
	 * The open spot farthest from the arrival, up to this far, for a test that needs distance but not an exact
	 * one. A maze floor can have no open spot at the full distance, so the search steps in until one fits.
	 */
	static Vec3 awayFromArrivalUpTo(final ServerLevel tower, final int floor, final double distance) {
		for (double reach = distance; reach >= 2.0; reach -= 2.0) {
			Vec3 spot = openSpotAway(tower, floor, reach);
			if (spot != null) {
				return spot;
			}
		}
		throw new IllegalStateException("floor " + floor + " has no open spot away from its arrival");
	}

	private static Vec3 openSpotAway(final ServerLevel tower, final int floor, final double distance) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		net.minecraft.core.BlockPos origin = plan.origin();
		double best = Double.MAX_VALUE;
		Vec3 spot = null;
		for (int x = 2; x < TowerLayout.SIZE - 2; x++) {
			for (int z = 2; z < TowerLayout.SIZE - 2; z++) {
				double away = Math.hypot(x - plan.arrivalX(), z - plan.arrivalZ());
				if (roomAround(plan, x, z, 2) && !plan.inSafeZone(x, z) && away >= distance && away < best) {
					best = away;
					spot = new Vec3(origin.getX() + x + 0.5, TowerLayout.BASE_Y, origin.getZ() + z + 0.5);
				}
			}
		}
		return spot;
	}

	/** Whether every spot within this many blocks of (x, z) is open in the plan, so a neighbour can stand beside it. */
	private static boolean roomAround(final TowerPlan plan, final int x, final int z, final int reach) {
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (plan.wall(x + dx, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Marks the raised portal's way open in a room's open-ground map: a boss floor's pyramid, whose steps are walked,
	 * and the portal's own columns, the goal of a walk, which stand on wall.
	 */
	static void openTheRaisedWay(final TowerPlan plan, final boolean[][] open) {
		BlockPos o = plan.origin();
		for (BlockPos wall : plan.plinth()) {
			int x = wall.getX() - o.getX();
			int z = wall.getZ() - o.getZ();
			if (wall.getY() >= TowerLayout.BASE_Y && !plan.isFramePost(x, z)) {
				open[x][z] = true;
			}
		}
		for (BlockPos inside : plan.portalInside()) {
			open[inside.getX() - o.getX()][inside.getZ() - o.getZ()] = true;
		}
	}

	/** Holds the chunks around a spot loaded, so what spawns there is seen. A portal ticket lapses after 300 ticks. */
	static void holdRoom(final ServerLevel tower, final Vec3 at) {
		tower.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL,
			net.minecraft.world.level.ChunkPos.containing(BlockPos.containing(at)), 2);
	}

	static boolean roomLoaded(final ServerLevel tower, final Vec3 at) {
		return tower.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(BlockPos.containing(at)));
	}

	static int cheb(final TowerMaze.Zone zone, final int x, final int z) {
		int dx = Math.max(Math.max(zone.x() - x, 0), x - (zone.x() + TowerMaze.SAFE - 1));
		int dz = Math.max(Math.max(zone.z() - z, 0), z - (zone.z() + TowerMaze.SAFE - 1));
		return Math.max(dx, dz);
	}

	/**
	 * Whether the line from one spot to another runs over open ground the whole way, and so do two lines a third of a block to
	 * either side of it: a body is wider than a line, and one that grazes a corner sees past it on paper only.
	 */
	private static boolean clear(final TowerPlan plan, final double ax, final double az, final double bx, final double bz) {
		double length = Math.hypot(bx - ax, bz - az);
		double px = length == 0.0 ? 0.0 : -(bz - az) / length * 0.35;
		double pz = length == 0.0 ? 0.0 : (bx - ax) / length * 0.35;
		int steps = (int) Math.ceil(length * 4.0);
		for (int i = 0; i <= steps; i++) {
			double t = steps == 0 ? 0.0 : i / (double) steps;
			for (int side = -1; side <= 1; side++) {
				if (plan.wall((int) Math.floor(ax + (bx - ax) * t + px * side), (int) Math.floor(az + (bz - az) * t + pz * side))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Where a mob stands just beside the entrance zone, where a player stands inside it, and where outside near the mob. */
	record ZoneSpots(Vec3 mob, Vec3 inside, Vec3 outside) {
	}

	/**
	 * Spots for a mob just outside the plan's entrance zone (one open cell between them, in plain sight of the zone), a
	 * player inside it, and a player outside it near the mob. A tower mob put inside a zone is sent out of it within a
	 * sweep, so a test that needs a mob to stay stands it here.
	 */
	static ZoneSpots spotsBesideZone(final TowerPlan plan) {
		return spotsBesideZone(plan, 0);
	}

	/**
	 * The same for any of the plan's zones: 0 is the entrance zone, 1 the boss floor's walled room. Beside the room
	 * means outside its ring, in sight of the inside through an opening.
	 */
	static ZoneSpots spotsBesideZone(final TowerPlan plan, final int zoneIndex) {
		TowerMaze.Zone zone = plan.safeZones().get(zoneIndex);
		BlockPos o = plan.origin();
		// Room to spare where the floor has it; a variation with paths two wide has none round a spot, so a single open block will do.
		for (int reach = 1; reach >= 0; reach--) {
			for (int beside = 2; beside <= 4; beside++) {
				for (int x = 2; x < TowerLayout.SIZE - 2; x++) {
					for (int z = 2; z < TowerLayout.SIZE - 2; z++) {
						if (cheb(zone, x, z) != beside || !roomAround(plan, x, z, reach)) {
							continue;
						}
						int ix = Math.max(zone.x() + 1, Math.min(zone.x() + TowerMaze.SAFE - 2, x));
						int iz = Math.max(zone.z() + 1, Math.min(zone.z() + TowerMaze.SAFE - 2, z));
						if (plan.wall(ix, iz) || !clear(plan, x + 0.5, z + 0.5, ix + 0.5, iz + 0.5)) {
							continue;
						}
						for (int ox = 2; ox < TowerLayout.SIZE - 2; ox++) {
							for (int oz = 2; oz < TowerLayout.SIZE - 2; oz++) {
								int apart = Math.max(Math.abs(ox - x), Math.abs(oz - z));
								if (apart >= 3 && apart <= 5 && cheb(zone, ox, oz) >= 2 && roomAround(plan, ox, oz, reach) && clear(plan, x + 0.5, z + 0.5, ox + 0.5, oz + 0.5)) {
									return new ZoneSpots(new Vec3(o.getX() + x + 0.5, TowerLayout.BASE_Y, o.getZ() + z + 0.5),
										new Vec3(o.getX() + ix + 0.5, TowerLayout.BASE_Y, o.getZ() + iz + 0.5),
										new Vec3(o.getX() + ox + 0.5, TowerLayout.BASE_Y, o.getZ() + oz + 0.5));
								}
							}
						}
					}
				}
			}
		}
		throw new IllegalStateException("no spot beside the zone of floor " + plan.floor());
	}

	static void result(final String test, final String outcome) {
		LOG.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
