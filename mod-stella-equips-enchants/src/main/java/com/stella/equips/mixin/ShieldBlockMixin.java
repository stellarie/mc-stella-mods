package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stella.equips.shield.Parry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Parry hooks the call that works out how much damage a raised shield blocks. The shield is still whole there,
 * so a blow that breaks it is parried too. The wrap returns the blocked amount unchanged, so it chains with the
 * hooks of other mods on the same call.
 */
@Mixin(LivingEntity.class)
public abstract class ShieldBlockMixin {
	@WrapOperation(
		method = "applyItemBlocking",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F")
	)
	private float stellaEquips$parry(final BlocksAttacks blocks, final DamageSource source, final float damage, final double angle, final Operation<Float> original) {
		float blocked = original.call(blocks, source, damage, angle);
		Parry.answer((LivingEntity) (Object) this, source, blocked);
		return blocked;
	}
}
