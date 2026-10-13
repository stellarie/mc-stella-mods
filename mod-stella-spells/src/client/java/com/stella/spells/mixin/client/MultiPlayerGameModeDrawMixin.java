package com.stella.spells.mixin.client;

import com.stella.spells.ArcherDraw;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client lets go of a use its player is not holding; an archer cast's draw must last its few ticks. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeDrawMixin {
	@Inject(method = "releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$holdTheDraw(final Player player, final CallbackInfo info) {
		if (Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(ArcherDraw.DRAWING))) {
			info.cancel();
		}
	}
}
