package com.stella.tower.mixin;

import com.stella.tower.TowerTravel;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the tower an enderman never sets out to take a block. Its floors and walls are built of blocks no
 * enderman may hold, and this keeps them so whatever a data pack tags holdable.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeMixin {
	@Shadow
	@Final
	private Enderman enderman;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void stellaTower$takeNothingHere(final CallbackInfoReturnable<Boolean> cir) {
		if (this.enderman.level().dimension().equals(TowerTravel.TOWER)) {
			cir.setReturnValue(false);
		}
	}
}
