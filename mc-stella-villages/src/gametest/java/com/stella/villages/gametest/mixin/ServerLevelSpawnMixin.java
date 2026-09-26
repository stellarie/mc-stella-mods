package com.stella.villages.gametest.mixin;

import com.stella.villages.PatrolInsertionTestGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSpawnMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void mcsv$rejectPatrolInsertion(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
		if (PatrolInsertionTestGate.shouldReject(entity)) {
			cir.setReturnValue(false);
		}
	}
}
