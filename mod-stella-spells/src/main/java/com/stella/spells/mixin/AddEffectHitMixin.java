package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.spells.CastHits;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An effect that took hold on a living entity is a hit of the cast that gave it. */
@Mixin(LivingEntity.class)
public abstract class AddEffectHitMixin {
	@ModifyReturnValue(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
	private boolean stellaSpells$effectHit(final boolean added, final MobEffectInstance effect, final Entity source) {
		if (added && source != null) {
			CastHits.onEffectAdded((LivingEntity) (Object) this, source);
		}
		return added;
	}
}
