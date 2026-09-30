package com.stella.rpg;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RpgTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("mod-stella-rpg-test");

	private RpgTestSupport() {
	}

	static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().invulnerable = false;
		return player;
	}

	static ServerPlayer realPlayer(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "rpg-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	static void bare(final LivingEntity entity) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			entity.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	static void setStat(final ServerPlayer player, final Stat stat, final int value) {
		StatPoints.setSheet(player, StatPoints.sheet(player).with(stat, value));
	}

	static float mobGuard(final float damage) {
		return (float) (damage * RpgCurve.damageTakenMultiplier(MobStats.MINIMUM));
	}

	static void result(final String test, final String detail) {
		LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
