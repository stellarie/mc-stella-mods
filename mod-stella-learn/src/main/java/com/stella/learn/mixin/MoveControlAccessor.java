package com.stella.learn.mixin;

import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** `MoveControl.strafe` fixes the speed modifier at 0.25, so a driver that wants another speed sets it afterward. */
@Mixin(MoveControl.class)
public interface MoveControlAccessor {
	@Accessor("speedModifier")
	void stella$setSpeedModifier(double speedModifier);
}
