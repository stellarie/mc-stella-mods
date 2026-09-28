package com.stella.tower.mixin;

import com.stella.tower.TowerPortalShape;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire placed inside a closed crying obsidian frame, by flint and steel, a fire charge, lava or lightning,
 * lights the tower's portal in any dimension, where vanilla looks only for obsidian.
 */
@Mixin(BaseFireBlock.class)
public abstract class TowerPortalIgniteMixin {
	@Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
	private void stellaTower$lightTowerPortal(
		final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean moved, final CallbackInfo ci
	) {
		if (level.isClientSide() || oldState.is(state.getBlock())) {
			return;
		}
		Optional<TowerPortalShape> shape = TowerPortalShape.find(level, pos);
		if (shape.isPresent()) {
			shape.get().createPortal(level);
			ci.cancel();
		}
	}
}
