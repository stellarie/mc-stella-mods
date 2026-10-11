package com.stella.equips.mixin;

import com.stella.equips.durability.MaterialDurability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every point of use damage passes this method. The material rolls first, then Unbreaking rolls on what is left. */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
	@ModifyVariable(method = "processDurabilityChange", at = @At("HEAD"), argsOnly = true)
	private int stellaEquips$materialChance(final int points, final int amount, final ServerLevel level, final ServerPlayer player) {
		return MaterialDurability.charge((ItemStack) (Object) this, points, level.getRandom());
	}
}
