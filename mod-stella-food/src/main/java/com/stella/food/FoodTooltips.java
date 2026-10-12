package com.stella.food;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.TooltipDisplay;

public final class FoodTooltips {
	private FoodTooltips() {
	}

	public static TooltipDisplay displayFor(final ItemStack stack, final TooltipDisplay display) {
		if (FoodItems.isDish(stack.getItem()) && stack.has(DataComponents.POTION_CONTENTS)) {
			return display.withHidden(DataComponents.POTION_CONTENTS, true);
		}
		return display;
	}

	public static void append(
		final ItemStack stack,
		final TooltipDisplay display,
		final Consumer<Component> lines
	) {
		TooltipDisplay requested = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		boolean cake = stack.is(Items.CAKE);
		if (display.hideTooltip() || !requested.shows(DataComponents.FOOD)
			|| !cake && (stack.get(DataComponents.FOOD) == null || stack.get(DataComponents.CONSUMABLE) == null)) {
			return;
		}
		DescriptionLines.add(lines, Component.translatable("tooltip.mod-stella-food.regeneration", duration(40))
			.withStyle(ChatFormatting.GRAY));
		Holder<MobEffect> buff = FoodItems.DISH_EFFECTS.get(stack.getItem());
		if (buff == null) {
			buff = stack.is(Items.CAKE) ? FoodEffects.SUGAR_RUSH : FoodCategories.effectFor(stack.getItem());
		}
		int ticks = FoodItems.isDish(stack.getItem())
			? stack.has(DataComponents.POTION_CONTENTS) ? FoodBehavior.INFUSED_TICKS : FoodBehavior.DISH_TICKS
			: FoodBehavior.ORDINARY_TICKS;
		Identifier id = buff.unwrapKey().orElseThrow().identifier();
		String description = "effect." + id.getNamespace() + "." + id.getPath() + ".desc";
		DescriptionLines.add(lines, Component.translatable(
			"tooltip.mod-stella-food.buff",
			buff.value().getDisplayName(),
			duration(ticks),
			Component.translatable(description)
		).withStyle(ChatFormatting.GRAY));
		if (FoodItems.isDish(stack.getItem())) {
			PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
			if (potion != null && requested.shows(DataComponents.POTION_CONTENTS)) {
				for (MobEffectInstance source : potion.getAllEffects()) {
					var mild = FoodBehavior.mildFor(source.getEffect());
					if (mild != null) {
						DescriptionLines.add(lines, Component.translatable("tooltip.mod-stella-food.potion", mild.value().getDisplayName(),
							duration(FoodBehavior.MILD_DEBUFF_TICKS)).withStyle(ChatFormatting.GRAY));
						continue;
					}
					Component name = source.getEffect().value().getDisplayName().copy();
					if (source.getAmplifier() > 0) {
						name = name.copy().append(" ").append(Component.translatable(
							"potion.potency." + source.getAmplifier()
						));
					}
					Component when = source.getEffect().value().isInstantaneous()
						? Component.translatable("tooltip.mod-stella-food.on_eating")
						: duration(FoodBehavior.INFUSED_TICKS);
					DescriptionLines.add(lines, Component.translatable("tooltip.mod-stella-food.potion", name, when)
						.withStyle(ChatFormatting.GRAY));
				}
			}
			Seasoning seasoning = Seasoning.read(stack);
			if (seasoning != null) {
				DescriptionLines.add(lines, Component.translatable(
					"tooltip.mod-stella-food.seasoned", seasoning.amount(), seasoning.label()
				).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static Component duration(final int ticks) {
		if (ticks % 1200 == 0) {
			return Component.translatable("tooltip.mod-stella-food.minutes", ticks / 1200);
		}
		return Component.translatable("tooltip.mod-stella-food.seconds", ticks / 20);
	}
}
