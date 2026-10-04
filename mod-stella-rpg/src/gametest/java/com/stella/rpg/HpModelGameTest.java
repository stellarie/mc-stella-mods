package com.stella.rpg;

import com.stella.rpg.job.Job;
import com.stella.rpg.job.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Max health follows level, VIT, and job. Regeneration and spell duration follow VIT and INT. */
public final class HpModelGameTest {
	private static final double REGEN_BASE = 0.005;
	private static final double REGEN_PER_VIT = 0.0002;

	private static double expectedMax(final ServerPlayer player, final int level, final Job job) {
		int vit = StatBonuses.effective(player, Stat.VIT);
		return (30.0 + 3.0 * (level - 1)) * (1.0 + vit / 100.0) * factor(job) * RpgScale.k();
	}

	private static double factor(final Job job) {
		return switch (job) {
			case FIGHTER -> 1.3;
			case PRIEST -> 1.1;
			case MAGE -> 0.85;
			default -> 1.0;
		};
	}

	private static ServerPlayer modeled(final GameTestHelper helper, final int level, final Job job, final int vit) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgScale.apply(player);
		player.experienceLevel = level;
		player.setAttached(Jobs.ATTACHMENT, new Jobs.State(job, List.of()));
		RpgTestSupport.setStat(player, Stat.VIT, vit);
		RpgAttributes.refresh(player);
		return player;
	}

	private static boolean near(final double actual, final double expected) {
		return Math.abs(actual - expected) <= Math.max(0.01, expected * 1e-5);
	}

	@GameTest
	public void maxHealthFollowsLevelVitAndJob(final GameTestHelper helper) {
		StringBuilder rows = new StringBuilder();
		for (int level : new int[] {1, 20, 100}) {
			for (Job job : new Job[] {Job.NONE, Job.FIGHTER, Job.PRIEST, Job.MAGE, Job.ARCHER}) {
				ServerPlayer player = modeled(helper, level, job, 25);
				double expected = expectedMax(player, level, job);
				rows.append(level).append('/').append(job.key()).append('=').append(player.getMaxHealth()).append(' ');
				helper.assertTrue(near(player.getMaxHealth(), expected),
					"level " + level + " job " + job + " max " + player.getMaxHealth() + " expected " + expected);
			}
		}
		ServerPlayer fresh = modeled(helper, 1, Job.NONE, 0);
		RpgTestSupport.result("maxHealthFollowsLevelVitAndJob", rows + "fresh=" + fresh.getMaxHealth());
		helper.assertTrue(near(fresh.getMaxHealth(), 30.0 * RpgScale.k()), "level 1 without VIT is " + fresh.getMaxHealth());
		helper.succeed();
	}

	@GameTest
	public void healthKeepsItsShare(final GameTestHelper helper) {
		ServerPlayer player = modeled(helper, 1, Job.NONE, 0);
		player.setHealth(player.getMaxHealth() / 2.0F);
		float oldMax = player.getMaxHealth();

		RpgTestSupport.setStat(player, Stat.VIT, 100);
		RpgAttributes.refresh(player);
		float afterVit = player.getHealth() / player.getMaxHealth();
		player.experienceLevel = 50;
		RpgAttributes.refresh(player);
		float afterLevel = player.getHealth() / player.getMaxHealth();
		player.setAttached(Jobs.ATTACHMENT, new Jobs.State(Job.FIGHTER, List.of()));
		RpgAttributes.refresh(player);
		float afterJob = player.getHealth() / player.getMaxHealth();
		RpgTestSupport.setStat(player, Stat.VIT, 0);
		RpgAttributes.refresh(player);
		float afterShrink = player.getHealth() / player.getMaxHealth();
		RpgTestSupport.result("healthKeepsItsShare", "old=" + oldMax + " shares=" + afterVit + "," + afterLevel + "," + afterJob
			+ "," + afterShrink);
		helper.assertTrue(player.getMaxHealth() > oldMax, "max did not grow: " + player.getMaxHealth());
		for (float share : new float[] {afterVit, afterLevel, afterJob, afterShrink}) {
			helper.assertTrue(Math.abs(share - 0.5F) < 1e-3F, "share " + share);
		}
		helper.succeed();
	}

	@GameTest
	public void equipsLevelModifierDoesNotStack(final GameTestHelper helper) {
		ServerPlayer player = modeled(helper, 16, Job.NONE, 0);
		float before = player.getMaxHealth();
		Identifier equips = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "power_max_health");
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		health.addOrReplacePermanentModifier(new AttributeModifier(equips, 37.5, AttributeModifier.Operation.ADD_VALUE));
		RpgAttributes.refresh(player);
		RpgTestSupport.result("equipsLevelModifierDoesNotStack", "before=" + before + " after=" + player.getMaxHealth());
		helper.assertTrue(near(player.getMaxHealth(), before), "max health stacked: " + player.getMaxHealth() + " vs " + before);
		helper.assertTrue(near(before, expectedMax(player, 16, Job.NONE)), "base model is " + before);
		helper.succeed();
	}

	@GameTest
	public void regenFollowsVit(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.setStat(player, Stat.VIT, 100);
		RpgAttributes.refresh(player);
		int vit = StatBonuses.effective(player, Stat.VIT);
		float max = player.getMaxHealth();
		player.setHealth(max / 2.0F);
		float start = player.getHealth();
		double expected = max * (REGEN_BASE + REGEN_PER_VIT * vit);
		try {
			HpModel.setClock(() -> 99L);
			HpModel.tick(helper.getLevel().getServer());
			float offBeat = player.getHealth();
			HpModel.setClock(() -> 100L);
			HpModel.tick(helper.getLevel().getServer());
			float onBeat = player.getHealth();
			RpgTestSupport.result("regenFollowsVit", "vit=" + vit + " start=" + start + " off=" + offBeat + " on=" + onBeat
				+ " expected=" + expected);
			helper.assertTrue(offBeat == start, "healed off the beat: " + offBeat);
			helper.assertTrue(Math.abs((onBeat - start) - expected) < Math.max(0.01, expected * 1e-4),
				"healed " + (onBeat - start) + " expected " + expected);

			player.setHealth(max);
			HpModel.tick(helper.getLevel().getServer());
			helper.assertTrue(player.getHealth() == max, "a full player changed to " + player.getHealth());

			player.kill(helper.getLevel());
			HpModel.tick(helper.getLevel().getServer());
			helper.assertTrue(!player.isAlive() && player.getHealth() <= 0.0F, "a dead player healed to " + player.getHealth());
		} finally {
			HpModel.setClock(null);
		}
		helper.succeed();
	}

	@GameTest
	public void durationFollowsInt(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		StringBuilder rows = new StringBuilder();
		double previous = 0.0;
		for (int intel : new int[] {0, 100, 200, 400, 900}) {
			RpgTestSupport.setStat(player, Stat.INT, intel);
			int effective = StatBonuses.effective(player, Stat.INT);
			double multiplier = RpgSpells.durationMultiplier(player);
			rows.append(effective).append('=').append(multiplier).append(' ');
			helper.assertTrue(Math.abs(multiplier - Math.min(3.0, 1.0 + effective / 200.0)) < 1e-9,
				"INT " + effective + " gave " + multiplier);
			helper.assertTrue(multiplier >= previous, "not monotonic at " + effective);
			previous = multiplier;
		}
		RpgTestSupport.result("durationFollowsInt", rows.toString());
		helper.assertTrue(previous == 3.0, "cap is " + previous);
		helper.succeed();
	}
}
