package com.stella.spells.mixin;

import com.stella.spells.SummonSkeleton;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A summoned skeleton stops being undead where healing is concerned: instant health mends it instead of
 * burning it. Undead harm still lands, so a hostile mob's magic hurts it as it always did.
 */
@Mixin(LivingEntity.class)
public abstract class SummonHealInversionMixin {
	@Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$servantsTakeHealingAsHealing(final CallbackInfoReturnable<Boolean> callback) {
		if ((Object) this instanceof LivingEntity body && SummonSkeleton.isServant(body)) {
			callback.setReturnValue(false);
		}
	}
}
