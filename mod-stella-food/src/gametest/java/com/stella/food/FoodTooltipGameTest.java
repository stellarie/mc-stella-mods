package com.stella.food;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.TooltipDisplay;

public final class FoodTooltipGameTest {
	private static final String LANG_PATH = "/assets/mod-stella-food/lang/en_us.json";

	@GameTest
	public void ordinaryAndCakeTooltipsExplainBuffs(final GameTestHelper helper) {
		FoodTestLog.track(helper, "ordinaryAndCakeTooltipsExplainBuffs");
		String apple = tooltip(helper, new ItemStack(Items.APPLE));
		String cake = tooltip(helper, new ItemStack(Items.CAKE));
		helper.assertTrue(
			apple.contains("Regeneration I") && apple.contains("2 seconds")
				&& apple.contains("Quick Step") && apple.contains("5 minutes")
				&& apple.contains("8%"),
			"apple tooltip omitted its regeneration, buff, duration, or movement benefit: " + apple
		);
		helper.assertTrue(
			cake.contains("Sugar Rush") && cake.contains("5 minutes") && cake.contains("12%"),
			"cake tooltip omitted its buff or benefit: " + cake
		);
		helper.succeed();
	}

	@GameTest
	public void dishAndInfusionTooltipsShowAppliedDurations(final GameTestHelper helper) {
		FoodTestLog.track(helper, "dishAndInfusionTooltipsShowAppliedDurations");
		ItemStack plain = new ItemStack(FoodItems.HUNTER_STEW);
		String dish = tooltip(helper, plain);
		ItemStack infused = plain.copy();
		infused.set(DataComponents.POTION_CONTENTS, PotionContents.createItemStack(Items.POTION, Potions.POISON)
			.get(DataComponents.POTION_CONTENTS));
		String poison = tooltip(helper, infused);
		helper.assertTrue(
			dish.contains("Surefooted") && dish.contains("30 minutes") && dish.contains("knockback"),
			"dish tooltip omitted its distinct buff, duration, or benefit: " + dish
		);
		helper.assertTrue(
			poison.contains("Surefooted") && poison.contains("45 minutes")
				&& poison.contains("Queasy") && poison.contains("5 minutes") && !poison.contains("Poison")
				&& !poison.contains("30 minutes") && occurrences(poison, "Queasy") == 1,
			"infused dish tooltip contradicted its applied effects: " + poison
		);
		helper.succeed();
	}

	@GameTest
	public void instantModdedAndNonFoodTooltipsStayAccurate(final GameTestHelper helper) {
		FoodTestLog.track(helper, "instantModdedAndNonFoodTooltipsStayAccurate");
		ItemStack instant = new ItemStack(FoodItems.ORCHARD_TART);
		instant.set(DataComponents.POTION_CONTENTS, PotionContents.createItemStack(Items.POTION, Potions.HEALING)
			.get(DataComponents.POTION_CONTENTS));
		ItemStack modded = new ItemStack(Items.STICK);
		modded.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build());
		modded.set(DataComponents.CONSUMABLE, new ItemStack(Items.APPLE).get(DataComponents.CONSUMABLE));
		String instantText = tooltip(helper, instant);
		String moddedText = tooltip(helper, modded);
		String stick = tooltip(helper, new ItemStack(Items.STICK));
		helper.assertTrue(
			instantText.contains("Instant Health") && instantText.contains("on eating")
				&& occurrences(instantText, "Instant Health") == 1,
			"instant infusion tooltip omitted its immediate effect: " + instantText
		);
		helper.assertTrue(
			moddedText.contains("Nourished") && moddedText.contains("5 minutes")
				&& moddedText.contains("exhaustion"),
			"modded ordinary food tooltip omitted its fallback buff: " + moddedText
		);
		helper.assertTrue(
			!stick.contains("Regeneration I") && !stick.contains("Nourished"),
			"non-food stick gained food tooltip lines: " + stick
		);
		helper.succeed();
	}

	@GameTest
	public void foodTooltipsRespectConsumabilityAndHiddenComponents(final GameTestHelper helper) {
		FoodTestLog.track(helper, "foodTooltipsRespectConsumabilityAndHiddenComponents");
		for (Item bucket : List.of(
			Items.COD_BUCKET, Items.SALMON_BUCKET, Items.TROPICAL_FISH_BUCKET, Items.PUFFERFISH_BUCKET
		)) {
			String shown = tooltip(helper, new ItemStack(bucket));
			helper.assertTrue(!shown.contains("Regeneration I") && !shown.contains("Nourished"),
				"non-consumable fish bucket gained food lines: " + shown);
		}
		ItemStack hiddenApple = new ItemStack(Items.APPLE);
		hiddenApple.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.FOOD, true));
		String apple = tooltip(helper, hiddenApple);
		helper.assertTrue(!apple.contains("Regeneration I") && !apple.contains("Quick Step"),
			"hidden food component still showed food lines: " + apple);
		ItemStack hiddenPotion = new ItemStack(FoodItems.HUNTER_STEW);
		hiddenPotion.set(DataComponents.POTION_CONTENTS,
			PotionContents.createItemStack(Items.POTION, Potions.POISON).get(DataComponents.POTION_CONTENTS));
		hiddenPotion.set(DataComponents.TOOLTIP_DISPLAY,
			TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true));
		String infused = tooltip(helper, hiddenPotion);
		helper.assertTrue(infused.contains("Surefooted") && !infused.contains("Poison"),
			"hidden potion component still showed infusion lines: " + infused);
		helper.succeed();
	}

	private static int occurrences(final String text, final String fragment) {
		return (text.length() - text.replace(fragment, "").length()) / fragment.length();
	}

	private static String tooltip(final GameTestHelper helper, final ItemStack stack) {
		Language previous = Language.getInstance();
		try {
			installLanguage();
			List<Component> lines = stack.getTooltipLines(
				Item.TooltipContext.of(helper.getLevel().registryAccess()), null, TooltipFlag.NORMAL
			);
			return String.join(" | ", lines.stream().map(Component::getString).toList());
		} finally {
			Language.inject(previous);
		}
	}

	private static void installLanguage() {
		Map<String, String> shipped = new HashMap<>();
		try (InputStream stream = FoodTooltipGameTest.class.getResourceAsStream(LANG_PATH)) {
			if (stream == null) {
				throw new IllegalStateException("missing resource " + LANG_PATH);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				shipped.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (IOException error) {
			throw new IllegalStateException("cannot load " + LANG_PATH, error);
		}
		Language vanilla = Language.DEFAULT_INSTANCE;
		Language.inject(new Language() {
			@Override
			public String getOrDefault(final String id, final String fallback) {
				return shipped.getOrDefault(id, vanilla.getOrDefault(id, fallback));
			}

			@Override
			public boolean has(final String id) {
				return shipped.containsKey(id) || vanilla.has(id);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return vanilla.isDefaultRightToLeft();
			}

			@Override
			public FormattedCharSequence getVisualOrder(final FormattedText text) {
				return vanilla.getVisualOrder(text);
			}
		});
	}
}
