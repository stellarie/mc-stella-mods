package com.stella.mobs.mixin;

import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Phantom.class)
public interface PhantomMoveTargetAccessor {
	@Accessor("moveTargetPoint")
	Vec3 stella$getMoveTargetPoint();

	@Accessor("moveTargetPoint")
	void stella$setMoveTargetPoint(Vec3 target);
}
