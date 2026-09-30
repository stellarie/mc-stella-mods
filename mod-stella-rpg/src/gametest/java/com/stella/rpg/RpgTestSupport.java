package com.stella.rpg;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import com.stella.rpg.roll.WeaponRolls;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
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
		// The mock holds a sword, and a weapon adds dice and a multiplier.
		bare(player);
		return player;
	}

	/** A joined player and the channel that collects every packet the server sends to it. */
	record Recorded(ServerPlayer player, EmbeddedChannel channel) {
	}

	static ServerPlayer realPlayer(final GameTestHelper helper) {
		return recordedPlayer(helper).player();
	}

	static Recorded recordedPlayer(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "rpg-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(at.x, at.y, at.z);
		return new Recorded(player, channel);
	}

	static void bare(final LivingEntity entity) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			entity.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	static void setStat(final ServerPlayer player, final Stat stat, final int value) {
		StatPoints.setSheet(player, StatPoints.sheet(player).with(stat, value));
	}

	/** The VIT guard and the INT magic defense of a threat-0 melee mob, for a magic hit. */
	static float mobMagicGuard(final float damage) {
		int intel = MobStats.base(RpgConfig.defaults(), MobStats.MELEE, Stat.INT, 0);
		return (float) (mobGuard(damage) * RpgCurve.magicDamageTakenMultiplier(intel));
	}

	/** The LUK share a threat-0 melee mob takes from a critical hit. */
	static float mobCritGuard() {
		int luk = MobStats.base(RpgConfig.defaults(), MobStats.MELEE, Stat.LUK, 0);
		return (float) RpgCurve.critDamageTakenMultiplier(luk);
	}

	static float mobGuard(final float damage) {
		// A threat-0 melee mob has its role's base VIT.
		int vit = MobStats.base(RpgConfig.defaults(), MobStats.MELEE, Stat.VIT, 0);
		return (float) (damage * RpgCurve.damageTakenMultiplier(vit));
	}

	/** A stack that holds only the given affixes, as a finished roll. */
	static ItemStack withAffixes(final Item item, final Map<String, Integer> affixes) {
		ItemStack stack = new ItemStack(item);
		stack.set(WeaponRolls.STAT_ROLLS, Map.of());
		stack.set(WeaponRolls.AFFIX_ROLLS, Map.copyOf(affixes));
		return stack;
	}

	/** Runs the action with the given share of roll slots landing on an affix. */
	static void withAffixChance(final double chance, final Runnable action) {
		try {
			RpgConfigStore.set(RpgConfigStore.parse("{\"weapon_rolls\":{\"affix_chance\":" + chance + "}}", message -> { }));
			action.run();
		} finally {
			RpgConfigStore.set(null);
		}
	}

	static void result(final String test, final String detail) {
		LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
