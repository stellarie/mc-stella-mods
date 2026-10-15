package com.stella.spells.mixin;

import com.stella.spells.StaffBolt;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A swing with a staff, even at air, fires the staff bolt. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class StaffSwingMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("TAIL"))
	private void modStellaSpells$staffBolt(final ServerboundPunchPacket packet, final CallbackInfo ci) {
		StaffBolt.onSwing(this.player);
	}
}
