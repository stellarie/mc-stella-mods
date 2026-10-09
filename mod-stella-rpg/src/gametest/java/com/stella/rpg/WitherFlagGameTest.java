package com.stella.rpg;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/**
 * The mobs mod flags an encounter Wither with {@code WitherState.hasWitherFlag}. {@link MobsBridge} reads that flag
 * by name, so percent-of-max-health effects use the elite share on it. A missing flag class costs only that flag.
 */
public final class WitherFlagGameTest {
	private static final String ELITE = "com.stella.mobs.behavior.EliteState";
	private static final String WITHER = "com.stella.mobs.behavior.WitherState";

	@GameTest
	public void flaggedWitherTakesTheEliteShare(final GameTestHelper helper) {
		MobsBridge.setFacts(null);
		try {
			Creeper plain = big(helper);
			Creeper flagged = big(helper);
			flagged.addTag("test.wither");
			float plainTick = PercentEffects.tick(plain);
			float flaggedTick = PercentEffects.tick(flagged);
			RpgTestSupport.result("witherFlagShare", "plain=" + plainTick + " flagged=" + flaggedTick);
			helper.assertTrue(MobsBridge.eliteOrBoss(flagged) && !MobsBridge.eliteOrBoss(plain), "the bridge missed the Wither flag");
			helper.assertTrue(Math.abs(flaggedTick - plainTick * PercentEffects.ELITE_SHARE) < 0.01F,
				"a flagged tick was " + flaggedTick + " against a plain " + plainTick);
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void aMissingFlagClassKeepsTheOthers(final GameTestHelper helper) {
		Creeper elite = big(helper);
		elite.addTag("test.elite");
		Creeper wither = big(helper);
		wither.addTag("test.wither");
		MethodHandle[] handles = MobsBridge.eliteFlags(MethodHandles.publicLookup(), new String[][] {
			{ELITE, "hasEliteFlag"}, {"com.stella.mobs.behavior.NoSuchState", "hasWitherFlag"}, {WITHER, "hasWitherFlag"}
		});
		boolean eliteSeen = MobsBridge.anyFlag(handles, elite);
		boolean witherSeen = MobsBridge.anyFlag(handles, wither);
		RpgTestSupport.result("witherFlagMissingClass", "handles=" + handles.length + " elite=" + eliteSeen + " wither=" + witherSeen);
		helper.assertTrue(handles.length == 2, "expected 2 resolved flags, got " + handles.length);
		helper.assertTrue(eliteSeen && witherSeen, "a flag was lost: elite=" + eliteSeen + " wither=" + witherSeen);
		helper.succeed();
	}

	@GameTest
	public void noFlagClassGivesNoFlag(final GameTestHelper helper) {
		Creeper any = big(helper);
		any.addTag("test.elite");
		MethodHandle[] handles = MobsBridge.eliteFlags(MethodHandles.publicLookup(), new String[][] {
			{"com.stella.mobs.behavior.NoSuchState", "hasWitherFlag"}
		});
		helper.assertTrue(handles.length == 0 && !MobsBridge.anyFlag(handles, any), "an absent class still flagged a mob");
		RpgTestSupport.result("witherFlagNoClass", "handles=" + handles.length);
		helper.succeed();
	}

	private static Creeper big(final GameTestHelper helper) {
		Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new Vec3(1.5, 2.0, 1.5));
		creeper.getAttribute(Attributes.MAX_HEALTH).setBaseValue(3000.0);
		creeper.setHealth(creeper.getMaxHealth());
		return creeper;
	}
}
