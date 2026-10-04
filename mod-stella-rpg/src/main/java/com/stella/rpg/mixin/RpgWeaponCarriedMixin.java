package com.stella.rpg.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.stella.rpg.WeaponSlot;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The client echoes the weapon slot back after the server selects it; the bound check would reject the echo. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RpgWeaponCarriedMixin {
	@ModifyExpressionValue(
		method = "handleSetCarriedItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectionSize()I")
	)
	private int stellaRpg$acceptWeaponSlot(final int size, @Local(argsOnly = true) final ServerboundSetCarriedItemPacket packet) {
		return WeaponSlot.isSlot(packet.getSlot()) ? packet.getSlot() + 1 : size;
	}
}
