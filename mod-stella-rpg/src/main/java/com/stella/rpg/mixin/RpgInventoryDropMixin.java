package com.stella.rpg.mixin;

import com.stella.rpg.tomb.Tombstones;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class RpgInventoryDropMixin {
	@Shadow
	@Final
	public Player player;

	// Cancelling keeps the rest for RpgDeath to copy.
	@Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$tombstone(final CallbackInfo callback) {
		if (this.player instanceof ServerPlayer serverPlayer && Tombstones.bury(serverPlayer)) {
			callback.cancel();
		}
	}
}
