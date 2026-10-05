package com.stella.equips.mixin;

import com.stella.equips.unique.UniqueDispatch;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reads the vanilla verdict on a critical blow, so a unique hook gets the same answer as the damage did. */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void stellaEquips$beginAttack(final Entity target, final CallbackInfo ci) {
		UniqueDispatch.beginAttack();
	}

	// Called only for a full strength attack, so a weak blow keeps the false from the head of the attack.
	@Inject(method = "canCriticalAttack", at = @At("RETURN"))
	private void stellaEquips$criticalAttack(final Entity target, final CallbackInfoReturnable<Boolean> cir) {
		UniqueDispatch.criticalAttack(cir.getReturnValueZ());
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void stellaEquips$endAttack(final Entity target, final CallbackInfo ci) {
		UniqueDispatch.endAttack();
	}
}
