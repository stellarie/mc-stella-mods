package com.stella.equips.mixin;

import com.stella.equips.enchant.Soulbound;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Soulbound items leave the inventory before vanilla drops it, so they never hit the ground. */
@Mixin(Player.class)
public class PlayerSoulboundMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void stella$holdBackSoulbound(final ServerLevel level, final CallbackInfo info) {
		Soulbound.holdBack((Player) (Object) this, level);
	}
}
