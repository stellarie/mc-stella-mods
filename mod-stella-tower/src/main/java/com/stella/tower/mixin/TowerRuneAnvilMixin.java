package com.stella.tower.mixin;

import com.stella.tower.RuneData;
import com.stella.tower.TowerItems;
import com.stella.tower.TowerRunes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A weapon or armor piece on the left and a rune on the right set the rune, replacing any before, for a
 * fixed 64 emeralds from the player's inventory and no levels. One rune is used.
 */
@Mixin(AnvilMenu.class)
public abstract class TowerRuneAnvilMixin {
	@Accessor("cost")
	protected abstract DataSlot stellaTower$cost();

	@Accessor("repairItemCountCost")
	protected abstract void stellaTower$setRepairItemCountCost(int count);

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void stellaTower$setRune(final CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		RuneData rune = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().get(TowerItems.RUNE_DATA);
		if (rune == null || !menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().is(TowerItems.RUNE)) {
			return;
		}
		ItemStack result = TowerRunes.apply(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(), rune);
		menu.getSlot(AnvilMenu.RESULT_SLOT).set(result);
		this.stellaTower$setRepairItemCountCost(1);
		this.stellaTower$cost().set(0);
		ci.cancel();
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void stellaTower$payInEmeralds(final Player player, final boolean hasItem, final CallbackInfoReturnable<Boolean> cir) {
		if (this.stellaTower$isRune()) {
			cir.setReturnValue(hasItem && (player.hasInfiniteMaterials() || player.getInventory().countItem(Items.EMERALD) >= TowerRunes.EMERALDS));
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void stellaTower$takeEmeralds(final Player player, final ItemStack stack, final CallbackInfo ci) {
		if (this.stellaTower$isRune() && !player.hasInfiniteMaterials()) {
			int owed = TowerRunes.EMERALDS;
			for (int slot = 0; slot < player.getInventory().getContainerSize() && owed > 0; slot++) {
				ItemStack held = player.getInventory().getItem(slot);
				if (held.is(Items.EMERALD)) {
					int take = Math.min(owed, held.getCount());
					held.shrink(take);
					owed -= take;
				}
			}
		}
	}

	@Unique
	private boolean stellaTower$isRune() {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		return menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().is(TowerItems.RUNE) && !menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty();
	}
}
