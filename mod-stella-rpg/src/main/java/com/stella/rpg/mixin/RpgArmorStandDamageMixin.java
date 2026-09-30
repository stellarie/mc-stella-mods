package com.stella.rpg.mixin;

import com.stella.rpg.RpgScale;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** An armor stand lowers health without actuallyHurt, so it needs its own K hook. */
@Mixin(ArmorStand.class)
public abstract class RpgArmorStandDamageMixin {
	@ModifyVariable(method = "causeDamage", at = @At("HEAD"), argsOnly = true)
	private float stellaRpg$scaleDamage(final float damage) {
		return damage * RpgScale.kf();
	}
}
