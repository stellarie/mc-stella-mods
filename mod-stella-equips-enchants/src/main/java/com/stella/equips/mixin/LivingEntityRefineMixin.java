package com.stella.equips.mixin;

import com.stella.equips.refine.RefineEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The refine effects that change a number the game is about to use: a hit, the damage taken, an effect's length, a kill's experience. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRefineMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaEquips$keen(final float amount, final ServerLevel level, final DamageSource source) {
		return RefineEffects.outgoing((LivingEntity) (Object) this, source, amount);
	}

	// Return hooks chain with the other mods; a cancelling inject would skip them.
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float stellaEquips$armourShare(final float damage, final DamageSource source) {
		return RefineEffects.incoming((LivingEntity) (Object) this, source, damage);
	}

	@ModifyReturnValue(method = "getExperienceReward", at = @At("RETURN"))
	private int stellaEquips$fortune(final int experience, final ServerLevel level, final @Nullable Entity killer) {
		return RefineEffects.experience(killer, experience);
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
