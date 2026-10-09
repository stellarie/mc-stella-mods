package com.stella.equips.mixin;

import com.stella.equips.imbue.NetherImbue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The nether star imbue hooks: the bonus on a withered target, and the Wither immunity of imbued armor. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityImbueMixin {
	// The head of hurtServer: before blocking, armor and the hurt cooldown.
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaEquips$imbueOutgoing(final float amount, final ServerLevel level, final DamageSource source) {
		return NetherImbue.outgoing((LivingEntity) (Object) this, source, amount);
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void stellaEquips$imbueRefusesWither(final MobEffectInstance effect, final CallbackInfoReturnable<Boolean> cir) {
		if (NetherImbue.blocksEffect((LivingEntity) (Object) this, effect)) {
			cir.setReturnValue(false);
		}
	}
}
