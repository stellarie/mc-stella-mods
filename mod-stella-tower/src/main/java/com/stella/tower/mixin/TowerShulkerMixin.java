package com.stella.tower.mixin;

import com.stella.tower.TowerMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A shulker teleports by its own road, not by {@code randomTeleport}. In the tower it may land only in its floor's
 * room and no higher than {@link TowerMobs#SHULKER_CEILING}: not over the roof, not past the walls, and not in the
 * roof's shadow where no one can reach it.
 */
@Mixin(Shulker.class)
public abstract class TowerShulkerMixin {
	@Redirect(method = "teleportSomewhere", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean stellaTower$stayInTheRoom(final Level level, final BlockPos target) {
		return level.isEmptyBlock(target) && TowerMobs.mayLandAt((Shulker) (Object) this, target, TowerMobs.SHULKER_CEILING);
	}
}
