package com.stella.rpg.mixin;

import com.stella.rpg.bag.Bag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class RpgBagPickupMixin {
	@Shadow
	@Final
	public Player player;

	// Only the "any slot" add overflows into the bag.
	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$overflowToBag(final int slot, final ItemStack stack, final CallbackInfoReturnable<Boolean> callback) {
		if (slot != -1 || stack.isEmpty()) {
			return;
		}
		int before = stack.getCount();
		Bag.insert(this.player, stack);
		if (stack.getCount() < before) {
			callback.setReturnValue(true);
		}
	}
}
