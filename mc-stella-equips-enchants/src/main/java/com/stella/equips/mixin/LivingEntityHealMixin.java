package com.stella.equips.mixin;

import com.stella.equips.numbers.DamageNumbers;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every heal passes through here, so the number shows the health the entity actually gained. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
	@Unique
	private float stellaEquips$healthBeforeHeal;

	@Inject(method = "heal", at = @At("HEAD"))
	private void stellaEquips$beforeHeal(final float amount, final CallbackInfo ci) {
		this.stellaEquips$healthBeforeHeal = ((LivingEntity) (Object) this).getHealth();
	}

	@Inject(method = "heal", at = @At("TAIL"))
	private void stellaEquips$afterHeal(final float amount, final CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		DamageNumbers.healed(self, self.getHealth() - this.stellaEquips$healthBeforeHeal);
	}
}
