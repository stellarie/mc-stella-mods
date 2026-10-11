package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/** The debug command raises a Blaze Sovereign where it is run, in the raid nether only. */
public class SovereignCommandGameTest {
	static final String SUMMON = RaidTravel.COMMAND + " sovereign summon";

	static CommandSourceStack at(final MinecraftServer server, final ServerLevel level, final Vec3 position) {
		return server.createCommandSourceStack().withLevel(level).withPosition(position).withSuppressedOutput();
	}

	@GameTest(environment = "mod-stella-nether-test:cmd_summon", maxTicks = 100)
	public void theSummonCommandRaisesASovereignWhereItIsRun(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 floor = BlazeSovereignGameTest.arena(raid, server, 10);
		server.getCommands().performPrefixedCommand(at(server, raid, floor), SUMMON);
		Blaze risen = BlazeSovereign.find(server, RaidMode.NORMAL);
		boolean here = risen != null && risen.position().distanceTo(floor) < 0.01;
		NetherTestSupport.result("summonCommand", "risen=" + (risen != null) + " here=" + here + " sovereign=" + BlazeSovereign.isSovereign(risen));
		if (risen != null) {
			risen.discard();
		}
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(here && BlazeSovereign.isSovereign(risen), "the command raised " + risen + " at " + (risen == null ? null : risen.position()) + ", not at " + floor);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:cmd_refused", maxTicks = 100)
	public void theSummonCommandRaisesNothingOutsideTheRaid(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Blaze before = BlazeSovereign.find(server, RaidMode.NORMAL);
		int result;
		try {
			result = server.getCommands().getDispatcher().execute(SUMMON, at(server, helper.getLevel(), helper.absoluteVec(new Vec3(1.5, 1.0, 1.5))));
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException refused) {
			result = 0;
		}
		Blaze after = BlazeSovereign.find(server, RaidMode.NORMAL);
		NetherTestSupport.result("summonRefused", "result=" + result + " same=" + (before == after));
		helper.assertTrue(result == 0 && before == after, "outside the raid the command returned " + result + " and the Sovereign changed=" + (before != after));
		helper.succeed();
	}
}
