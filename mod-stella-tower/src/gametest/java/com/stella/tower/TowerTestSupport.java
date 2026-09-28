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

	/** Floors no earlier run has built: the test world keeps its floors, so a reused number proves nothing. */
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_FLOOR =
		new java.util.concurrent.atomic.AtomicInteger(100_000 + (int) ((System.currentTimeMillis() / 1000L) % 1_000_000L) * 16);

	/** A floor number unique to this run, with the floor above it free as well. */
	static int freshFloor() {
		return NEXT_FLOOR.getAndAdd(4);
	}

	static void result(final String test, final String outcome) {
		LOG.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
