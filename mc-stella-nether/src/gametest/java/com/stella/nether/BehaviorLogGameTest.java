package com.stella.nether;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Blaze;

/**
 * The shipped jar logs no behaviour: its lines go through a sink that starts silent, and only the tests switch it
 * on. The logger itself is left alone: a test that reconfigured it once hid every later line of the run.
 */
public class BehaviorLogGameTest {
	@GameTest(environment = "mod-stella-nether-test:quiet_log", maxTicks = 100)
	public void theShippedJarWritesNoBehaviourLines(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		List<String> routed = new CopyOnWriteArrayList<>();
		BehaviorLog.Sink previous = BehaviorLog.set((message, args) -> routed.add(message));
		try {
			Blaze blaze = BlazeSovereign.rise(raid, BlazeSovereignGameTest.arena(raid, server, 8));
			blaze.discard();
		} finally {
			BehaviorLog.set(previous);
		}
		long risen = routed.stream().filter(line -> line.contains("[NETHER] sovereign risen")).count();
		boolean shippedSilent = NetherGameTestInit.shippedSink == BehaviorLog.SILENT;
		NetherTestSupport.result("behaviourLog", "shippedSilent=" + shippedSilent + " routedRise=" + risen);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(shippedSilent && risen == 1, "the jar starts with the silent sink=" + shippedSilent + ", a rise went through it " + risen + " times");
		helper.succeed();
	}
}
