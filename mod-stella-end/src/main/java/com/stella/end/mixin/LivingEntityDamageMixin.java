package com.stella.end.mixin;

import com.stella.end.DamageRules;
import com.stella.end.HurtMemo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every blow lands through this method, the dragon's own included (its override ends in a call to it), so the roost, the
 * crystals, the draconic blade and a refined withering arm change the damage here, before any other rule reads it. The
 * blow also notes the health it began with, so a rule that heals by the health a blow took reads the true loss.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin implements HurtMemo {
	@Unique
	private float stellaEnd$healthBeforeHurt;

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaEnd$scaleTheBlow(final float damage, final ServerLevel level, final DamageSource source) {
		return DamageRules.scale((LivingEntity) (Object) this, source, damage);
	}

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void stellaEnd$noteTheHealth(final ServerLevel level, final DamageSource source, final float damage, final CallbackInfoReturnable<Boolean> cir) {
		this.stellaEnd$healthBeforeHurt = ((LivingEntity) (Object) this).getHealth();
	}

	@Override
	public float stellaEnd$healthBeforeHurt() {
		return this.stellaEnd$healthBeforeHurt;
	}
}
