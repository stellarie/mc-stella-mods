package com.stella.mobs.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Speed and Slowness, gear speed, and stat speed no longer pull the field of view. The view reads the walking speed
 * as if only the sprint changed it, so a sprint still widens the view as in vanilla.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class SteadyFovMixin {
	private static final double SPRINT_FACTOR = 1.3;

	@WrapOperation(
		method = "getFieldOfViewModifier",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D")
	)
	private double stellaMobs$steadySpeed(final AbstractClientPlayer player, final Holder<Attribute> attribute, final Operation<Double> original) {
		double walking = player.getAbilities().getWalkingSpeed();
		return player.isSprinting() ? walking * SPRINT_FACTOR : walking;
	}
}
