package com.stella.tower.mixin;

import com.stella.tower.RuneGuards;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every effect a living entity gains passes its armor runes first: Ice shortens slowness, Holy every harm. */
@Mixin(LivingEntity.class)
public abstract class RuneGuardEffectMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance stellaTower$temper(final MobEffectInstance instance, final MobEffectInstance original, final @Nullable Entity source) {
		return RuneGuards.temper((LivingEntity) (Object) this, instance);
	}
}
