package com.stella.mobs.mixin;

import com.stella.mobs.behavior.ReactiveState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Shulker.class)
public abstract class ShulkerEscapeMixin {
	@Redirect(
		method = "teleportSomewhere",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/core/BlockPos;offset(III)Lnet/minecraft/core/BlockPos;",
			ordinal = 0
		)
	)
	private BlockPos stella$useEscapeTarget(
		final BlockPos origin, final int x, final int y, final int z
	) {
		BlockPos target = ReactiveState.get((Shulker)(Object)this).getR02ShulkerTarget();
		return target == null ? origin.offset(x, y, z) : target;
	}

}
