package com.stella.spells.mixin;

import com.stella.spells.SummonSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A golem never takes a player's summoned skeleton for a foe. */
@Mixin(Mob.class)
public abstract class SummonGolemMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$golemsSpareServants(final LivingEntity target, final CallbackInfoReturnable<Boolean> callback) {
		if ((Object) this instanceof AbstractGolem && target.entityTags().contains(SummonSkeleton.SUMMON_TAG)) {
			callback.setReturnValue(false);
		}
	}
}
