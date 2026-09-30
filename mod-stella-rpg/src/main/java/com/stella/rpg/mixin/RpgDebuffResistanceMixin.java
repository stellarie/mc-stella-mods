package com.stella.rpg.mixin;

import com.stella.rpg.DebuffResistance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class RpgDebuffResistanceMixin {
	@ModifyVariable(
		method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
		at = @At("HEAD"), argsOnly = true
	)
	private MobEffectInstance stellaRpg$resistAdd(final MobEffectInstance effect) {
		return this.stellaRpg$resist(effect);
	}

	@ModifyVariable(method = "forceAddEffect", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance stellaRpg$resistForce(final MobEffectInstance effect) {
		return this.stellaRpg$resist(effect);
	}

	private MobEffectInstance stellaRpg$resist(final MobEffectInstance effect) {
		LivingEntity self = (LivingEntity) (Object) this;
		return self.level().isClientSide() ? effect : DebuffResistance.apply(self, effect);
	}
}
