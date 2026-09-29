package com.stella.learn;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A flat night floor, a survival player a mob can legally target, and a mob that keeps its target. */
final class LearnArena {
	static final long NIGHT_TIME = 18000L;
	static final int SPAWN_Y = 3;
	static final float PLAYER_HEALTH = 10000.0F;
	private static final int SIZE = 48;
	/** Space between tests: a floor this wide overlaps its neighbours otherwise, and a mob targets the wrong test's player. */
	static final int PADDING = 64;
	/** Ticks a test waits for its chunks before it runs anyway and fails loudly. */
	static final int READY_TICKS = 300;

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

	/**
	 * Runs `then` once each watched entity stands in an entity-ticking chunk. A chunk forced this tick joins some ticks
	 * later, longer while a whole batch of tests forces its own, and a query for entities in it finds none until then, so
	 * a callout sent at tick 0 can reach nobody.
	 */
	static void whenReady(final GameTestHelper helper, final java.util.List<? extends net.minecraft.world.entity.Entity> watched, final Runnable then) {
		poll(helper, watched, then, READY_TICKS);
	}

	private static void poll(
		final GameTestHelper helper, final java.util.List<? extends net.minecraft.world.entity.Entity> watched, final Runnable then, final int attemptsLeft
	) {
		if (attemptsLeft <= 0 || watched.stream().allMatch(entity -> entity.isAlive() && helper.getLevel().isPositionEntityTicking(entity.blockPosition()))) {
			then.run();
			return;
		}

		helper.runAfterDelay(1L, () -> poll(helper, watched, then, attemptsLeft - 1));
	}

	/** Checks `condition` each tick until it holds, then runs `onSuccess`; past `maxTicks` it fails with `message`. */
	static void pollUntil(
		final GameTestHelper helper, final java.util.function.BooleanSupplier condition, final int maxTicks,
		final Runnable onSuccess, final java.util.function.Supplier<String> message
	) {
		if (condition.getAsBoolean()) {
			onSuccess.run();
		} else if (maxTicks <= 0) {
			helper.fail(message.get());
		} else {
			helper.runAfterDelay(1L, () -> pollUntil(helper, condition, maxTicks - 1, onSuccess, message));
		}
	}

	/** Raises a shield in the player's off hand and keeps it raised, since the mock player never ticks its own item use. */
	static void raiseShield(final ServerPlayer player) {
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.startUsingItem(InteractionHand.OFF_HAND);
	}

	static void lowerShield(final ServerPlayer player) {
		player.stopUsingItem();
	}

	/** Sets the mob's target every tick, as a fight would, and lifts the player's damage immunity. */
	static void keepTargeting(final GameTestHelper helper, final Mob mob, final ServerPlayer player) {
		helper.onEachTick(() -> {
			player.setInvulnerableTime(0);
			mob.setTarget(player);
		});
	}
}
