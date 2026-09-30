package com.stella.rpg.mixin;

import com.stella.rpg.roll.WeaponRolls;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class RpgCraftRollMixin {
	// Shift-click calls this before the stack moves.
	@Inject(method = "onCraftedBy", at = @At("HEAD"))
	private void stellaRpg$rollCrafted(final ItemStack stack, final Player player, final CallbackInfo callback) {
		if (player instanceof ServerPlayer) {
			WeaponRolls.roll(stack, player.getRandom());
		}
	}
}
