package com.stella.mobs.mixin;

import com.stella.mobs.behavior.Levitation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every effect a living entity gains passes here, so the levitation cap cannot be walked around. */
@Mixin(LivingEntity.class)
public class LevitationCapMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance stella$capLevitation(final MobEffectInstance instance, final MobEffectInstance original, final @Nullable Entity source) {
		return Levitation.cap(instance, source);
	}
}
