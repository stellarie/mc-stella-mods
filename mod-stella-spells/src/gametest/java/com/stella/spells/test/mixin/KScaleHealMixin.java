package com.stella.spells.test.mixin;

import com.stella.spells.KScaleHarness;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Test harness only: K multiplies every heal, as the RPG mod does. */
@Mixin(LivingEntity.class)
public abstract class KScaleHealMixin {
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float stellaSpellsTest$scaleHeal(final float heal) {
		return heal * KScaleHarness.factor();
	}
}
