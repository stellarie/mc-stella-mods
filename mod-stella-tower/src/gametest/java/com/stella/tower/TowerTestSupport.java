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
	 * Runs start a minute and more apart, and a run takes some two thousand floors, so the start moves on
	 * forty floors a second; forty million floors still lie well inside the world border.
	 */
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_FLOOR =
		new java.util.concurrent.atomic.AtomicInteger(100_000 + (int) ((System.currentTimeMillis() / 1000L) % 1_000_000L) * 40);
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
		if (spot == null) {
			throw new IllegalStateException("floor " + floor + " has no open spot " + distance + " from its arrival");
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

	/** Holds the chunks around a spot loaded, so what spawns there is seen. A portal ticket lapses after 300 ticks. */
	static void holdRoom(final ServerLevel tower, final Vec3 at) {
		tower.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL,
			net.minecraft.world.level.ChunkPos.containing(BlockPos.containing(at)), 2);
	}

	static boolean roomLoaded(final ServerLevel tower, final Vec3 at) {
		return tower.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(BlockPos.containing(at)));
	}

	static void result(final String test, final String outcome) {
		LOG.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
