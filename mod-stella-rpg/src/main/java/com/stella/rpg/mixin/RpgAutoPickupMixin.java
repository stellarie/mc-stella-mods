package com.stella.rpg.mixin;

import com.stella.rpg.AutoPickup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class RpgAutoPickupMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$autoPickupOff(final Player player, final CallbackInfo info) {
		if (!AutoPickup.allows(player)) {
			info.cancel();
		}
	}
}
