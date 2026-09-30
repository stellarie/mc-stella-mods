package com.stella.tower.mixin;

import com.stella.tower.TowerMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every hit on a living entity passes through the tower: what a tower mob deals grows with its floor,
 * whatever carries it (a swing, an arrow, a blast or a potion), and a tower mob too big for the health
 * attribute takes the rest of its size as a divisor.
 */
@Mixin(LivingEntity.class)
public abstract class TowerDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaTower$scale(final float amount, final ServerLevel level, final DamageSource source) {
		return TowerMobs.scaleDamage((LivingEntity) (Object) this, source, amount);
	}
}
