package com.stella.rpg;

import com.stella.rpg.job.ArcaneWard;
import com.stella.rpg.job.Job;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.spider.Spider;

/**
 * The Mage innate: an absorption shield of (MATK / 100) x 10 percent of max health that refills 300 ticks after the
 * last hit. When it breaks, hostile mobs within 3 blocks take (MATK / 100) x 5 percent of the max health of the mage.
 */
public final class ArcaneWardGameTest {
	private static final int LEVEL = 20;
	private static final int INT_POINTS = 60;
	private static final double CELL_X = 2.5;
	private static final double CELL_Z = 2.5;

	private static ServerPlayer mage(final GameTestHelper helper, final int points) {
		ServerPlayer mage = InnateTestSupport.player(helper, Job.MAGE, LEVEL);
		RpgTestSupport.setStat(mage, Stat.INT, points);
		return mage;
	}

	private static double matk(final ServerPlayer player) {
		return StatBonuses.effective(player, Stat.INT) * RpgConfigStore.current().magic().matkPerInt();
	}

	private static double shield(final ServerPlayer player) {
		return ArcaneWard.SHIELD_SHARE * matk(player) * player.getMaxHealth();
	}

	@GameTest(maxTicks = 40)
	public void theShieldIsTenPercentOfMaxHealthPerHundredMatk(final GameTestHelper helper) {
		helper.assertTrue(ArcaneWard.SHIELD_SHARE == 0.10 && ArcaneWard.BURST_SHARE == 0.05 && ArcaneWard.REFILL_TICKS == 300
			&& ArcaneWard.BURST_RADIUS == 3.0, "the ward numbers drifted from 10 percent, 5 percent, 300 ticks and 3 blocks");
		ServerPlayer mage = mage(helper, INT_POINTS);
		ServerPlayer control = InnateTestSupport.player(helper, Job.PRIEST, LEVEL);
		RpgTestSupport.setStat(control, Stat.INT, INT_POINTS);
		double small = shield(mage);
		helper.runAfterDelay(6L, () -> {
			float low = mage.getAbsorptionAmount();
			RpgTestSupport.setStat(mage, Stat.INT, 2 * INT_POINTS);
			double big = shield(mage);
			helper.runAfterDelay(6L, () -> {
				float high = mage.getAbsorptionAmount();
				RpgTestSupport.result("theShieldIsTenPercentOfMaxHealthPerHundredMatk",
					"matk=" + matk(mage) + " max=" + mage.getMaxHealth() + " low=" + low + " expectedLow=" + small + " high=" + high
						+ " expectedHigh=" + big + " control=" + control.getAbsorptionAmount());
				helper.assertTrue(small > 0.0 && big > small * 1.5, "more INT did not give a bigger shield: " + small + " then " + big);
				helper.assertTrue(InnateTestSupport.near(low, small), "the shield is " + low + ", expected " + small);
				helper.assertTrue(InnateTestSupport.near(high, big), "after more INT the shield is " + high + ", expected " + big);
				helper.assertTrue(control.getAbsorptionAmount() == 0.0F, "a priest with the same INT holds a shield of " + control.getAbsorptionAmount());
				InnateTestSupport.retire(mage);
				InnateTestSupport.retire(control);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 520)
	public void theShieldRefillsThreeHundredTicksAfterTheLastHit(final GameTestHelper helper) {
		ServerPlayer mage = mage(helper, INT_POINTS);
		float[] full = {0.0F};
		float[] afterFirst = {0.0F};
		float[] afterSecond = {0.0F};
		helper.runAfterDelay(6L, () -> {
			full[0] = mage.getAbsorptionAmount();
			InnateTestSupport.hit(helper, mage, 0.3 * full[0]);
			afterFirst[0] = mage.getAbsorptionAmount();
		});
		long second = 106L;
		helper.runAfterDelay(second, () -> {
			InnateTestSupport.hit(helper, mage, 0.1 * full[0]);
			afterSecond[0] = mage.getAbsorptionAmount();
		});
		// Past 300 ticks from the first hit, but not from the second.
		helper.runAfterDelay(6L + ArcaneWard.REFILL_TICKS + 10L, () -> helper.assertTrue(mage.getAbsorptionAmount() == afterSecond[0],
			"the shield refilled to " + mage.getAbsorptionAmount() + " 300 ticks after the first hit, but a later hit came since"));
		helper.runAfterDelay(second + ArcaneWard.REFILL_TICKS - 20L, () -> helper.assertTrue(mage.getAbsorptionAmount() == afterSecond[0],
			"the shield refilled to " + mage.getAbsorptionAmount() + " before 300 ticks passed"));
		helper.runAfterDelay(second + ArcaneWard.REFILL_TICKS + 20L, () -> {
			RpgTestSupport.result("theShieldRefillsThreeHundredTicksAfterTheLastHit",
				"full=" + full[0] + " afterFirst=" + afterFirst[0] + " afterSecond=" + afterSecond[0] + " now=" + mage.getAbsorptionAmount());
			helper.assertTrue(full[0] > 0.0F && afterFirst[0] < full[0] && afterSecond[0] < afterFirst[0], "the hits did not wear the shield");
			helper.assertTrue(InnateTestSupport.near(mage.getAbsorptionAmount(), full[0]),
				"the shield is " + mage.getAbsorptionAmount() + " after 300 quiet ticks, expected " + full[0]);
			InnateTestSupport.retire(mage);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void breakingTheShieldHurtsHostilesWithinThreeBlocksOnly(final GameTestHelper helper) {
		ServerPlayer mage = mage(helper, INT_POINTS);
		Spider near = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 4, 2);
		// A smaller pool keeps the float steps of the health below the tolerance of the check.
		near.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0);
		near.setHealth(near.getMaxHealth());
		// Five blocks above the mage, so no wall of the cell is near it.
		Spider farAway = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 2, 6, 2);
		Pig harmless = InnateTestSupport.dummy(helper, EntityTypes.PIG, 1, 2);
		Spider friend = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 2, 4);
		friend.addTag("mc-stella-villages.patrol");
		helper.runAfterDelay(6L, () -> {
			float shield = mage.getAbsorptionAmount();
			helper.assertTrue(shield > 0.0F, "the mage holds no shield");
			InnateTestSupport.hit(helper, mage, 0.3 * shield);
			helper.assertTrue(near.getHealth() == near.getMaxHealth(), "a hit that left the shield standing hurt a mob");
			double matk = matk(mage);
			// The base is the mage's max health, the base of the shield. The dummy's pool is far larger, so the bases differ.
			double expected = matk * ArcaneWard.BURST_SHARE * mage.getMaxHealth();
			InnateTestSupport.hit(helper, mage, 3.0 * shield);
			double lost = near.getMaxHealth() - near.getHealth();
			RpgTestSupport.result("breakingTheShieldHurtsHostilesWithinThreeBlocksOnly",
				"matk=" + matk + " lost=" + lost + " expected=" + expected + " far=" + (farAway.getMaxHealth() - farAway.getHealth())
					+ " pig=" + (harmless.getMaxHealth() - harmless.getHealth()) + " friend=" + (friend.getMaxHealth() - friend.getHealth()));
			helper.assertTrue(mage.getAbsorptionAmount() == 0.0F && mage.isAlive(), "the hit did not break the shield, or it killed the mage");
			helper.runAfterDelay(2L, () -> {
				double burst = near.getMaxHealth() - near.getHealth();
				helper.assertTrue(expected > 0.0 && InnateTestSupport.near(burst, expected),
					"a mob 2 blocks away lost " + burst + ", expected " + expected);
				helper.assertTrue(farAway.getHealth() == farAway.getMaxHealth(), "a mob 5 blocks away was hurt");
				helper.assertTrue(harmless.getHealth() == harmless.getMaxHealth(), "a pig was hurt");
				helper.assertTrue(friend.getHealth() == friend.getMaxHealth(), "a mob that a village marked as a friend was hurt");
				near.discard();
				farAway.discard();
				harmless.discard();
				friend.discard();
				InnateTestSupport.retire(mage);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 80)
	public void theWardNeitherStacksWithNorErasesAnAbsorptionEffect(final GameTestHelper helper) {
		ServerPlayer mage = mage(helper, INT_POINTS);
		ServerPlayer bare = mage(helper, INT_POINTS);
		helper.runAfterDelay(4L, () -> {
			float ward = mage.getAbsorptionAmount();
			helper.assertTrue(ward > 0.0F, "the mage holds no shield");
			mage.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2_000, 4));
			float grant = mage.getAbsorptionAmount();
			helper.runAfterDelay(40L, () -> {
				RpgTestSupport.result("theWardNeitherStacksWithNorErasesAnAbsorptionEffect",
					"ward=" + ward + " grant=" + grant + " now=" + mage.getAbsorptionAmount() + " alone=" + bare.getAbsorptionAmount());
				helper.assertTrue(grant > ward * 1.5F, "the Absorption effect gave " + grant + ", no more than the ward " + ward);
				helper.assertTrue(mage.getAbsorptionAmount() == grant, "the pool is " + mage.getAbsorptionAmount() + ", the effect gave " + grant);
				helper.assertTrue(bare.getAbsorptionAmount() == ward, "a second refill changed the ward to " + bare.getAbsorptionAmount());
				mage.removeAllEffects();
				InnateTestSupport.retire(mage);
				InnateTestSupport.retire(bare);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 60)
	public void smallFlamesCircleTheFeetOnlyWhileTheShieldHolds(final GameTestHelper helper) {
		RpgTestSupport.Recorded recorded = RpgTestSupport.recordedPlayer(helper);
		ServerPlayer mage = recorded.player();
		RpgTestSupport.bare(mage);
		mage.experienceLevel = LEVEL;
		com.stella.rpg.job.Jobs.choose(mage, Job.MAGE);
		RpgTestSupport.setStat(mage, Stat.INT, INT_POINTS);
		helper.runAfterDelay(6L, () -> {
			drain(recorded);
			helper.runAfterDelay(12L, () -> {
				List<double[]> flames = flames(recorded);
				double widest = 0.0;
				double lowest = Double.MAX_VALUE;
				double highest = -Double.MAX_VALUE;
				for (double[] at : flames) {
					widest = Math.max(widest, Math.hypot(at[0] - mage.getX(), at[2] - mage.getZ()));
					lowest = Math.min(lowest, at[1] - mage.getY());
					highest = Math.max(highest, at[1] - mage.getY());
				}
				RpgTestSupport.result("smallFlamesCircleTheFeetOnlyWhileTheShieldHolds",
					"flames=" + flames.size() + " widest=" + widest + " low=" + lowest + " high=" + highest);
				helper.assertTrue(flames.size() >= 20, "the shield showed only " + flames.size() + " small flames in 12 ticks");
				helper.assertTrue(widest > 0.4 && widest < 1.2, "the flames circle at " + widest + " blocks");
				helper.assertTrue(lowest >= 0.0 && highest <= 0.5, "the flames are " + lowest + " to " + highest + " blocks above the feet");
				InnateTestSupport.hit(helper, mage, 3.0 * mage.getAbsorptionAmount());
				helper.assertTrue(mage.getAbsorptionAmount() == 0.0F, "the hit left a shield of " + mage.getAbsorptionAmount());
				helper.runAfterDelay(2L, () -> {
					drain(recorded);
					helper.runAfterDelay(12L, () -> {
						int broken = flames(recorded).size();
						RpgTestSupport.result("smallFlamesCircleTheFeetOnlyWhileTheShieldHolds", "afterBreak=" + broken);
						helper.assertTrue(broken == 0, "a broken shield still showed " + broken + " small flames");
						InnateTestSupport.retire(mage);
						helper.succeed();
					});
				});
			});
		});
	}

	@GameTest(maxTicks = 40)
	public void theShieldIsUpAgainAfterRespawn(final GameTestHelper helper) {
		ServerPlayer mage = mage(helper, INT_POINTS);
		ServerPlayer reborn = InnateTestSupport.respawn(helper, mage);
		RpgTestSupport.setStat(reborn, Stat.INT, INT_POINTS);
		helper.runAfterDelay(6L, () -> {
			double expected = shield(reborn);
			RpgTestSupport.result("theShieldIsUpAgainAfterRespawn", "shield=" + reborn.getAbsorptionAmount() + " expected=" + expected);
			helper.assertTrue(expected > 0.0 && InnateTestSupport.near(reborn.getAbsorptionAmount(), expected),
				"the respawned mage holds " + reborn.getAbsorptionAmount() + ", expected " + expected);
			InnateTestSupport.retire(reborn);
			helper.succeed();
		});
	}

	private static void drain(final RpgTestSupport.Recorded recorded) {
		while (recorded.channel().readOutbound() != null) {
			// Discard what the mage was sent so far.
		}
	}

	/** The small flames within two blocks of the mage. A mage in a neighbouring cell sends its own to the same connection. */
	private static List<double[]> flames(final RpgTestSupport.Recorded recorded) {
		List<double[]> found = new ArrayList<>();
		ServerPlayer mage = recorded.player();
		Object message;
		while ((message = recorded.channel().readOutbound()) != null) {
			if (message instanceof ClientboundLevelParticlesPacket packet && packet.particle().getType() == ParticleTypes.SMALL_FLAME
				&& Math.hypot(packet.x() - mage.getX(), packet.z() - mage.getZ()) < 2.0) {
				found.add(new double[] {packet.x(), packet.y(), packet.z()});
			}
		}
		return found;
	}
}
