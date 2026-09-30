package com.stella.villages.mixin;

import com.stella.villages.dummy.TrainingDummy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A dummy records every hit before the stand's own damage code can break it. */
@Mixin(ArmorStand.class)
public abstract class TrainingDummyHurtMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$recordHit(final ServerLevel level, final DamageSource source, final float damage,
		final CallbackInfoReturnable<Boolean> callback) {
		ArmorStand self = (ArmorStand) (Object) this;
		if (TrainingDummy.is(self)) {
			TrainingDummy.record(self, source, damage);
			callback.setReturnValue(false);
		}
	}
}
