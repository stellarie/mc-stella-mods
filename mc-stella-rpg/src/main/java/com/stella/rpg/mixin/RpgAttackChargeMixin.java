package com.stella.rpg.mixin;

import com.stella.rpg.RpgCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class RpgAttackChargeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void stellaRpg$beginAttack(final Entity target, final CallbackInfo callback) {
		Player player = (Player) (Object) this;
		RpgCombat.beginAttack(player, player.getAttackStrengthScale(0.5F));
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void stellaRpg$endAttack(final Entity target, final CallbackInfo callback) {
		RpgCombat.endAttack((Player) (Object) this);
	}
}
