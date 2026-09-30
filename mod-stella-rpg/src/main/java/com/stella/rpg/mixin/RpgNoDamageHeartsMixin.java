package com.stella.rpg.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A hit shows no damage-indicator hearts. The hurt flash is a separate event and stays. */
@Mixin(Player.class)
public abstract class RpgNoDamageHeartsMixin {
	@Redirect(
		method = "damageStatsAndHearts",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
		)
	)
	private <T extends ParticleOptions> int stellaRpg$noHearts(
		final ServerLevel level, final T particle, final double x, final double y, final double z, final int count,
		final double xOffset, final double yOffset, final double zOffset, final double speed
	) {
		return 0;
	}
}
