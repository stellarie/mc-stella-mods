package com.stella.spells.mixin;

import com.stella.spells.SpellStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Arcane Ward, Frozen, Brittle, and Marked change the damage a hit deals; Scorched changes the healing. */
@Mixin(LivingEntity.class)
public abstract class SpellStatusDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaSpells$statusDamage(final float amount, final ServerLevel level, final DamageSource source) {
		return SpellStatus.onHurt((LivingEntity) (Object) this, level, source, amount);
	}

	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float stellaSpells$statusHeal(final float amount) {
		return SpellStatus.onHeal((LivingEntity) (Object) this, amount);
	}
}
