package com.stella.spells.mixin;

import com.stella.spells.SummonSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The awakened servant's mount answers the health the spec names. Vanilla's {@code minecraft:max_health}
 * attribute is capped at 1024, so a mount of 24000 reads its maximum from here instead, and the health it
 * carries is bounded by the same number.
 */
@Mixin(LivingEntity.class)
public abstract class SummonMountHealthMixin {
	@Inject(method = "getMaxHealth", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$mountMaxHealth(final CallbackInfoReturnable<Float> callback) {
		if ((Object) this instanceof Mob mob && SummonSkeleton.isMount(mob)) {
			callback.setReturnValue((float) SummonSkeleton.HORSE_MAX_HEALTH);
		}
	}
}
