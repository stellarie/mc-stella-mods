package com.stella.equips.mixin;

import com.stella.equips.bag.EmeraldBag;
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
public abstract class InventoryEmeraldMixin {
	@Shadow
	@Final
	public Player player;

	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void stellaEquips$depositEmeralds(final int slot, final ItemStack stack, final CallbackInfoReturnable<Boolean> callback) {
		if (EmeraldBag.absorb(this.player, stack)) {
			callback.setReturnValue(true);
		}
	}
}
