package com.stella.spells.mixin;

import com.stella.spells.SummonSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A summoned skeleton's blow is worth its master's MATK when the RPG mod scales spells. */
@Mixin(LivingEntity.class)
public abstract class SummonHitMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaSpells$servantHit(final float amount, final ServerLevel level, final DamageSource source) {
		double hit = SummonSkeleton.hit(source);
		return Double.isNaN(hit) ? amount : (float) hit;
	}
}
