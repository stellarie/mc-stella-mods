package com.stella.mobs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
public interface CubeMoveControlInvoker {
	@Invoker("setDirection")
	void stella$setDirection(float direction, boolean aggressive);

	@Invoker("setWantedMovement")
	void stella$setWantedMovement(double movement);
}
