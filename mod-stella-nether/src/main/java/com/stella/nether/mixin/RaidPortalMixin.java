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

/** A nether portal asks a player which nether to enter, and inside the raid nether it leads back out. */
@Mixin(NetherPortalBlock.class)
public abstract class RaidPortalMixin {
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void stellaNether$route(final ServerLevel level, final Entity entity, final BlockPos portal, final CallbackInfoReturnable<TeleportTransition> cir) {
		RaidTravel.Route route = RaidTravel.route(level, entity, portal);
		if (route.decided()) {
			cir.setReturnValue(route.transition());
		}
	}
}
