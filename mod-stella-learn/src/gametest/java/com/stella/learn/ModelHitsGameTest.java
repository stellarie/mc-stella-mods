package com.stella.learn;

import com.stella.learn.model.ArcherObservation;
import com.stella.learn.model.ArcherState;
import com.stella.learn.model.Contract;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** The hit memory of the observation: zero before a first hit, then the archer's own hits and how many more the target needs. */
public class ModelHitsGameTest {
	private static final String[] FIELDS = {"last_hit", "mean_hit", "hits_needed", "ticks_since_hit", "has_hit"};

	private static float raw(final float[] observation, final String field) {
		Contract contract = Contract.archer();
		int index = contract.indexOf("target." + field);
		return observation[index] * contract.scale(index);
	}

	@GameTest(maxTicks = 300, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void hitFieldsFollowTheArchersOwnHits(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.whenReady(helper, List.of(player, archer), () -> {
			ArcherState state = new ArcherState();
			float[] before = ArcherObservation.build(archer, player, state);
			for (String field : FIELDS) {
				helper.assertTrue(raw(before, field) == 0.0F, "target." + field + " is " + raw(before, field) + " before a first hit");
			}

			float max = player.getMaxHealth();
			state.recordHit(0.1F * max, max);
			state.recordHit(0.3F * max, max);
			float[] after = ArcherObservation.build(archer, player, state);
			float health = player.getHealth() / max;
			helper.assertTrue(Math.abs(raw(after, "last_hit") - 0.3F) < 1.0E-4F, "last_hit " + raw(after, "last_hit"));
			helper.assertTrue(Math.abs(raw(after, "mean_hit") - 0.2F) < 1.0E-4F, "mean_hit " + raw(after, "mean_hit"));
			helper.assertTrue(Math.abs(raw(after, "hits_needed") - health / 0.2F) < 1.0E-2F, "hits_needed " + raw(after, "hits_needed") + " against " + health / 0.2F);
			helper.assertTrue(raw(after, "has_hit") == 1.0F, "has_hit " + raw(after, "has_hit"));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=hitFieldsFollowTheArchersOwnHits RESULT last={} mean={} needed={}", raw(after, "last_hit"), raw(after, "mean_hit"), raw(after, "hits_needed"));
			helper.succeed();
		});
	}
}
