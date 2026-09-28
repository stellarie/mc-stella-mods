package com.stella.nether.mixin;

import com.stella.nether.EndSeal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An End portal leads nowhere while the End is sealed, unless the one stepping in brings the cores to open it. */
@Mixin(EndPortalBlock.class)
public abstract class EndSealMixin {
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void stellaNether$seal(final ServerLevel level, final Entity entity, final BlockPos portal, final CallbackInfoReturnable<TeleportTransition> cir) {
		if (!EndSeal.allows(level, entity)) {
			cir.setReturnValue(null);
		}
	}
}
