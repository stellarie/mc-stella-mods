package com.stella.tower.mixin;

import com.stella.tower.TowerPeace;
import com.stella.tower.TowerSafety;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob with a brain, a piglin brute, a hoglin or a breeze, never senses a player in a safe zone as something to attack,
 * and a tower mob among them never senses a fellow of the tower as one.
 */
@Mixin(Sensor.class)
public abstract class TowerSensorMixin {
	@Inject(method = "isEntityAttackable", at = @At("HEAD"), cancellable = true)
	private static void stellaTower$noSenseInASafeZone(final ServerLevel level, final LivingEntity attacker, final LivingEntity target,
		final CallbackInfoReturnable<Boolean> cir) {
		if (TowerSafety.sheltered(target) || TowerPeace.fellows(attacker, target)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isEntityAttackableIgnoringLineOfSight", at = @At("HEAD"), cancellable = true)
	private static void stellaTower$noSenseInASafeZoneThroughWalls(final ServerLevel level, final LivingEntity attacker, final LivingEntity target,
		final CallbackInfoReturnable<Boolean> cir) {
		if (TowerSafety.sheltered(target) || TowerPeace.fellows(attacker, target)) {
			cir.setReturnValue(false);
		}
	}
}
