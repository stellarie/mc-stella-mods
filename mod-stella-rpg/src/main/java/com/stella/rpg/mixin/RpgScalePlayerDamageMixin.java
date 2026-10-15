package com.stella.rpg.mixin;

import com.stella.rpg.RpgScale;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Player overrides actuallyHurt without calling super, so it needs its own K hook. */
@Mixin(Player.class)
public abstract class RpgScalePlayerDamageMixin {
	@ModifyVariable(
		method = "actuallyHurt",
		at = @At(
			value = "INVOKE_ASSIGN",
			target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"
		),
		argsOnly = true
	)
	private float stellaRpg$scaleDamage(final float damage, final net.minecraft.server.level.ServerLevel level,
		final net.minecraft.world.damagesource.DamageSource source) {
		return com.stella.rpg.OneDamage.apply(source, damage * RpgScale.kf());
	}
}
