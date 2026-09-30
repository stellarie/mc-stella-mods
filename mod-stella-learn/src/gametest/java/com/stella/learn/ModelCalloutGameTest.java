package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** A model-driven archer has no rally goal to answer with, so it declines a rally and says why. */
public class ModelCalloutGameTest {
	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void modelDrivenArcherDeclinesRally(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 26.5);
		Mob caller = helper.spawnMob(EntityTypes.ZOMBIE, 20, LearnArena.SPAWN_Y, 28).spawn();
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 28, LearnArena.SPAWN_Y, 28).spawn();
		LearnArena.keepTargeting(helper, archer, player);
		LearnArena.whenReady(helper, List.of(player, archer, caller, zombie), () -> LearnArena.pollUntil(helper, () -> ModelTests.tagged(archer), 40, () -> {
			Callout rally = new Callout(Rally.TYPE, caller.getUUID(), player.getUUID(), 1, 200, null);
			List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, rally, LearnConfig.defaults());
			CalloutBoard.Answer own = answers.stream().filter(answer -> answer.receiver().equals(archer.getUUID())).findFirst().orElse(null);
			CalloutBoard.Answer other = answers.stream().filter(answer -> answer.receiver().equals(zombie.getUUID())).findFirst().orElse(null);
			helper.assertTrue(own != null && !own.accepted() && "model".equals(own.reason()), "the archer answered " + own);
			helper.assertTrue(other != null && other.accepted(), "the plain zombie, kept as a control, answered " + other);
			List<String> lines = LearnTestInit.linesWith("learnCallout ANSWER", "receiver=" + archer.getUUID(), "accepted=false", "reason=model");
			StellaLearn.LOGGER.info("[BEHAVIOR] test=modelDrivenArcherDeclinesRally RESULT answers={} lines={}", answers.size(), lines.size());
			helper.assertTrue(lines.size() == 1, "the archer's refusal wrote " + lines.size() + " ANSWER lines");
			ModelTests.pass(helper);
		}, () -> "the archer was never made model driven"));
	}
}
