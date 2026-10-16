package com.stella.end;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Shared fixtures: the End level, a clean start, the castle held in memory, a player inside it, the summary line. */
final class EndTestSupport {
	private EndTestSupport() {
	}

	static ServerLevel end(final GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		helper.assertTrue(end != null, "the End is not loaded");
		return end;
	}

	/**
	 * Starts a test from nothing: any player an earlier test left in the End goes, every castle mob goes, the spawner
	 * forgets its records, the castle stands and its chunks stay loaded. The End and its castle are shared by every test.
	 */
	static ServerLevel fresh(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = end(helper);
		for (ServerPlayer leftover : List.copyOf(end.players())) {
			server.getPlayerList().remove(leftover);
		}
		clearMobs(end);
		FloorSpawner.reset();
		EndCastles.ensureBuilt(end);
		hold(end, EndCastle.min());
		return end;
	}

	/**
	 * Removes every castle mob, and the vanilla dragon the fight makes once a player stands in the End: it flies
	 * through the castle and would hurt mobs and test players in any test that runs after one that joined a player.
	 */
	static void clearMobs(final ServerLevel end) {
		for (Entity entity : List.copyOf(iterate(end))) {
			if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
				|| entity.entityTags().stream().anyMatch(tag -> tag.startsWith(EndMobs.FLOOR_TAG))) {
				entity.discard();
			}
		}
	}

	static List<Entity> entities(final ServerLevel end) {
		return iterate(end);
	}

	private static List<Entity> iterate(final ServerLevel end) {
		java.util.ArrayList<Entity> all = new java.util.ArrayList<>();
		end.getAllEntities().forEach(all::add);
		return all;
	}

	/** Loads and keeps the four chunks of a castle at a footprint corner: a test player does not move chunk tickets as a real one does. */
	static void hold(final ServerLevel level, final BlockPos min) {
		int x0 = min.getX() >> 4;
		int z0 = min.getZ() >> 4;
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				level.getChunk(x0 + dx, z0 + dz);
				level.setChunkForced(x0 + dx, z0 + dz, true);
			}
		}
		for (int i = 0; i < 1000 && level.getChunkSource().pollTask(); i++) {
		}
	}

	static void release(final ServerLevel level, final BlockPos min) {
		int x0 = min.getX() >> 4;
		int z0 = min.getZ() >> 4;
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				level.setChunkForced(x0 + dx, z0 + dz, false);
			}
		}
	}

	/** A player joined through the player list and sent to a spot of a level, so every system that counts players sees them. */
	static ServerPlayer join(final GameTestHelper helper, final ServerLevel level, final Vec3 at) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "climber"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleport(new TeleportTransition(level, at, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		vulnerable(player);
		// Castle mobs hit hard, and a dead player stops every floor near them. The player takes no damage but stays a
		// target: an invulnerable player is dropped by every mob's target goals.
		player.addTag(EndGameTestInit.TEST_PLAYER_TAG);
		return player;
	}

	/**
	 * Lets a test player be hurt and hunted. A player with no client counts as changing dimension and as not yet
	 * loaded, and either makes them invulnerable; a real client clears both within moments.
	 */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	/** The world position of a local castle cell at an absolute y. */
	static BlockPos world(final BlockPos min, final int lx, final int y, final int lz) {
		return new BlockPos(min.getX() + lx, y, min.getZ() + lz);
	}

	/** A player standing on the landing cell of a floor of the real castle. */
	static ServerPlayer joinOnFloor(final GameTestHelper helper, final ServerLevel end, final int floor) {
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		BlockPos at = world(EndCastle.min(), plan.landingX(), EndCastle.standY(floor), plan.landingZ());
		return join(helper, end, Vec3.atBottomCenterOf(at));
	}

	/** The spawn cell of a floor nearest to a local cell among those between two distances of it, at the floor's standing height. */
	static BlockPos cellNear(final GameTestHelper helper, final CastleLayout.FloorPlan plan, final int floor, final int ox, final int oz, final double min, final double max) {
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int[] cell : plan.spawnCells()) {
			double distance = Math.hypot(cell[0] - ox, cell[1] - oz);
			if (distance >= min && distance <= max && distance < bestDistance) {
				best = cell;
				bestDistance = distance;
			}
		}
		helper.assertTrue(best != null, "floor " + floor + " has no spawn cell " + min + " to " + max + " from " + ox + "," + oz);
		return world(EndCastle.min(), best[0], EndCastle.standY(floor), best[1]);
	}

	static BlockState state(final ServerLevel level, final BlockPos min, final int lx, final int y, final int lz) {
		return level.getBlockState(world(min, lx, y, lz));
	}

	static void result(final String test, final String outcome) {
		StellaEnd.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
