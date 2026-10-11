package com.stella.tower;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Drunk, it heals the drinker in full and grants Regeneration III for half a minute. */
public class SoulTonicItem extends Item {
	public static final int REGENERATION_TICKS = 30 * 20;
	/** Regeneration III: the amplifier counts from nought. */
	public static final int REGENERATION_AMPLIFIER = 2;

	public SoulTonicItem(final Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(final ItemStack itemStack, final Level level, final LivingEntity entity) {
		ItemStack rest = super.finishUsingItem(itemStack, level, entity);
		if (level instanceof ServerLevel) {
			entity.setHealth(entity.getMaxHealth());
			entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_TICKS, REGENERATION_AMPLIFIER));
		}
		return rest;
	}
}
