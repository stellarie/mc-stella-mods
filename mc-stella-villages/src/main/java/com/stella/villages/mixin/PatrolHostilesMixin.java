package com.stella.villages.mixin;

import com.stella.villages.patrol.PatrolState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A villager does not count its village's patrol as hostile, so it neither flees nor calls a golem. */
@Mixin(VillagerHostilesSensor.class)
public abstract class PatrolHostilesMixin {
	@Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$patrolsAreNotHostile(
		final ServerLevel level, final LivingEntity villager, final LivingEntity target, final CallbackInfoReturnable<Boolean> callback
	) {
		if (target instanceof Mob member && PatrolState.isMarked(member)) {
			callback.setReturnValue(false);
		}
	}
}
