package com.stella.nether.mixin;

import com.stella.nether.BlazeEnchantments;
import com.stella.nether.NetherItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A weapon or armor piece on the left and a Blaze Core on the right: Overclock or Magma Armor, for one core and some levels. */
@Mixin(AnvilMenu.class)
public abstract class BlazeCoreAnvilMixin {
	@Accessor("cost")
	protected abstract DataSlot stellaNether$cost();

	@Accessor("repairItemCountCost")
	protected abstract void stellaNether$setRepairItemCountCost(int count);

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void stellaNether$imbue(final CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		if (!menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().is(NetherItems.BLAZE_CORE)
			|| !(((ItemCombinerMenuAccessor) this).stellaNether$player().level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack result = BlazeEnchantments.imbue(level, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem());
		menu.getSlot(AnvilMenu.RESULT_SLOT).set(result);
		this.stellaNether$setRepairItemCountCost(result.isEmpty() ? 0 : 1);
		this.stellaNether$cost().set(result.isEmpty() ? 0 : BlazeEnchantments.ANVIL_LEVELS);
		ci.cancel();
	}
}
