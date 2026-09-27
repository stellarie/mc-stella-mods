package com.stella.mobs.mixin;

import com.stella.mobs.behavior.FriendlyPatrol;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player may sleep with a friendly patrol nearby. */
@Mixin(Monster.class)
public abstract class FriendlyPatrolRestMixin {
	@Inject(method = "isPreventingPlayerRest", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$friendlyPatrolsLetPlayersSleep(final ServerLevel level, final Player player, final CallbackInfoReturnable<Boolean> callback) {
		if (FriendlyPatrol.isFriendly((Monster) (Object) this)) {
			callback.setReturnValue(false);
		}
	}
}
