package com.stella.end.mixin;

import com.stella.end.CastleArrival;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player entering the End by its portal lands in the castle. Vanilla builds the obsidian platform inside this method,
 * so the redirect returns before it runs. Mobs, items and the way back out keep vanilla's rules.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalArrivalMixin {
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$arriveInTheCastle(
		final ServerLevel currentLevel, final Entity entity, final BlockPos portalEntryPos, final CallbackInfoReturnable<TeleportTransition> cir
	) {
		if (!(entity instanceof ServerPlayer) || currentLevel.dimension().equals(Level.END)) {
			return;
		}
		ServerLevel end = currentLevel.getServer().getLevel(Level.END);
		if (end != null) {
			cir.setReturnValue(CastleArrival.transition(end));
		}
	}
}
