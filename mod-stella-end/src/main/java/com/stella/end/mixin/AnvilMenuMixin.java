package com.stella.end.mixin;

import com.stella.end.CoreImbue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A weapon and a core make the imbued weapon at a set price, using one core. The core step cancels the vanilla repair and rename step. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Accessor("cost")
	protected abstract DataSlot stellaEnd$costSlot();

	@Accessor("repairItemCountCost")
	protected abstract void stellaEnd$setRepairCount(int count);

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$imbueCore(final CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack core = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		if (!CoreImbue.isCore(core)) {
			return;
		}
		Player player = ((ItemCombinerAccess) this).stellaEnd$player();
		ItemStack result = CoreImbue.result(player.level().registryAccess(), menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(), core);
		menu.getSlot(AnvilMenu.RESULT_SLOT).set(result);
		// The cancel skips the vanilla price step, so this branch writes the price itself.
		this.stellaEnd$costSlot().set(result.isEmpty() ? 0 : CoreImbue.COST);
		this.stellaEnd$setRepairCount(1);
		menu.broadcastChanges();
		ci.cancel();
	}
}
