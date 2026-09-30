package com.stella.rpg.mixin;

import com.stella.rpg.roll.WeaponRolls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class RpgMobDropRollMixin {
	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void stellaRpg$rollEquipment(
		final ServerLevel level,
		final DamageSource source,
		final boolean killedByPlayer,
		final CallbackInfo callback
	) {
		WeaponRolls.rollEquipment((Mob) (Object) this);
	}
}
