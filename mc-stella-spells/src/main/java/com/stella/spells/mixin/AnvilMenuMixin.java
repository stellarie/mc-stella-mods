package com.stella.spells.mixin;

import com.stella.spells.SpellLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Unique
	private ItemStack modStellaSpells$input = ItemStack.EMPTY;
	@Unique
	private ItemStack modStellaSpells$result = ItemStack.EMPTY;

	@Inject(method = "createResult", at = @At("RETURN"))
	private void modStellaSpells$snapshot(final CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		this.modStellaSpells$input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().copy();
		this.modStellaSpells$result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void modStellaSpells$logEnchantments(
		final Player player, final ItemStack taken, final CallbackInfo ci
	) {
		if (!(player instanceof ServerPlayer)) {
			return;
		}

		SpellLog.anvil(this.modStellaSpells$input, this.modStellaSpells$result);
	}
}
