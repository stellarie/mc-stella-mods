package com.stella.nether.mixin;

import com.stella.nether.RaidTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inside the raid nether, every nether portal leads back to the guardian a player used. Everywhere else it is vanilla's. */
@Mixin(NetherPortalBlock.class)
public abstract class RaidPortalMixin {
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void stellaNether$leaveRaid(final ServerLevel level, final Entity entity, final BlockPos portal, final CallbackInfoReturnable<TeleportTransition> cir) {
		if (level.dimension().equals(RaidTravel.RAID)) {
			cir.setReturnValue(RaidTravel.leaveRaid(level, entity));
		}
	}
}
