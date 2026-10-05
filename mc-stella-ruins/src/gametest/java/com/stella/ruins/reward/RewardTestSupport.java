package com.stella.ruins.reward;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared fixtures: a joined player, a still target, and the log of what the probe effect saw. */
final class RewardTestSupport {
	static final Logger LOG = LoggerFactory.getLogger("mod-stella-ruins-test");

	private static final Map<UUID, List<String>> SEEN = new ConcurrentHashMap<>();

	private RewardTestSupport() {
	}

	/** A player joined through the player list and ticked every tick, so it behaves like a real one. */
	static ServerPlayer join(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "reward-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		// The packet listener ticks a real player. This one has no listener, so the test does it.
		helper.onEachTick(() -> {
			if (!player.isRemoved()) {
				player.doTick();
			}
		});
		return player;
	}

	/** A player with no client counts as still loading, and that makes them invulnerable. */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** A cow stands still and sunlight does not hurt it, so only the test deals damage. */
	static Cow cow(final GameTestHelper helper, final double x, final double z, final float health) {
		ServerLevel level = helper.getLevel();
		Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
		// The mobs mod would scale this cow by its distance from the origin; the tag keeps the subject at its own health.
		cow.addTag(com.stella.ruins.world.RuinMobKind.NO_SPAWN_ROLL_TAG);
		cow.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		cow.setNoAi(true);
		cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.max(health, 20.0F));
		cow.setHealth(health);
		level.addFreshEntity(cow);
		return cow;
	}

	static void record(final UUID user, final String line) {
		SEEN.computeIfAbsent(user, id -> new CopyOnWriteArrayList<>()).add(line);
	}

	/** What the probe effect saw for one user, in order. */
	static List<String> seen(final UUID user) {
		return List.copyOf(SEEN.getOrDefault(user, List.of()));
	}

	static long count(final UUID user, final String kind) {
		return seen(user).stream().filter(line -> line.startsWith(kind + ":")).count();
	}

	static void result(final String test, final String outcome) {
		LOG.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
