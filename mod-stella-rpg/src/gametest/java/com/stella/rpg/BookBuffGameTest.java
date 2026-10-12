package com.stella.rpg;

import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The MATK a refined spell book leaves on its caster. The spells mod publishes it as an ObjectShare function; this
 * mod adds it to the MATK percent, and does nothing when the key is missing.
 */
public final class BookBuffGameTest {
	private static final double EPSILON = 1e-9;
	private static final double BUFF = 0.10;

	@GameTest
	public void aBookBuffRaisesTheMatkPercentAndTheSpellHitWhileTheSpellsModIsPresent(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		double plainPercent = RpgSpells.matkPercent(player);
		double plainHit = RpgSpells.spellHit(player, 100.0);
		Object saved = FabricLoader.getInstance().getObjectShare().get(BookBuffs.MATK_SHARE);
		FabricLoader.getInstance().getObjectShare().put(BookBuffs.MATK_SHARE, (ToDoubleFunction<Object>) entity -> BUFF);
		try {
			double buffedPercent = RpgSpells.matkPercent(player);
			double buffedHit = RpgSpells.spellHit(player, 100.0);
			RpgTestSupport.result("aBookBuffRaisesTheMatkPercentAndTheSpellHit",
				"plain=" + plainPercent + " buffed=" + buffedPercent + " plainHit=" + plainHit + " buffedHit=" + buffedHit);
			helper.assertTrue(Math.abs(buffedPercent - plainPercent - BUFF) < EPSILON, "MATK percent went from " + plainPercent + " to " + buffedPercent);
			helper.assertTrue(Math.abs(buffedHit / plainHit - (1.0 + buffedPercent) / (1.0 + plainPercent)) < EPSILON,
				"the spell hit went from " + plainHit + " to " + buffedHit);
			helper.assertTrue(buffedHit > plainHit, "the buff did not raise the spell hit");
		} finally {
			restore(saved);
		}
		helper.succeed();
	}

	@GameTest
	public void withNoSpellsModOrAnUnusableAnswerTheBuffIsZero(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		Object saved = FabricLoader.getInstance().getObjectShare().get(BookBuffs.MATK_SHARE);
		try {
			FabricLoader.getInstance().getObjectShare().remove(BookBuffs.MATK_SHARE);
			double absent = RpgSpells.matkPercent(player);
			FabricLoader.getInstance().getObjectShare().put(BookBuffs.MATK_SHARE, (ToDoubleFunction<Object>) entity -> {
				throw new IllegalStateException("the sibling broke");
			});
			double broken = RpgSpells.matkPercent(player);
			FabricLoader.getInstance().getObjectShare().put(BookBuffs.MATK_SHARE, (ToDoubleFunction<Object>) entity -> Double.NaN);
			double notANumber = RpgSpells.matkPercent(player);
			FabricLoader.getInstance().getObjectShare().put(BookBuffs.MATK_SHARE, (ToDoubleFunction<Object>) entity -> -0.5);
			double negative = RpgSpells.matkPercent(player);
			FabricLoader.getInstance().getObjectShare().put(BookBuffs.MATK_SHARE, (ToDoubleFunction<Object>) entity -> 50.0);
			double huge = RpgSpells.matkPercent(player);
			RpgTestSupport.result("withNoSpellsModOrAnUnusableAnswerTheBuffIsZero",
				"absent=" + absent + " broken=" + broken + " nan=" + notANumber + " negative=" + negative + " huge=" + huge);
			helper.assertTrue(Math.abs(absent - 1.0) < EPSILON, "the bare MATK percent at INT 100 is " + absent);
			helper.assertTrue(broken == absent && notANumber == absent && negative == absent, "an unusable answer changed MATK percent");
			helper.assertTrue(Math.abs(huge - absent - 1.0) < EPSILON, "a huge answer was not capped: " + huge);
		} finally {
			restore(saved);
		}
		helper.succeed();
	}

	private static void restore(final Object saved) {
		if (saved == null) {
			FabricLoader.getInstance().getObjectShare().remove(BookBuffs.MATK_SHARE);
		} else {
			FabricLoader.getInstance().getObjectShare().put(BookBuffs.MATK_SHARE, saved);
		}
	}
}
