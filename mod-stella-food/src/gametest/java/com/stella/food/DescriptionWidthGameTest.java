package com.stella.food;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Every food and dish description line is at most 24 characters wide, and it states the numbers of the effect. */
public final class DescriptionWidthGameTest {
	@GameTest
	public void everyFoodAndDishTooltipFitsTheLimit(final GameTestHelper helper) {
		FoodTestLog.track(helper, "everyFoodAndDishTooltipFitsTheLimit");
		List<ItemStack> stacks = new ArrayList<>();
		for (Item food : List.of(Items.BEEF, Items.COD, Items.APPLE, Items.CARROT, Items.BREAD, Items.COOKIE, Items.CAKE,
			Items.ROTTEN_FLESH, Items.MUSHROOM_STEW)) {
			stacks.add(new ItemStack(food));
		}
		for (Item dish : FoodItems.DISHES) {
			stacks.add(new ItemStack(dish));
			for (var potion : List.of(Potions.POISON, Potions.HEALING, Potions.SWIFTNESS, Potions.STRONG_SWIFTNESS)) {
				ItemStack infused = new ItemStack(dish);
				infused.set(DataComponents.POTION_CONTENTS, PotionContents.createItemStack(Items.POTION, potion).get(DataComponents.POTION_CONTENTS));
				infused.set(StellaFood.SEASONING, new Seasoning("dex", 10));
				stacks.add(infused);
			}
		}
		List<String> tooLong = new ArrayList<>();
		int shown = 0;
		for (ItemStack stack : stacks) {
			List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, stack));
			shown += lines.size();
			tooLong.addAll(DescriptionSupport.tooLong(lines));
		}
		helper.assertTrue(shown > stacks.size(), "the food stacks showed only " + shown + " lines");
		helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	@GameTest
	public void theWrappedTooltipKeepsTheDurationsAndTheEffectText(final GameTestHelper helper) {
		FoodTestLog.track(helper, "theWrappedTooltipKeepsTheDurationsAndTheEffectText");
		String apple = String.join(" ", DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(Items.APPLE))));
		String stew = String.join(" ", DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(FoodItems.HUNTER_STEW))));
		helper.assertTrue(apple.contains("Regeneration I — 2 seconds: Brief regeneration effect."), "apple lost its regeneration line: " + apple);
		helper.assertTrue(apple.contains("Quick Step — " + FoodBehavior.ORDINARY_TICKS / 1200 + " minutes: Walk 8% faster."),
			"apple lost its buff line: " + apple);
		helper.assertTrue(stew.contains("Surefooted — " + FoodBehavior.DISH_TICKS / 1200 + " minutes: Take 25% less knockback."),
			"the stew lost its buff line: " + stew);
		helper.succeed();
	}

	/** The percent in each description is the amount the effect applies, so a changed amount fails here. */
	@SuppressWarnings("removal")
	@GameTest
	public void describedPercentsMatchTheEffectAmounts(final GameTestHelper helper) {
		FoodTestLog.track(helper, "describedPercentsMatchTheEffectAmounts");
		for (Holder<MobEffect> effect : List.of(FoodEffects.STEADY_HAND, FoodEffects.QUICK_STEP, FoodEffects.STURDY,
			FoodEffects.SUGAR_RUSH, FoodEffects.SUREFOOTED, FoodEffects.LIGHT_STEP, FoodEffects.RIVER_SENSE, FoodEffects.CURRENT_RIDER)) {
			double[] amount = {0.0};
			effect.value().createModifiers(0, (attribute, modifier) -> amount[0] = modifier.amount());
			String percent = Math.round(Math.abs(amount[0]) * 100.0) + "%";
			String text = describe(effect);
			helper.assertTrue(text.contains(percent), effect.getRegisteredName() + " applies " + percent + " but says: " + text);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (Holder<MobEffect> effect : List.of(FoodEffects.ENDURANCE, FoodEffects.WILD_TASTE, FoodEffects.NOURISHED)) {
			player.removeAllEffects();
			player.addEffect(new MobEffectInstance(effect, 200));
			String percent = Math.round((1.0 - FoodBehavior.exhaustionScale(player)) * 100.0) + "%";
			String text = describe(effect);
			helper.assertTrue(text.contains(percent), effect.getRegisteredName() + " saves " + percent + " but says: " + text);
		}
		String rested = describe(FoodEffects.WELL_RESTED);
		String bonus = Math.round((WellRested.XP_FACTOR - 1.0) * 100.0) + "%";
		helper.assertTrue(rested.contains(bonus), "well rested gives " + bonus + " but says: " + rested);
		helper.succeed();
	}

	private static String describe(final Holder<MobEffect> effect) {
		Identifier id = effect.unwrapKey().orElseThrow().identifier();
		return DescriptionSupport.withShippedText(() -> Component
			.translatable("effect." + id.getNamespace() + "." + id.getPath() + ".desc").getString());
	}
}
