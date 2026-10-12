package com.stella.spells.mixin;

import com.stella.spells.ArcherDraw;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A release that arrives while an archer cast holds the draw must not loose the weapon. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDrawMixin {
	@Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$holdTheDraw(final CallbackInfo info) {
		if ((Object) this instanceof ServerPlayer player && ArcherDraw.holding(player)) {
			info.cancel();
		}
	}
}
