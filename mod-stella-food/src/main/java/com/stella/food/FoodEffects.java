package com.stella.food;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class FoodEffects {
	public static final Holder<MobEffect> STEADY_HAND = register(
		"steady_hand",
		basic(0xB87333).addAttributeModifier(
			Attributes.ATTACK_KNOCKBACK,
			modifier("steady_hand"),
			-0.15,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		)
	);
	public static final Holder<MobEffect> RIVER_SENSE = register(
		"river_sense",
		basic(0x3F8FD2).addAttributeModifier(
			Attributes.WATER_MOVEMENT_EFFICIENCY,
			modifier("river_sense"),
			0.20,
			AttributeModifier.Operation.ADD_VALUE
		)
	);
	public static final Holder<MobEffect> QUICK_STEP = register(
		"quick_step",
		basic(0x9BCB3C).addAttributeModifier(
			Attributes.MOVEMENT_SPEED,
			modifier("quick_step"),
			0.08,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		)
	);
	public static final Holder<MobEffect> STURDY = register(
		"sturdy",
		basic(0x8A6F4D).addAttributeModifier(
			Attributes.KNOCKBACK_RESISTANCE,
			modifier("sturdy"),
			0.10,
			AttributeModifier.Operation.ADD_VALUE
		)
	);
	public static final Holder<MobEffect> ENDURANCE = register("endurance", basic(0xD7B56D));
	public static final Holder<MobEffect> SUGAR_RUSH = register(
		"sugar_rush",
		basic(0xF59ECB).addAttributeModifier(
			Attributes.MOVEMENT_SPEED,
			modifier("sugar_rush"),
			0.12,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		)
	);
	public static final Holder<MobEffect> WILD_TASTE = register("wild_taste", basic(0x8D5AB5));
	public static final Holder<MobEffect> NOURISHED = register("nourished", basic(0xE7A84B));
	public static final Holder<MobEffect> SUREFOOTED = register(
		"surefooted",
		basic(0xA96F47).addAttributeModifier(
			Attributes.KNOCKBACK_RESISTANCE,
			modifier("surefooted"),
			0.25,
			AttributeModifier.Operation.ADD_VALUE
		)
	);
	public static final Holder<MobEffect> DEEP_BREATH = register("deep_breath", basic(0x287FA6));
	public static final Holder<MobEffect> LIGHT_STEP = register(
		"light_step",
		basic(0xD8D8B0).addAttributeModifier(
			Attributes.FALL_DAMAGE_MULTIPLIER,
			modifier("light_step"),
			-0.35,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		)
	);
	public static final Holder<MobEffect> CURRENT_RIDER = register(
		"current_rider",
		basic(0x166FC0).addAttributeModifier(
			Attributes.WATER_MOVEMENT_EFFICIENCY,
			modifier("current_rider"),
			0.45,
			AttributeModifier.Operation.ADD_VALUE
		)
	);
	public static final Holder<MobEffect> EMBER_SKIN = register("ember_skin", basic(0xD95A2B));
	public static final Holder<MobEffect> SECOND_WIND = register("second_wind", new SecondWindEffect());
	/** One effect per stat; the RPG mod reads each by id and counts one stat point per level. */
	public static final Map<String, Holder<MobEffect>> SEASONED = seasoned();

	private FoodEffects() {
	}

	public static void register() {
	}

	private static Map<String, Holder<MobEffect>> seasoned() {
		int[] colors = {0xD9534F, 0x4FC37A, 0xE08A3C, 0x4FA8D9, 0x8A63D2, 0xE6C83C};
		Map<String, Holder<MobEffect>> effects = new LinkedHashMap<>();
		for (int i = 0; i < Seasoning.STATS.size(); i++) {
			String stat = Seasoning.STATS.get(i);
			effects.put(stat, register("seasoned_" + stat, basic(colors[i])));
		}
		return Map.copyOf(effects);
	}

	private static MobEffect basic(final int color) {
		return new FoodMobEffect(color);
	}

	private static Identifier modifier(final String path) {
		return StellaFood.id("effect/" + path);
	}

	private static Holder<MobEffect> register(final String path, final MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, StellaFood.id(path), effect);
	}

	private static class FoodMobEffect extends MobEffect {
		FoodMobEffect(final int color) {
			super(MobEffectCategory.BENEFICIAL, color);
		}
	}

	private static final class SecondWindEffect extends FoodMobEffect {
		private static final String USED_TAG = StellaFood.MOD_ID + ".second_wind_used";

		SecondWindEffect() {
			super(0xF2D46B);
		}

		@Override
		public void onEffectAdded(final LivingEntity entity, final int amplifier) {
			super.onEffectAdded(entity, amplifier);
			entity.removeTag(USED_TAG);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(final int tickCount, final int amplifier) {
			return true;
		}

		@Override
		public boolean applyEffectTick(
			final net.minecraft.server.level.ServerLevel level,
			final LivingEntity entity,
			final int amplifier
		) {
			if (entity.getHealth() <= entity.getMaxHealth() * 0.5F && entity.addTag(USED_TAG)) {
				entity.heal(4.0F);
			}
			return true;
		}
	}
}
