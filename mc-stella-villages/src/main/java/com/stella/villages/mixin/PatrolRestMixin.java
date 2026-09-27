package com.stella.villages.mixin;

import com.stella.villages.patrol.PatrolState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player may sleep with a village patrol close by. */
@Mixin(Monster.class)
public abstract class PatrolRestMixin {
	@Inject(method = "isPreventingPlayerRest", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$patrolsLetPlayersSleep(final ServerLevel level, final Player player, final CallbackInfoReturnable<Boolean> callback) {
		if (PatrolState.isMarked((Monster) (Object) this)) {
			callback.setReturnValue(false);
		}
	}
}
