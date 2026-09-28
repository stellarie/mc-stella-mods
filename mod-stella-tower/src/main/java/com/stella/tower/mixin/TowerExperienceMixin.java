package com.stella.tower.mixin;

import com.stella.tower.TowerRewards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A tower mob's experience grows with its floor. */
@Mixin(LivingEntity.class)
public abstract class TowerExperienceMixin {
	@Inject(method = "getExperienceReward", at = @At("RETURN"), cancellable = true)
	private void stellaTower$grow(final ServerLevel level, final Entity killer, final CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(TowerRewards.experience((LivingEntity) (Object) this, cir.getReturnValue()));
	}
}
