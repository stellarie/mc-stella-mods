package com.stella.equips.mixin;

import com.stella.equips.refine.RefineEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The refine effects that change a number the game is about to use: a hit, the damage taken, an effect's length, a kill's experience. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRefineMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaEquips$keen(final float amount, final ServerLevel level, final DamageSource source) {
		return RefineEffects.outgoing((LivingEntity) (Object) this, source, amount);
	}

	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void stellaEquips$armourShare(final DamageSource source, final float damage, final CallbackInfoReturnable<Float> callback) {
		callback.setReturnValue(RefineEffects.incoming((LivingEntity) (Object) this, source, callback.getReturnValue()));
	}

	@Inject(method = "getExperienceReward", at = @At("RETURN"), cancellable = true)
	private void stellaEquips$fortune(final ServerLevel level, final @Nullable Entity killer, final CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(RefineEffects.experience(killer, callback.getReturnValue()));
	}

	@ModifyVariable(
		method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
		at = @At("HEAD"), argsOnly = true
	)
	private MobEffectInstance stellaEquips$clarityAdd(final MobEffectInstance effect) {
		return this.stellaEquips$clarity(effect);
	}

	@ModifyVariable(method = "forceAddEffect", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance stellaEquips$clarityForce(final MobEffectInstance effect) {
		return this.stellaEquips$clarity(effect);
	}

	@Unique
	private MobEffectInstance stellaEquips$clarity(final MobEffectInstance effect) {
		LivingEntity self = (LivingEntity) (Object) this;
		return self.level().isClientSide() ? effect : RefineEffects.shorten(self, effect);
	}
}
