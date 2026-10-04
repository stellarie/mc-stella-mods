package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.equips.unique.UniqueDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The hooks of the uniques that change a number the game is about to use: a hit, the damage taken, an effect's length. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUniqueMixin {
	// The head of hurtServer: the one point before blocking, armor and the hurt cooldown.
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaEquips$uniqueOutgoing(final float amount, final ServerLevel level, final DamageSource source) {
		return UniqueDispatch.outgoing((LivingEntity) (Object) this, source, amount);
	}

	// Return hooks chain with the other mods; a cancelling inject would skip them.
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float stellaEquips$uniqueIncoming(final float damage, final DamageSource source) {
		return UniqueDispatch.incoming((LivingEntity) (Object) this, source, damage);
	}

	@ModifyVariable(
		method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
		at = @At("HEAD"), argsOnly = true
	)
	private MobEffectInstance stellaEquips$uniqueHarmfulAdd(final MobEffectInstance effect) {
		return this.stellaEquips$uniqueHarmful(effect);
	}

	@ModifyVariable(method = "forceAddEffect", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance stellaEquips$uniqueHarmfulForce(final MobEffectInstance effect) {
		return this.stellaEquips$uniqueHarmful(effect);
	}

	@Unique
	private MobEffectInstance stellaEquips$uniqueHarmful(final MobEffectInstance effect) {
		LivingEntity self = (LivingEntity) (Object) this;
		return self.level().isClientSide() ? effect : UniqueDispatch.harmful(self, effect);
	}
}
