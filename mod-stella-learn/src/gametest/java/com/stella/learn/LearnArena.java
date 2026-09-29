package com.stella.learn;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A flat night floor, a survival player a mob can legally target, and a mob that keeps its target. */
final class LearnArena {
	static final long NIGHT_TIME = 18000L;
	static final int SPAWN_Y = 3;
	static final float PLAYER_HEALTH = 10000.0F;
	private static final int SIZE = 48;

	private LearnArena() {
	}

	/** Builds the floor and holds every chunk it covers, since a mob in a chunk the test area does not reach never ticks. */
	static void floor(final GameTestHelper helper) {
		helper.setTime(NIGHT_TIME);
		BlockPos low = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos high = helper.absolutePos(new BlockPos(SIZE, 0, SIZE));
		for (int chunkX = low.getX() >> 4; chunkX <= high.getX() >> 4; chunkX++) {
			for (int chunkZ = low.getZ() >> 4; chunkZ <= high.getZ() >> 4; chunkZ++) {
				int x = chunkX;
				int z = chunkZ;
				helper.getLevel().setChunkForced(x, z, true);
				helper.runBeforeTestEnd(() -> helper.getLevel().setChunkForced(x, z, false));
			}
		}

		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				// The two layers below fill the air pocket above the world's own ground, or a flee search lands in it.
				helper.setBlock(x, -2, z, Blocks.STONE);
				helper.setBlock(x, -1, z, Blocks.STONE);
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A survival player, since 26.3 drops an invulnerable one from target validation. It cannot die in a test. */
	static ServerPlayer player(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(PLAYER_HEALTH);
		player.setHealth(PLAYER_HEALTH);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	/** Sets the mob's target every tick, as a fight would, and lifts the player's damage immunity. */
	static void keepTargeting(final GameTestHelper helper, final Mob mob, final ServerPlayer player) {
		helper.onEachTick(() -> {
			player.setInvulnerableTime(0);
			mob.setTarget(player);
		});
	}
}
