package com.stella.tower.mixin;

import com.stella.tower.TowerTravel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every jump into the tower from outside, pearl and mount included, records the way back first. */
@Mixin(ServerPlayer.class)
public abstract class TowerEntryMixin {
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
	private void stellaTower$recordWayBack(final TeleportTransition transition, final CallbackInfoReturnable<ServerPlayer> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (transition.newLevel().dimension().equals(TowerTravel.TOWER) && !self.level().dimension().equals(TowerTravel.TOWER)) {
			TowerTravel.beforeEntering(self);
		}
	}
}
