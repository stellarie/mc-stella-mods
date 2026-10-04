package com.stella.rpg.client.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.stella.rpg.WeaponSlot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The server selects the weapon slot with the vanilla packet; the hotbar check would drop it. */
@Mixin(ClientPacketListener.class)
public abstract class WeaponHeldSlotMixin {
	@ModifyExpressionValue(
		method = "handleSetHeldSlot",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;isHotbarSlot(I)Z")
	)
	private boolean stellaRpg$acceptWeaponSlot(final boolean hotbar, @Local(argsOnly = true) final ClientboundSetHeldSlotPacket packet) {
		return hotbar || WeaponSlot.isSlot(packet.slot());
	}
}
