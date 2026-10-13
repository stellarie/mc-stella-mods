package com.stella.rpg;

import com.stella.rpg.job.Innate;
import com.stella.rpg.job.Job;
import com.stella.rpg.job.JobInnates;
import com.stella.rpg.job.Jobs;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** A job change grants one innate skill. It is the innate of the new job only, and it outlives death. */
public final class JobInnateGameTest {
	private static final Map<Job, Innate> EXPECTED = Map.of(
		Job.ARCHER, Innate.FALCON_GUARD, Job.FIGHTER, Innate.DOUBLE_STRIKE, Job.MAGE, Innate.ARCANE_WARD, Job.PRIEST, Innate.SANCTUARY
	);

	@GameTest
	@SuppressWarnings("unchecked")
	public void aJobChangeGrantsTheInnateOfThatJobOnly(final GameTestHelper helper) {
		Function<ServerPlayer, String> share = (Function<ServerPlayer, String>)
			((Map<String, Object>) FabricLoader.getInstance().getObjectShare().get(Jobs.SHARE_KEY)).get("innate");
		helper.assertTrue(share != null, "the job share has no innate function");
		for (Map.Entry<Job, Innate> entry : EXPECTED.entrySet()) {
			ServerPlayer player = RpgTestSupport.player(helper);
			player.experienceLevel = Jobs.MIN_LEVEL;
			helper.assertTrue(JobInnates.of(player) == Innate.NONE && share.apply(player).equals("none"),
				"a player without a job holds " + JobInnates.of(player));
			helper.assertTrue(Jobs.choose(player, entry.getKey()), entry.getKey() + " was refused");
			helper.assertTrue(JobInnates.of(player) == entry.getValue(), entry.getKey() + " holds " + JobInnates.of(player));
			for (Innate other : Innate.values()) {
				helper.assertTrue(JobInnates.has(player, other) == (other == entry.getValue()),
					entry.getKey() + " answers has(" + other + ") = " + JobInnates.has(player, other));
			}
			helper.assertTrue(share.apply(player).equals(entry.getValue().key()), "the share says " + share.apply(player));
			RpgTestSupport.result("aJobChangeGrantsTheInnateOfThatJobOnly", entry.getKey() + " -> " + share.apply(player));
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theInnateSurvivesDeathAndRespawn(final GameTestHelper helper) {
		for (Map.Entry<Job, Innate> entry : EXPECTED.entrySet()) {
			ServerPlayer player = InnateTestSupport.player(helper, entry.getKey(), 45);
			ServerPlayer reborn = InnateTestSupport.respawn(helper, player);
			RpgTestSupport.result("theInnateSurvivesDeathAndRespawn", entry.getKey() + " -> " + JobInnates.of(reborn));
			helper.assertTrue(reborn != player, "the player did not respawn");
			helper.assertTrue(JobInnates.of(reborn) == entry.getValue(), entry.getKey() + " holds " + JobInnates.of(reborn) + " after respawn");
			InnateTestSupport.retire(reborn);
		}
		helper.succeed();
	}

	/** How the particles look on a client is seen on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theFlamesAndTheSparklesLookRightRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] choose Mage at the trainer: small flames circle your feet while the shield holds and stop when it breaks. "
			+ "Choose Priest: red and green sparkles mark a 3 block radius at your feet.");
	}
}
