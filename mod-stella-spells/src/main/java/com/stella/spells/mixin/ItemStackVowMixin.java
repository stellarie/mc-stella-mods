package com.stella.spells.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.stella.spells.UnbrokenVow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The Unbroken Vow carries a share of the durability an item would spend. Every path to wear ends in
 * {@code ItemStack.hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer)}: the hand and the armour slot
 * both call it, so the vow is read once, at the amount that reaches the item.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackVowMixin {
	@ModifyVariable(
		method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private int stella$carryTheVowWear(final int amount, @Local(argsOnly = true) final ServerPlayer player) {
		return player == null ? amount : UnbrokenVow.spent(player, amount);
	}
}
