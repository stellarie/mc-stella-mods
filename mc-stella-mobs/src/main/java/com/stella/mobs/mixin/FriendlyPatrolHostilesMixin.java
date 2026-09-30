package com.stella.mobs.mixin;

import com.stella.mobs.behavior.FriendlyPatrol;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A villager does not count a friendly patrol as a hostile, so it neither flees nor calls a golem. */
@Mixin(VillagerHostilesSensor.class)
public abstract class FriendlyPatrolHostilesMixin {
	@Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$friendlyPatrolsAreNotHostile(
		final ServerLevel level, final LivingEntity villager, final LivingEntity target, final CallbackInfoReturnable<Boolean> callback
	) {
		if (FriendlyPatrol.isFriendly(target)) {
			callback.setReturnValue(false);
		}
	}
}
