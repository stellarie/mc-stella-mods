package com.stella.mobs.mixin;

import com.stella.mobs.behavior.SnowGolemCold;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * G05: vanilla melts a snow golem with one call inside its own aiStep, so the suppression wraps that call
 * and nothing else. The alternative, cancelling fire damage from an event, cannot tell the melt from a
 * golem that is genuinely on fire, because vanilla reuses the same damage source for both.
 */
@Mixin(SnowGolem.class)
public class SnowGolemMeltMixin {
	@Redirect(
		method = "aiStep",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/animal/golem/SnowGolem;hurtServer(Lnet/minecraft/server/level/ServerLevel;"
				+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean stella$keepTheSnow(
		final SnowGolem golem, final ServerLevel level, final DamageSource source, final float damage
	) {
		return !SnowGolemCold.keepsItsSnow(golem) && golem.hurtServer(level, source, damage);
	}
}
