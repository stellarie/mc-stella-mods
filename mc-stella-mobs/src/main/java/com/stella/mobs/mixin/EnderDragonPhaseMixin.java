package com.stella.mobs.mixin;

import com.stella.mobs.behavior.CreeperEscapeController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnderDragon.class)
public abstract class EnderDragonPhaseMixin {
	@Redirect(
		method = "aiStep",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;"
				+ "doServerTick(Lnet/minecraft/server/level/ServerLevel;)V"
		)
	)
	private void stella$pausePhase(final DragonPhaseInstance phase, final ServerLevel level) {
		EnderDragon dragon = (EnderDragon)(Object)this;
		if (!CreeperEscapeController.pauseDragonPhase(dragon, phase)) {
			phase.doServerTick(level);
		}
	}

	@Redirect(
		method = "aiStep",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;"
				+ "getFlyTargetLocation()Lnet/minecraft/world/phys/Vec3;"
		)
	)
	private Vec3 stella$redirectFlightTarget(final DragonPhaseInstance phase) {
		return CreeperEscapeController.dragonTarget((EnderDragon)(Object)this, phase);
	}
}
