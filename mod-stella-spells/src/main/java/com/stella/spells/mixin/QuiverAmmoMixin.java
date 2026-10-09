package com.stella.spells.mixin;

import com.stella.spells.Quiver;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The off-hand quiver is the first ammunition a bow or a crossbow draws, before the vanilla search. */
@Mixin(Player.class)
public abstract class QuiverAmmoMixin {
	// Both sides must agree: the client use() refuses a draw it sees no projectile for.
	@Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$quiverFirst(final ItemStack weapon, final CallbackInfoReturnable<ItemStack> cir) {
		ItemStack drawn = Quiver.projectileFor((Player) (Object) this, weapon);
		if (!drawn.isEmpty()) {
			cir.setReturnValue(drawn);
		}
	}
}
