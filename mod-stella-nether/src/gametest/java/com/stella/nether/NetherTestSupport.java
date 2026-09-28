package com.stella.nether;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared fixtures: a player that can change dimension, the vanilla portal's destination, the raid level, the summary line. */
final class NetherTestSupport {
	private NetherTestSupport() {
	}

	/** A player joined through the player list, so a teleport to another dimension carries them. */
	static ServerPlayer join(final GameTestHelper helper, final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "raider"), false);
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

	/** Where a vanilla nether portal at a relative spot would send an entity standing in it now. */
	static @Nullable TeleportTransition portal(final ServerLevel level, final Entity entity, final BlockPos absolute) {
		return ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(level, entity, absolute);
	}

	static ServerLevel raid(final GameTestHelper helper) {
		ServerLevel raid = helper.getLevel().getServer().getLevel(RaidTravel.RAID);
		helper.assertTrue(raid != null, "the raid dimension is not loaded");
		return raid;
	}

	static void result(final String test, final String outcome) {
		StellaNether.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
