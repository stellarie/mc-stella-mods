package com.stella.food;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;

/** A damaging potion in a dish becomes a five-minute mild debuff; food never poisons, withers or harms. */
public final class MildDebuffGameTest {
	@GameTest
	public void poisonBecomesQueasy(final GameTestHelper helper) {
		check(helper, "poisonBecomesQueasy", Potions.POISON, MobEffects.POISON, FoodEffects.QUEASY);
	}

	@GameTest
	public void witherBecomesSluggish(final GameTestHelper helper) {
		ItemStack dish = new ItemStack(FoodItems.HUNTER_STEW);
		dish.set(DataComponents.POTION_CONTENTS, new PotionContents(java.util.Optional.empty(), java.util.Optional.empty(),
			java.util.List.of(new MobEffectInstance(MobEffects.WITHER, 400)), java.util.Optional.empty()));
		eat(helper, "witherBecomesSluggish", dish, MobEffects.WITHER, FoodEffects.SLUGGISH);
	}

	@GameTest
	public void harmingBecomesHeavy(final GameTestHelper helper) {
		check(helper, "harmingBecomesHeavy", Potions.HARMING, MobEffects.INSTANT_DAMAGE, FoodEffects.HEAVY);
	}

	@GameTest
	public void slownessStays(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack dish = new ItemStack(FoodItems.HUNTER_STEW);
		dish.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SLOWNESS));
		FoodBehavior.onConsumed(helper.getLevel(), player, dish);
		MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
		StellaFood.LOGGER.info("[BEHAVIOR] test=slownessStays RESULT slow={}", slow);
		helper.assertTrue(slow != null && slow.getDuration() > FoodBehavior.MILD_DEBUFF_TICKS, "slowness from a dish changed: " + slow);
		helper.succeed();
	}

	private static void check(final GameTestHelper helper, final String test, final Holder<Potion> potion,
		final Holder<MobEffect> original, final Holder<MobEffect> mild) {
		ItemStack dish = new ItemStack(FoodItems.HUNTER_STEW);
		dish.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
		eat(helper, test, dish, original, mild);
	}

	private static void eat(final GameTestHelper helper, final String test, final ItemStack dish,
		final Holder<MobEffect> original, final Holder<MobEffect> mild) {
		ServerPlayer player = player(helper);
		float before = player.getHealth();
		FoodBehavior.onConsumed(helper.getLevel(), player, dish);
		MobEffectInstance got = player.getEffect(mild);
		boolean noOriginal = !player.hasEffect(original);
		boolean unhurt = player.getHealth() >= before;
		StellaFood.LOGGER.info("[BEHAVIOR] test={} RESULT mild={} noOriginal={} unhurt={}", test, got, noOriginal, unhurt);
		helper.assertTrue(got != null && got.getDuration() == FoodBehavior.MILD_DEBUFF_TICKS, "no five-minute mild debuff: " + got);
		helper.assertTrue(noOriginal && unhurt, "the damaging effect still applied");
		helper.succeed();
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}
}
