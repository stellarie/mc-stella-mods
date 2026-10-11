package com.stella.tower;

import com.stella.mobs.behavior.TrueInvisibility;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Perfect Invisibility Potion's veil. The potion gives vanilla invisibility, which the mobs mod turns into true
 * invisibility for every mob that is not undead, lifted for {@link TrueInvisibility#REVEAL_TICKS} ticks by an attack. The veil
 * carries the same hiding to the undead, which the mobs mod leaves seeing an invisible player.
 */
public final class PerfectVeil {
	/** A minute, in ticks. */
	public static final int TICKS = 60 * 20;
	public static final int COLOR = 0xB8D8F0;
	public static final Holder<MobEffect> VEIL = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, StellaTower.id("perfect_veil"),
		new MobEffect(MobEffectCategory.BENEFICIAL, COLOR) {
		});

	private PerfectVeil() {
	}

	public static void register() {
		StellaTower.LOGGER.debug("Registered {}", VEIL.getRegisteredName());
	}

	/**
	 * Whether a looker cannot see the target at all: a player wearing the veil and invisible, who has not struck a blow in the
	 * last {@link TrueInvisibility#REVEAL_TICKS} ticks. No mob, undead or not, takes such a player as its target.
	 */
	public static boolean hides(final LivingEntity target, final Entity looker) {
		return target instanceof Player player && player.hasEffect(VEIL) && player.hasEffect(MobEffects.INVISIBILITY) && !TrueInvisibility.isRevealed(player);
	}

	/** The potion the ghost merchant sells. */
	public static ItemStack potion() {
		ItemStack potion = new ItemStack(Items.POTION);
		potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(COLOR), List.of(
			new MobEffectInstance(MobEffects.INVISIBILITY, TICKS, 0, false, false, true),
			new MobEffectInstance(VEIL, TICKS, 0, false, false, false)), Optional.empty()));
		potion.set(DataComponents.ITEM_NAME, Component.translatable("item.mod-stella-tower.perfect_invisibility").withStyle(ChatFormatting.AQUA));
		potion.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.mod-stella-tower.perfect_invisibility.desc").withStyle(ChatFormatting.GRAY))));
		return potion;
	}
}
