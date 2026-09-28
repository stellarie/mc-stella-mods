package com.stella.food;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public final class FoodBehavior {
	public static final int ORDINARY_TICKS = 6_000;
	public static final int DISH_TICKS = 36_000;
	public static final int INFUSED_TICKS = 54_000;

	private FoodBehavior() {
	}

	public static void onConsumed(final Level level, final LivingEntity eater, final ItemStack food) {
		if (level.isClientSide() || food.get(DataComponents.FOOD) == null) {
			return;
		}
		eater.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40));
		Holder<MobEffect> dishEffect = FoodItems.DISH_EFFECTS.get(food.getItem());
		if (dishEffect != null) {
			int dishTicks = food.has(DataComponents.POTION_CONTENTS) ? INFUSED_TICKS : DISH_TICKS;
			eater.addEffect(new MobEffectInstance(dishEffect, dishTicks));
			applyInfusion(eater, food);
			return;
		}
		eater.addEffect(new MobEffectInstance(FoodCategories.effectFor(food.getItem()), ORDINARY_TICKS));
	}

	public static void onCakeConsumed(final Level level, final LivingEntity eater) {
		if (level.isClientSide()) {
			return;
		}
		eater.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40));
		eater.addEffect(new MobEffectInstance(FoodEffects.SUGAR_RUSH, ORDINARY_TICKS));
	}

	private static void applyInfusion(final LivingEntity eater, final ItemStack food) {
		PotionContents contents = food.get(DataComponents.POTION_CONTENTS);
		if (contents == null || !(eater.level() instanceof ServerLevel level)) {
			return;
		}
		for (MobEffectInstance source : contents.getAllEffects()) {
			Holder<MobEffect> effect = source.getEffect();
			if (effect.value().isInstantaneous()) {
				effect.value().applyInstantaneousEffect(
					level,
					eater,
					eater,
					eater,
					source.getAmplifier(),
					1.0
				);
			} else {
				eater.addEffect(new MobEffectInstance(effect, INFUSED_TICKS, source.getAmplifier()));
			}
		}
	}

	public static float exhaustionScale(final LivingEntity entity) {
		if (entity.hasEffect(FoodEffects.ENDURANCE)) {
			return 0.80F;
		}
		if (entity.hasEffect(FoodEffects.WILD_TASTE) || entity.hasEffect(FoodEffects.NOURISHED)) {
			return 0.90F;
		}
		return 1.0F;
	}
}
