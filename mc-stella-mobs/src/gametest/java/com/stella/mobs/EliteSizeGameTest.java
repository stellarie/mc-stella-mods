package com.stella.mobs;

import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Elites and bosses stand out: an elite is 40% larger than its kind, and a boss 80%. An elite saved smaller grows on load. */
public class EliteSizeGameTest {
	private static final String TEST = "eliteSize";

	@GameTest
	public void anEliteStandsFortyPercentLarger(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Elite.mark(zombie, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		double scale = zombie.getAttributeValue(Attributes.SCALE);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anEliteStandsFortyPercentLarger scale={}", TEST, scale);
		helper.assertTrue(Math.abs(scale - (1.0 + Elite.SCALE_BONUS)) < 1.0e-6 && Elite.SCALE_BONUS >= 0.4 - 1.0e-9, "the elite stands at " + scale);
		zombie.discard();
		helper.succeed();
	}

	@GameTest
	public void aBossStandsLargerStill(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Bosses.blessAsBoss(zombie, helper.getLevel(), 1.0);
		double scale = zombie.getAttributeValue(Attributes.SCALE);
		double expected = 1.0 + Elite.SCALE_BONUS + Elite.BOSS_SCALE_BONUS;
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aBossStandsLargerStill scale={} expected={}", TEST, scale, expected);
		helper.assertTrue(Math.abs(scale - expected) < 1.0e-6 && expected > 1.0 + Elite.SCALE_BONUS, "the boss stands at " + scale + ", not " + expected);
		zombie.discard();
		helper.succeed();
	}

	@GameTest
	public void anEliteSavedSmallerGrowsWhenItLoads(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Elite.mark(zombie, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		AttributeInstance scale = zombie.getAttribute(Attributes.SCALE);
		scale.removeModifier(Elite.SCALE_ID);
		scale.addPermanentModifier(new AttributeModifier(Elite.SCALE_ID, 0.2, AttributeModifier.Operation.ADD_VALUE));
		Elite.refreshScale(zombie);
		double after = zombie.getAttributeValue(Attributes.SCALE);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anEliteSavedSmallerGrowsWhenItLoads after={}", TEST, after);
		helper.assertTrue(Math.abs(after - (1.0 + Elite.SCALE_BONUS)) < 1.0e-6, "the old elite stayed at " + after);
		zombie.discard();
		helper.succeed();
	}

	private static Zombie zombie(final GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}
}
