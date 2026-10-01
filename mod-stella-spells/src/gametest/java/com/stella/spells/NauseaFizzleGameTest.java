package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Under Nausea a cast can fizzle: no effect, the cooldown runs, a scroll stays. Without Nausea it never does. */
public final class NauseaFizzleGameTest {
	@GameTest
	public void bookFizzle(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper, true);
		SpellbookItem book = book();
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		float before = caster.getHealth();
		SpellCasting.CastResult result;
		try {
			SpellCasting.setFizzleRoll(player -> true);
			result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		} finally {
			SpellCasting.setFizzleRoll(null);
		}
		boolean cooling = caster.getCooldowns().isOnCooldown(caster.getMainHandItem());
		log("bookFizzle", "result=" + result + " cooling=" + cooling + " healed=" + (caster.getHealth() - before));
		helper.assertTrue(result == SpellCasting.CastResult.FIZZLE && cooling && caster.getHealth() == before,
			"a fizzled book cast was " + result + " cooling=" + cooling + " health " + before + "->" + caster.getHealth());
		helper.succeed();
	}

	@GameTest
	public void scrollKept(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper, true);
		SpellScrollItem scroll = Spellbooks.SCROLLS.stream().filter(item -> item.definition().id().equals(book().definition().id()))
			.findFirst().orElseThrow();
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scroll, 3));
		SpellCasting.CastResult result;
		try {
			SpellCasting.setFizzleRoll(player -> true);
			result = SpellCasting.castScroll(caster, scroll, InteractionHand.MAIN_HAND);
		} finally {
			SpellCasting.setFizzleRoll(null);
		}
		int left = caster.getMainHandItem().getCount();
		log("scrollKept", "result=" + result + " left=" + left);
		helper.assertTrue(result == SpellCasting.CastResult.FIZZLE && left == 3, "a fizzled scroll cast used the scroll: " + result + " " + left);
		helper.succeed();
	}

	@GameTest
	public void noNausea(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper, false);
		SpellbookItem book = book();
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		float before = caster.getHealth();
		SpellCasting.CastResult result;
		try {
			SpellCasting.setFizzleRoll(player -> true);
			result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		} finally {
			SpellCasting.setFizzleRoll(null);
		}
		log("noNausea", "result=" + result + " healed=" + (caster.getHealth() - before));
		helper.assertTrue(result == SpellCasting.CastResult.CAST && caster.getHealth() > before, "a sober cast fizzled: " + result);
		helper.succeed();
	}

	private static SpellbookItem book() {
		return Spellbooks.CATALOG.stream().filter(item -> item.definition().id().getPath().equals("mending_word")).findFirst().orElseThrow();
	}

	private static ServerPlayer caster(final GameTestHelper helper, final boolean nauseous) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.setGameMode(GameType.SURVIVAL);
		caster.setHealth(caster.getMaxHealth() / 2.0F);
		if (nauseous) {
			caster.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20_000));
		}
		return caster;
	}

	private static void log(final String test, final String result) {
		Spellbooks.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, result);
	}
}
