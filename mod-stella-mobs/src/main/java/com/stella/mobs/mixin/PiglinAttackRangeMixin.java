package com.stella.mobs.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.mobs.behavior.RangedReach;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A piglin's Brain asks this call whether its target is in crossbow range, to start the attack and to decide
 * whether to walk closer. Vanilla answers for 8 blocks. The answer for a crossbow piglin is the reach, less the
 * margin the caller passes. Any other mob gets vanilla's answer. The value is changed and not replaced, so another
 * mod's hook on the same call still chains.
 */
@Mixin(BehaviorUtils.class)
public abstract class PiglinAttackRangeMixin {
	@ModifyReturnValue(method = "isWithinAttackRange", at = @At("RETURN"))
	private static boolean stella$crossbowPiglinReach(
		final boolean original, final Mob body, final LivingEntity target, final int projectileAttackRangeMargin
	) {
		if (!RangedReach.isCrossbowPiglin(body)) {
			return original;
		}

		return body.closerThan(target, RangedReach.RANGE - projectileAttackRangeMargin);
	}
}
