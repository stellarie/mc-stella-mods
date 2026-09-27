package com.stella.spells.mixin;

import com.stella.spells.SpellImbue;
import com.stella.spells.SpellbookItem;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A weapon on the left and a spellbook on the right make the imbued weapon; the book is used up whole. */
@Mixin(AnvilMenu.class)
public abstract class SpellImbueAnvilMixin {
	@Accessor("cost")
	protected abstract DataSlot modStellaSpells$imbueCost();

	@Accessor("repairItemCountCost")
	protected abstract void modStellaSpells$setRepairItemCountCost(int count);

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void modStellaSpells$imbue(final CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack weapon = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		if (!(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getItem() instanceof SpellbookItem book) || !SpellImbue.canImbue(weapon)) {
			return;
		}

		ItemStack result = SpellImbue.imbue(weapon, book.definition());
		menu.getSlot(AnvilMenu.RESULT_SLOT).set(result);
		this.modStellaSpells$setRepairItemCountCost(0);
		this.modStellaSpells$imbueCost().set(result.isEmpty() ? 0 : SpellImbue.IMBUE_XP_COST);
		ci.cancel();
	}
}
