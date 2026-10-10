package com.stella.tower.mixin;

import com.stella.tower.TowerLayout;
import com.stella.tower.TowerPlan;
import com.stella.tower.TowerTravel;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chorus fruit, endermen and shulkers teleport at random; in the tower a random teleport may only land in
 * the room it starts from, never on its roof or in another floor.
 */
@Mixin(LivingEntity.class)
public abstract class TowerRandomTeleportMixin {
	@Inject(method = "randomTeleport(DDDZLjava/util/function/Predicate;)Z", at = @At("HEAD"), cancellable = true)
	private void stellaTower$stayInTheRoom(
		final double x, final double y, final double z, final boolean particles, final Predicate<BlockState> avoid,
		final CallbackInfoReturnable<Boolean> cir
	) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().dimension().equals(TowerTravel.TOWER)) {
			return;
		}
		int floor = TowerLayout.floorAt(self.blockPosition());
		BlockPos target = BlockPos.containing(x, y, z);
		boolean inRoom = floor > 0 && TowerLayout.floorAt(target) == floor && target.getY() >= TowerLayout.BASE_Y
			&& target.getY() < TowerLayout.BASE_Y + TowerPlan.atQuiet((net.minecraft.server.level.ServerLevel) self.level(), floor).roofY();
		if (!inRoom) {
			cir.setReturnValue(false);
		}
	}
}
