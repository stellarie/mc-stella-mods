package com.stella.end;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Switches the behaviour lines on for the tests, records where each castle mob loaded and how far the nearest player
 * stood, and clears the castle's mobs and held chunks before the world saves.
 */
public final class EndGameTestInit implements ModInitializer {
	static final BehaviorLog.Sink TEST_SINK = StellaEnd.LOGGER::info;
	/** Whether the vanilla dragon fight ticks. Only the test of the exit portal switches it on. */
	public static volatile boolean dragonFightRuns;
	/** The tag of a fake player a test joined. */
	static final String TEST_PLAYER_TAG = "mod-stella-end-test.player";
	/** Where a castle mob loaded and how far the nearest player stood then. A test clears this before it reads it. */
	record Spawn(BlockPos at, double nearestPlayer) {
	}

	static final List<Spawn> SPAWNS = new CopyOnWriteArrayList<>();

	@Override
	public void onInitialize() {
		BehaviorLog.set(TEST_SINK);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension().equals(Level.END) && entity instanceof Mob mob && EndMobs.floorOf(mob) > 0) {
				SPAWNS.add(new Spawn(mob.blockPosition(), level.players().stream().mapToDouble(player -> player.distanceTo(mob)).min().orElse(Double.MAX_VALUE)));
			}
		});
		// A test player takes no damage, so a long wait never ends with a dead player, yet mobs still hunt them.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !entity.entityTags().contains(TEST_PLAYER_TAG));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && player.level().dimension().equals(Level.END)) {
				StellaEnd.LOGGER.info("[END] test player died source={} attacker={} at={}", source.getMsgId(), source.getEntity(), player.blockPosition());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel end = server.getLevel(Level.END);
			if (end == null) {
				return;
			}
			for (ServerPlayer player : List.copyOf(end.players())) {
				server.getPlayerList().remove(player);
			}
			EndTestSupport.clearMobs(end);
			EndTestSupport.release(end, EndCastle.min());
		});
	}
}
